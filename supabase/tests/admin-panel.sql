begin;
do $$
declare
  a uuid := gen_random_uuid(); u uuid := gen_random_uuid(); d uuid := gen_random_uuid();
  s jsonb; p jsonb; device_id uuid; rejected boolean;
begin
  insert into auth.users(id,aud,role) values(a,'authenticated','authenticated'),(u,'authenticated','authenticated');
  insert into public.sv_admins(user_id) values(a);
  perform public.sv_create_account(a,u,'test_'||replace(u::text,'-',''),'Panel test',true,null,null);
  p := public.sv_create_plan(a,'Panel test '||a::text);
  perform public.sv_set_plan(a,(p->>'id')::uuid,'Changed '||a::text,false);
  if exists(select 1 from public.sv_plans where id=(p->>'id')::uuid and enabled) then raise exception 'FAIL: plan state'; end if;
  s := public.sv_open_session(u,d,'Test box');
  select id into device_id from public.sv_devices where user_id=u and installation_id=d;
  perform public.sv_set_device(a,device_id,true);
  rejected := false;
  begin perform public.sv_open_session(u,d,'Blocked box');
  exception when others then if sqlerrm<>'device_blocked' then raise; end if; rejected := true; end;
  if not rejected then raise exception 'FAIL: blocked device login'; end if;
  perform public.sv_set_device(a,device_id,false);
  if not exists(select 1 from public.sv_sessions where id=(s->>'session_id')::uuid and revoked_at is not null) then raise exception 'FAIL: unblocked old session'; end if;
  s := public.sv_open_session(u,d,'New login');
  perform public.sv_revoke_account_sessions(a,u);
  rejected := false;
  begin perform public.sv_validate_session(u,(s->>'session_id')::uuid,d);
  exception when others then if sqlerrm<>'session_replaced' then raise; end if; rejected := true; end;
  if not rejected then raise exception 'FAIL: password reset did not revoke session'; end if;
  if has_function_privilege('authenticated','public.sv_set_device(uuid,uuid,boolean)','EXECUTE') then raise exception 'FAIL: client device mutation'; end if;
  raise notice 'PASS: plans/device blocking/session revocation/admin permissions';
end $$;
rollback;
