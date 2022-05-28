-- Add column to flag if item is available on r2
ALTER TABLE public.versions ADD COLUMN "r2" boolean NOT NULL DEFAULT false;