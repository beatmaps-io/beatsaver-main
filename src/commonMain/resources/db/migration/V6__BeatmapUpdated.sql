-- Add columns for when beatmap was last published / updated
ALTER TABLE public.beatmap
    ADD COLUMN "updatedAt" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN "lastPublishedAt" timestamp with time zone;

-- These aren't great defaults
UPDATE beatmap SET "lastPublishedAt" = uploaded, "updatedAt" = COALESCE("deletedAt", uploaded, CURRENT_TIMESTAMP);

-- Add indexes
CREATE INDEX top_updatedat ON public.beatmap USING btree ("updatedAt") WHERE "deletedAt" IS NULL;
CREATE INDEX top_lastpublishedat ON public.beatmap USING btree ("lastPublishedAt") WHERE "deletedAt" IS NULL;