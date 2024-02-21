ALTER TYPE alerttype ADD VALUE 'Follow';

ALTER TABLE public.uploader
ADD COLUMN "followAlerts" boolean NOT NULL DEFAULT TRUE;

ALTER TABLE public.follows
ADD COLUMN "following" boolean NOT NULL DEFAULT TRUE;
