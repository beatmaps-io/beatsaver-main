CREATE TABLE public.patreon
(
    "patreonId" integer NOT NULL,
    pledge integer,
    tier integer,
    active boolean NOT NULL,
    "expireAt" timestamp with time zone,
    CONSTRAINT patreon_pkey PRIMARY KEY ("patreonId")
) TABLESPACE pg_default;

ALTER TABLE public.patreon OWNER TO beatmaps;

ALTER TABLE public.uploader
    ADD COLUMN "patreonId" integer,
    ADD CONSTRAINT "patreonFK" FOREIGN KEY ("patreonId") REFERENCES public.patreon("patreonId");
