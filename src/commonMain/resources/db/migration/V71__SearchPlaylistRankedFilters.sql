UPDATE public.playlist
SET config =
        jsonb_set(
                config::jsonb,
                '{"searchParams", "ranked"}'::text[],
                (case when config -> 'searchParams' ->> 'ranked' = 'true' then '"Ranked"' else '"All"' end)::jsonb,
                false
        )
WHERE type = 'Search'
  and config::jsonb -> 'searchParams' ? 'ranked'
