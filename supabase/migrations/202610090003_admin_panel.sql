begin;
create function public.sv_create_plan(p_admin uuid, p_name text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare p public.sv_plans%rowtype;
begin
  if not exists(select 1 from public.sv_admins where user_id = p_admin) then raise exception 'admin_required'; end if;
  insert into public.sv_plans(name) values(p_name) returning * into p;
  insert into public.sv_audit(actor_id, action, detail) values(p_admin, 'plan_created', jsonb_build_object('plan_id', p.id));
  return to_jsonb(p);
end $$;
create function public.sv_set_plan(p_admin uuid, p_plan uuid, p_name text, p_enabled boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not exists(select 1 from public.sv_admins where user_id = p_admin) then raise exception 'admin_required'; end if;
  update public.sv_plans set name = p_name, enabled = p_enabled where id = p_plan;
  if not found then raise exception 'plan_not_found'; end if;
  insert into public.sv_audit(actor_id, action, detail) values(p_admin, 'plan_changed', jsonb_build_object('plan_id', p_plan));
end $$;
create function public.sv_set_device(p_admin uuid, p_device uuid, p_blocked boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare u uuid;
begin
  if not exists(select 1 from public.sv_admins where user_id = p_admin) then raise exception 'admin_required'; end if;
  if p_blocked is null then raise exception 'invalid_access'; end if;
  select user_id into u from public.sv_devices where id = p_device;
  if not found then raise exception 'device_not_found'; end if;
  perform 1 from public.sv_accounts where user_id = u for update;
  update public.sv_devices set blocked = p_blocked where id = p_device;
  if p_blocked then
    update public.sv_sessions set revoked_at = clock_timestamp(), revoked_reason = 'device_blocked'
      where user_id = u and device_id = p_device and revoked_at is null;
  end if;
  insert into public.sv_audit(actor_id, account_id, action, detail)
    values(p_admin, u, 'device_changed', jsonb_build_object('device_id', p_device, 'blocked', p_blocked));
end $$;
create function public.sv_revoke_account_sessions(p_admin uuid, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not exists(select 1 from public.sv_admins where user_id = p_admin) then raise exception 'admin_required'; end if;
  perform 1 from public.sv_accounts where user_id = p_user for update;
  if not found then raise exception 'account_not_found'; end if;
  update public.sv_accounts set session_generation = session_generation + 1, updated_at = clock_timestamp() where user_id = p_user;
  update public.sv_sessions set revoked_at = clock_timestamp(), revoked_reason = 'password_reset'
    where user_id = p_user and revoked_at is null;
  insert into public.sv_audit(actor_id, account_id, action) values(p_admin, p_user, 'password_reset_requested');
end $$;
revoke all on function public.sv_create_plan(uuid,text) from public, anon, authenticated;
revoke all on function public.sv_set_plan(uuid,uuid,text,boolean) from public, anon, authenticated;
revoke all on function public.sv_set_device(uuid,uuid,boolean) from public, anon, authenticated;
revoke all on function public.sv_revoke_account_sessions(uuid,uuid) from public, anon, authenticated;
grant execute on function public.sv_create_plan(uuid,text) to service_role;
grant execute on function public.sv_set_plan(uuid,uuid,text,boolean) to service_role;
grant execute on function public.sv_set_device(uuid,uuid,boolean) to service_role;
grant execute on function public.sv_revoke_account_sessions(uuid,uuid) to service_role;
commit;
