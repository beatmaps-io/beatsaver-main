CREATE INDEX IF NOT EXISTS collaboration_search1
    ON public.collaboration USING btree
        ("collaboratorId" ASC NULLS LAST, "requestedAt" ASC NULLS LAST)
    TABLESPACE pg_default
    WHERE NOT accepted;
