ALTER TABLE public.uploader
    ADD COLUMN "description" text COLLATE pg_catalog."default" NOT NULL DEFAULT ''::text;
