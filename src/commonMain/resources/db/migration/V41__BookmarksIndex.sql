CREATE INDEX IF NOT EXISTS playlist_type
    ON public.playlist USING btree
        ("createdAt" ASC NULLS LAST, "type")
    TABLESPACE pg_default
    WHERE "type" != 'Private' AND "deletedAt" IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS bookmark_playlist
    ON public.playlist USING btree ("owner")
    WHERE "type" = 'System';
