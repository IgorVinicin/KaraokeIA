begin;
do $$
declare
  a uuid := gen_random_uuid(); u uuid := gen_random_uuid();
  d1 uuid := gen_random_uuid(); d2 uuid := gen_random_uuid();
  s1 jsonb; s2 jsonb; result jsonb; rejected boolean;
  key text := replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');
begin
  insert into auth.users(id, aud, role) values(a, 'authenticated', 'authenticated'), (u, 'authenticated', 'authenticated');
  insert into public.sv_admins(user_id) values(a);
  result := public.sv_create_account(a, u, 'test_' || replace(u::text, '-', ''), 'API Test', true, null, null);
  if (result->>'user_id')::uuid <> u then raise exception 'FAIL: account creation'; end if;
  if not exists(select 1 from public.sv_audit where account_id = u and action = 'account_created') then
    raise exception 'FAIL: missing audit';
  end if;
  s1 := public.sv_open_session(u, d1, 'Box 1');
  s2 := public.sv_open_session(u, d2, 'Box 2');
  perform public.sv_close_session(u, (s1->>'session_id')::uuid, d1);
  perform public.sv_validate_session(u, (s2->>'session_id')::uuid, d2);
  perform public.sv_close_session(u, (s2->>'session_id')::uuid, d1);
  perform public.sv_validate_session(u, (s2->>'session_id')::uuid, d2);
  perform public.sv_close_session(u, (s2->>'session_id')::uuid, d2);
  rejected := false;
  begin
    perform public.sv_validate_session(u, (s2->>'session_id')::uuid, d2);
  exception when others then
    if sqlerrm <> 'session_replaced' then raise; end if;
    rejected := true;
  end;
  if not rejected then raise exception 'FAIL: logout did not revoke session'; end if;
  -- Isolate rate counters in this transaction; rollback restores production state.
  delete from soltavoz_private.auth_attempts;
  for i in 1..10 loop
    if not public.sv_auth_attempt(key) then raise exception 'FAIL: rate denied too early'; end if;
  end loop;
  if public.sv_auth_attempt(key) then raise exception 'FAIL: rate not enforced'; end if;
  if has_function_privilege('authenticated', 'public.sv_create_account(uuid,uuid,text,text,boolean,timestamptz,uuid)', 'EXECUTE')
    or has_function_privilege('anon', 'public.sv_auth_attempt(text)', 'EXECUTE') then
    raise exception 'FAIL: client function access';
  end if;
  raise notice 'PASS: create/audit/logout/device binding/persistent limits/server-only permissions';
end $$;
rollback;
