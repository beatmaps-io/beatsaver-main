DROP INDEX IF EXISTS collaboration_search;

ALTER TABLE collaboration
    ADD COLUMN IF NOT EXISTS "uploadedAt" timestamp with time zone;

CREATE INDEX IF NOT EXISTS collaboration_search
    ON public.collaboration USING btree
        ("collaboratorId" ASC NULLS LAST, "uploadedAt" ASC NULLS LAST)
    TABLESPACE pg_default
    WHERE accepted;
