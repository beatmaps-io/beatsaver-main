ALTER TABLE public.follows
    ADD COLUMN "collab" boolean NOT NULL DEFAULT TRUE;

UPDATE public.follows SET "collab" = "upload";