-- Add new user columns
ALTER TABLE public.uploader ADD COLUMN "renamedAt" timestamp with time zone DEFAULT now() NOT NULL;
