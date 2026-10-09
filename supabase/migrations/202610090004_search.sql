begin;
create table soltavoz_private.search_cache(query text primary key, songs jsonb not null, expires_at timestamptz not null);
create table soltavoz_private.search_budget(day date primary key, calls integer not null);
create function public.sv_search_cache(p_query text,p_songs jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
  if length(p_query) not between 2 and 160 then raise exception 'invalid_query'; end if;
  if p_songs is null then
    select songs into result from soltavoz_private.search_cache where query=p_query and expires_at>clock_timestamp();
    return result;
  end if;
  if jsonb_typeof(p_songs)<>'array' or jsonb_array_length(p_songs)>15 or octet_length(p_songs::text)>16384 then raise exception 'invalid_songs'; end if;
  perform pg_catalog.pg_advisory_xact_lock(1847308);
  delete from soltavoz_private.search_cache where expires_at<=clock_timestamp();
  insert into soltavoz_private.search_cache(query,songs,expires_at) values(p_query,p_songs,clock_timestamp()+interval '15 minutes')
    on conflict(query) do update set songs=excluded.songs,expires_at=excluded.expires_at;
  delete from soltavoz_private.search_cache where query in
    (select query from soltavoz_private.search_cache order by expires_at desc offset 200);
  return p_songs;
end $$;
create function public.sv_search_budget() returns boolean
language plpgsql security definer set search_path='' as $$
declare n integer; today date := (clock_timestamp() at time zone 'UTC')::date;
begin
  insert into soltavoz_private.search_budget(day,calls) values(today,1)
    on conflict(day) do update set calls=search_budget.calls+1 where search_budget.calls<80 returning calls into n;
  delete from soltavoz_private.search_budget where day<today-7;
  return n is not null;
end $$;
revoke all on function public.sv_search_cache(text,jsonb) from public,anon,authenticated;
revoke all on function public.sv_search_budget() from public,anon,authenticated;
grant execute on function public.sv_search_cache(text,jsonb) to service_role;
grant execute on function public.sv_search_budget() to service_role;
commit;
