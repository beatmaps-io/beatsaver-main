ALTER TABLE public.beatmap
    ADD COLUMN "tags" character varying[] COLLATE pg_catalog."default";

CREATE INDEX bm_tags
    ON public.beatmap USING gin
    (tags COLLATE pg_catalog."default")
    TABLESPACE pg_default;

ALTER TABLE public.uploader
    ADD COLUMN curator boolean NOT NULL DEFAULT false;

ALTER TABLE public.playlist
    ADD COLUMN "curatedAt" timestamp with time zone,
    ADD COLUMN "curatedBy" integer;