begin;
-- Persistent limits across Edge Function instances. Keys contain hashes only.
create table soltavoz_private.auth_attempts (
  key text primary key,
  window_start timestamptz not null,
  count integer not null
);
create function public.sv_auth_attempt(p_key text) returns boolean
language plpgsql security definer set search_path = '' as $$
declare t timestamptz := clock_timestamp(); total integer; per_identity integer;
begin
  if p_key !~ '^[a-f0-9]{64}$' then raise exception 'invalid_limit_key'; end if;
  -- Global row is always locked first to serialize counter changes.
  insert into soltavoz_private.auth_attempts(key, window_start, count) values('global', t, 1)
  on conflict(key) do update set
    window_start = case when auth_attempts.window_start <= t - interval '1 minute' then t else auth_attempts.window_start end,
    count = case when auth_attempts.window_start <= t - interval '1 minute' then 1 else auth_attempts.count + 1 end
  returning count into total;
  if total > 120 then return false; end if;
  delete from soltavoz_private.auth_attempts where key <> 'global' and window_start < t - interval '10 minutes';
  insert into soltavoz_private.auth_attempts(key, window_start, count) values(p_key, t, 1)
  on conflict(key) do update set
    window_start = case when auth_attempts.window_start <= t - interval '1 minute' then t else auth_attempts.window_start end,
    count = case when auth_attempts.window_start <= t - interval '1 minute' then 1 else auth_attempts.count + 1 end
  returning count into per_identity;
  return per_identity <= 10;
end $$;

create function public.sv_create_account(p_admin uuid, p_user uuid, p_username text,
  p_display text, p_enabled boolean, p_expires timestamptz, p_plan uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare a public.sv_accounts%rowtype;
begin
  if not exists(select 1 from public.sv_admins where user_id = p_admin) then raise exception 'admin_required'; end if;
  insert into public.sv_accounts(user_id, username, display_name, enabled, expires_at, plan_id)
    values(p_user, p_username, p_display, p_enabled, p_expires, p_plan) returning * into a;
  insert into public.sv_audit(actor_id, account_id, action) values(p_admin, p_user, 'account_created');
  return to_jsonb(a) - 'session_generation';
end $$;

create function public.sv_close_session(p_user uuid, p_session uuid, p_installation uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  perform 1 from public.sv_accounts where user_id = p_user for update;
  update public.sv_sessions s set revoked_at = clock_timestamp(), revoked_reason = 'logout'
    where s.id = p_session and s.user_id = p_user and s.revoked_at is null
      and exists(select 1 from public.sv_devices d where d.id = s.device_id
        and d.user_id = p_user and d.installation_id = p_installation);
  -- Idempotent; an old logout cannot revoke the replacement session.
  if found then
    insert into public.sv_audit(actor_id, account_id, action, detail)
      values(p_user, p_user, 'logout', jsonb_build_object('session_id', p_session));
  end if;
end $$;
revoke all on function public.sv_auth_attempt(text) from public, anon, authenticated;
revoke all on function public.sv_create_account(uuid,uuid,text,text,boolean,timestamptz,uuid) from public, anon, authenticated;
revoke all on function public.sv_close_session(uuid,uuid,uuid) from public, anon, authenticated;
grant execute on function public.sv_auth_attempt(text) to service_role;
grant execute on function public.sv_create_account(uuid,uuid,text,text,boolean,timestamptz,uuid) to service_role;
grant execute on function public.sv_close_session(uuid,uuid,uuid) to service_role;
commit;
