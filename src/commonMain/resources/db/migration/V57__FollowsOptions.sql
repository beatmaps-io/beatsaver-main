ALTER TABLE public.follows
    ADD COLUMN "upload" boolean NOT NULL DEFAULT TRUE,
    ADD COLUMN "curation" boolean NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT follow_link UNIQUE USING INDEX follow_link;
