begin;
do $$
declare q text := 'test-'||gen_random_uuid()::text;
begin
 if public.sv_search_cache(q,null) is not null then raise exception 'FAIL: cache miss'; end if;
 perform public.sv_search_cache(q,'[]'::jsonb);
 if public.sv_search_cache(q,null) is distinct from '[]'::jsonb then raise exception 'FAIL: empty result cache'; end if;
 delete from soltavoz_private.search_budget;
 for i in 1..80 loop if not public.sv_search_budget() then raise exception 'FAIL: budget early rejection'; end if; end loop;
 if public.sv_search_budget() then raise exception 'FAIL: budget exceeded'; end if;
 if has_function_privilege('authenticated','public.sv_search_budget()','EXECUTE') then raise exception 'FAIL: direct budget access'; end if;
 raise notice 'PASS: shared cache, daily budget and server-only permissions';
end $$;
rollback;
