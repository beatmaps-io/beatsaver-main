CREATE INDEX IF NOT EXISTS playlist_type
    ON public.playlist USING btree
        ("createdAt" ASC NULLS LAST, "type")
    TABLESPACE pg_default
    WHERE "type" != 'Private' AND "deletedAt" IS NULL;
