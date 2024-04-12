ALTER TABLE public.beatmap
    ADD COLUMN "blRanked" boolean DEFAULT false NOT NULL,
    ADD COLUMN "blQualified" boolean DEFAULT false NOT NULL,
    ADD COLUMN "blRankedAt" timestamp with time zone,
    ADD COLUMN "blQualifiedAt" timestamp with time zone;

ALTER TABLE public.difficulty
    ADD COLUMN "blStars" numeric(4,2),
    ADD COLUMN "blRankedAt" timestamp with time zone,
    ADD COLUMN "blQualifiedAt" timestamp with time zone;
