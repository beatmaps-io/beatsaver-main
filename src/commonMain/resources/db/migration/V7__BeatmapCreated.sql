-- Add columns for when beatmap was created
ALTER TABLE public.beatmap
    ADD COLUMN "createdAt" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- These aren't great defaults
UPDATE beatmap SET "createdAt" = COALESCE(uploaded, CURRENT_TIMESTAMP);

-- Add indexes
CREATE INDEX top_createdat ON public.beatmap USING btree ("createdAt") WHERE "deletedAt" IS NULL;
