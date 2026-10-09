-- Execute in SQL Editor as postgres after the migration. Always rolls back.
begin;
do $$
declare
  u uuid := gen_random_uuid();
  a uuid := gen_random_uuid();
  d1 uuid := gen_random_uuid();
  d2 uuid := gen_random_uuid();
  s1 jsonb;
  s2 jsonb;
  deadline timestamptz := clock_timestamp() + interval '2 hours';
  rejected boolean;
begin
  insert into auth.users(id, aud, role) values(u, 'authenticated', 'authenticated'),
    (a, 'authenticated', 'authenticated');
  insert into public.sv_accounts(user_id, username, display_name, enabled, expires_at)
    values(u, 'test_' || replace(u::text, '-', ''), 'Temporary test', true, deadline);
  insert into public.sv_admins(user_id) values(a);

  s1 := public.sv_open_session(u, d1, 'First box');
  if (s1->>'offline_until')::timestamptz <> deadline then
    raise exception 'FAIL: lease exceeds account expiration';
  end if;
  perform public.sv_validate_session(u, (s1->>'session_id')::uuid, d1);
  s2 := public.sv_open_session(u, d2, 'Second box');
  if (s2->>'generation')::bigint <= (s1->>'generation')::bigint then
    raise exception 'FAIL: generation did not advance';
  end if;
  if (select count(*) from public.sv_sessions where user_id = u and revoked_at is null) <> 1 then
    raise exception 'FAIL: multiple active sessions';
  end if;
  rejected := false;
  begin
    perform public.sv_validate_session(u, (s1->>'session_id')::uuid, d1);
  exception when others then
    if sqlerrm <> 'session_replaced' then raise; end if;
    rejected := true;
  end;
  if not rejected then raise exception 'FAIL: replaced session accepted'; end if;
  rejected := false;
  begin
    perform public.sv_validate_session(u, (s2->>'session_id')::uuid, d1);
  exception when others then
    if sqlerrm <> 'device_blocked' then raise; end if;
    rejected := true;
  end;
  if not rejected then raise exception 'FAIL: wrong device accepted'; end if;
  rejected := false;
  begin
    perform public.sv_set_access(u, u, false, null, null);
  exception when others then
    if sqlerrm <> 'admin_required' then raise; end if;
    rejected := true;
  end;
  if not rejected then raise exception 'FAIL: client changed access'; end if;
  perform public.sv_set_access(a, u, false, null, null);
  rejected := false;
  begin
    perform public.sv_open_session(u, d1, 'Blocked box');
  exception when others then
    if sqlerrm <> 'account_blocked' then raise; end if;
    rejected := true;
  end;
  if not rejected then raise exception 'FAIL: blocked account accepted'; end if;
  perform public.sv_set_access(a, u, true, clock_timestamp() - interval '1 minute', null);
  rejected := false;
  begin
    perform public.sv_open_session(u, d1, 'Expired box');
  exception when others then
    if sqlerrm <> 'account_expired' then raise; end if;
    rejected := true;
  end;
  if not rejected then raise exception 'FAIL: expired account accepted'; end if;
  perform public.sv_set_access(a, u, true, null, null);
  s1 := public.sv_open_session(u, d1, 'Unlimited account');
  if (s1->>'offline_until')::timestamptz - (s1->>'validated_at')::timestamptz <> interval '24 hours' then
    raise exception 'FAIL: unlimited account must still have a bounded lease';
  end if;
  if has_function_privilege('authenticated', 'public.sv_open_session(uuid,uuid,text)', 'EXECUTE')
    or has_function_privilege('anon', 'public.sv_set_access(uuid,uuid,boolean,timestamptz,uuid)', 'EXECUTE')
    or has_table_privilege('authenticated', 'public.sv_accounts', 'UPDATE') then
    raise exception 'FAIL: direct client access allowed';
  end if;
  raise notice 'PASS: session replacement, device binding, expiration, blocking, admin and permissions';
end $$;
rollback;
