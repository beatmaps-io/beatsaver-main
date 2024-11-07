create or replace function public.array_merge(arr1 anyarray, arr2 anyarray)
    returns anyarray language sql immutable
as $$
select array_agg(distinct elem order by elem)
from (
         select unnest(arr1) elem
         union
         select unnest(arr2)
     ) s
$$;

create or replace aggregate array_merge_agg(anyarray) (
    sfunc = array_merge,
    stype = anyarray
);
