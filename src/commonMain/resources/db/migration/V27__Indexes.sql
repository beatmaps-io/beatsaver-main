DROP INDEX IF EXISTS link;

ALTER TABLE public.playlist_map
    ADD CONSTRAINT link UNIQUE ("playlistId", "mapId");

CREATE INDEX IF NOT EXISTS playlist_public
    ON public.playlist USING btree
    ("createdAt" ASC NULLS LAST)
    TABLESPACE pg_default
    WHERE public AND "deletedAt" IS NULL;
