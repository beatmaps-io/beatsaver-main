ALTER TABLE public.uploader
    ADD COLUMN "updatedAt" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN "statsUpdatedAt" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Add indexes
CREATE INDEX uploader_updatedat ON public.uploader USING btree ("updatedAt") WHERE "active";
