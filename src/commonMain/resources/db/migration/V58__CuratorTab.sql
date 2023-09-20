ALTER TABLE public.uploader
    ADD COLUMN "curatorTab" boolean NOT NULL DEFAULT FALSE;

UPDATE uploader SET "curatorTab" = TRUE WHERE curator;
