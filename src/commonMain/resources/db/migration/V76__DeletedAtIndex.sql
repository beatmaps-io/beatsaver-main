CREATE INDEX IF NOT EXISTS deleted_idx
    ON public.beatmap USING btree
    ("deletedAt" ASC NULLS LAST)
    TABLESPACE pg_default
    WHERE "deletedAt" IS NOT NULL;
