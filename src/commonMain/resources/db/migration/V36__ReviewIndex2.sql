ALTER TABLE ONLY public.review
    ADD CONSTRAINT review_unique UNIQUE ("mapId", creator),
    ADD COLUMN "updatedAt" timestamp with time zone;

UPDATE public.review SET "updatedAt" = "createdAt";

ALTER TABLE ONLY public.review
    ALTER COLUMN "updatedAt" SET NOT NULL;
