-- Run as postgres in the project's SQL Editor. No credentials in this file.
begin;

create schema if not exists soltavoz_private;
revoke all on schema soltavoz_private from public, anon, authenticated;

create table public.sv_admins (
  user_id uuid primary key references auth.users(id) on delete cascade
);
create table public.sv_plans (
  id uuid primary key default gen_random_uuid(),
  name text not null unique check (length(name) between 1 and 80),
  enabled boolean not null default true,
  created_at timestamptz not null default now()
);
create table public.sv_accounts (
  user_id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique check (username ~ '^[a-z0-9][a-z0-9._-]{2,39}$'),
  display_name text not null check (length(display_name) between 1 and 120),
  plan_id uuid references public.sv_plans(id),
  enabled boolean not null default false,
  expires_at timestamptz,
  session_generation bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table public.sv_devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.sv_accounts(user_id) on delete cascade,
  installation_id uuid not null,
  label text not null check (length(label) between 1 and 120),
  blocked boolean not null default false,
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  unique (user_id, installation_id),
  unique (id, user_id)
);
create table public.sv_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.sv_accounts(user_id) on delete cascade,
  device_id uuid not null,
  generation bigint not null,
  started_at timestamptz not null default now(),
  validated_at timestamptz not null default now(),
  offline_until timestamptz not null,
  revoked_at timestamptz,
  revoked_reason text,
  foreign key (device_id, user_id) references public.sv_devices(id, user_id),
  check (offline_until >= validated_at)
);
create unique index sv_one_current_session on public.sv_sessions(user_id)
  where revoked_at is null;
create table public.sv_audit (
  id bigint generated always as identity primary key,
  actor_id uuid,
  account_id uuid,
  action text not null,
  detail jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- All operations go through authenticated server functions. No client writes.
alter table public.sv_admins enable row level security;
alter table public.sv_plans enable row level security;
alter table public.sv_accounts enable row level security;
alter table public.sv_devices enable row level security;
alter table public.sv_sessions enable row level security;
alter table public.sv_audit enable row level security;
revoke all on public.sv_admins, public.sv_plans, public.sv_accounts,
  public.sv_devices, public.sv_sessions, public.sv_audit from anon, authenticated;
grant select, insert, update, delete on public.sv_admins, public.sv_plans,
  public.sv_accounts, public.sv_devices, public.sv_sessions to service_role;
grant select, insert on public.sv_audit to service_role;
grant usage, select on sequence public.sv_audit_id_seq to service_role;

-- The Edge Function must derive p_user from a VERIFIED Auth token, never body.
create function public.sv_open_session(p_user uuid, p_installation uuid, p_label text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  a public.sv_accounts%rowtype;
  d public.sv_devices%rowtype;
  s public.sv_sessions%rowtype;
  t timestamptz := clock_timestamp();
begin
  if p_installation is null or p_label is null or length(p_label) not between 1 and 120 then
    raise exception 'invalid_device';
  end if;
  select * into a from public.sv_accounts where user_id = p_user for update;
  if not found or not a.enabled then raise exception 'account_blocked'; end if;
  if a.expires_at is not null and a.expires_at <= t then raise exception 'account_expired'; end if;
  select * into d from public.sv_devices
    where user_id = p_user and installation_id = p_installation;
  if found and d.blocked then raise exception 'device_blocked'; end if;
  insert into public.sv_devices(user_id, installation_id, label, last_seen_at)
    values(p_user, p_installation, p_label, t)
    on conflict(user_id, installation_id) do update
      set label = excluded.label, last_seen_at = excluded.last_seen_at
    returning * into d;
  update public.sv_sessions set revoked_at = t, revoked_reason = 'replaced'
    where user_id = p_user and revoked_at is null;
  update public.sv_accounts set session_generation = session_generation + 1,
    updated_at = t where user_id = p_user returning * into a;
  insert into public.sv_sessions(user_id, device_id, generation, validated_at, offline_until)
    values(p_user, d.id, a.session_generation, t,
      least(t + interval '24 hours', coalesce(a.expires_at, 'infinity'::timestamptz)))
    returning * into s;
  insert into public.sv_audit(actor_id, account_id, action, detail)
    values(p_user, p_user, 'session_opened', jsonb_build_object('session_id', s.id, 'device_id', d.id));
  return jsonb_build_object('session_id', s.id, 'installation_id', p_installation,
    'generation', s.generation, 'validated_at', t, 'offline_until', s.offline_until);
end $$;

create function public.sv_validate_session(p_user uuid, p_session uuid, p_installation uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  a public.sv_accounts%rowtype;
  s public.sv_sessions%rowtype;
  d public.sv_devices%rowtype;
  t timestamptz := clock_timestamp();
begin
  -- Same lock order as open/admin operations: account, then session/device.
  select * into a from public.sv_accounts where user_id = p_user for update;
  if not found or not a.enabled then raise exception 'account_blocked'; end if;
  if a.expires_at is not null and a.expires_at <= t then raise exception 'account_expired'; end if;
  select * into s from public.sv_sessions where id = p_session and user_id = p_user;
  if not found or s.revoked_at is not null or s.generation <> a.session_generation then
    raise exception 'session_replaced';
  end if;
  select * into d from public.sv_devices where id = s.device_id and user_id = p_user;
  if d.installation_id is distinct from p_installation or d.blocked then
    raise exception 'device_blocked';
  end if;
  update public.sv_sessions set validated_at = t,
    offline_until = least(t + interval '24 hours', coalesce(a.expires_at, 'infinity'::timestamptz))
    where id = s.id returning * into s;
  update public.sv_devices set last_seen_at = t where id = d.id;
  return jsonb_build_object('session_id', s.id, 'installation_id', p_installation,
    'generation', s.generation, 'validated_at', t, 'offline_until', s.offline_until);
end $$;

create function public.sv_set_access(p_admin uuid, p_user uuid, p_enabled boolean, p_expires timestamptz, p_plan uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if not exists(select 1 from public.sv_admins where user_id = p_admin) then
    raise exception 'admin_required';
  end if;
  if p_enabled is null then raise exception 'invalid_access'; end if;
  perform 1 from public.sv_accounts where user_id = p_user for update;
  if not found then raise exception 'account_not_found'; end if;
  update public.sv_accounts set enabled = p_enabled, expires_at = p_expires,
    plan_id = p_plan, updated_at = clock_timestamp(), session_generation = session_generation + 1
    where user_id = p_user;
  -- Any administrative change invalidates earlier leases online; re-login required.
  update public.sv_sessions set revoked_at = clock_timestamp(), revoked_reason = 'access_changed'
    where user_id = p_user and revoked_at is null;
  insert into public.sv_audit(actor_id, account_id, action, detail)
    values(p_admin, p_user, 'access_changed', jsonb_build_object('enabled', p_enabled,
      'expires_at', p_expires, 'plan_id', p_plan));
end $$;

revoke all on function public.sv_open_session(uuid,uuid,text) from public, anon, authenticated;
revoke all on function public.sv_validate_session(uuid,uuid,uuid) from public, anon, authenticated;
revoke all on function public.sv_set_access(uuid,uuid,boolean,timestamptz,uuid) from public, anon, authenticated;
grant execute on function public.sv_open_session(uuid,uuid,text) to service_role;
grant execute on function public.sv_validate_session(uuid,uuid,uuid) to service_role;
grant execute on function public.sv_set_access(uuid,uuid,boolean,timestamptz,uuid) to service_role;

commit;
