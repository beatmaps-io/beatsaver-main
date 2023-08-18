CREATE INDEX IF NOT EXISTS collaboration_search
    ON public.collaboration USING btree
        ("collaboratorId" ASC NULLS LAST)
    TABLESPACE pg_default
    WHERE accepted;
