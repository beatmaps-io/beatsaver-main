ALTER TABLE public.follows
    ADD COLUMN "upload" boolean NOT NULL DEFAULT TRUE,
    ADD COLUMN "curation" boolean NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT follow_link UNIQUE USING INDEX follow_link;

ALTER TABLE public.uploader
    ADD COLUMN "curationAlerts" boolean NOT NULL DEFAULT TRUE,
    ADD COLUMN "reviewAlerts" boolean NOT NULL DEFAULT FALSE;
