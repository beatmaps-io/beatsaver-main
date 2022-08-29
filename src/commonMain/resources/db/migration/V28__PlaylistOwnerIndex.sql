CREATE INDEX IF NOT EXISTS playlist_owner
    ON public.playlist USING btree
    ("owner" ASC NULLS LAST)
    TABLESPACE pg_default
    WHERE "deletedAt" IS NULL;
