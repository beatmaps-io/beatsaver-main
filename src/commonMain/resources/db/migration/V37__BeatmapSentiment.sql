ALTER TABLE public.beatmap
    ADD COLUMN "sentiment" numeric(4,3) NOT NULL DEFAULT 0,
    ADD COLUMN "reviews" integer NOT NULL DEFAULT 0;
