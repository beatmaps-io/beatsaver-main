ALTER TABLE public.uploader
    ADD COLUMN "suspendedAt" timestamp with time zone;

UPDATE public.uploader
    SET "suspendedAt" = CURRENT_TIMESTAMP
    WHERE "uploadLimit" = 0