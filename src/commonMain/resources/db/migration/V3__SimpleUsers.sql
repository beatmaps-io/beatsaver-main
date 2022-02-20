-- Case insensitive text column so that usernames are treated as taken regardless of casing
CREATE EXTENSION IF NOT EXISTS citext;
-- Add new user columns
ALTER TABLE public.uploader ADD COLUMN "verifyToken" character(40), ADD COLUMN active boolean NOT NULL DEFAULT false, ADD COLUMN "uniqueName" citext COLLATE pg_catalog."default";
-- All existing users should be active
UPDATE public.uploader SET active = TRUE;
-- Unique names should be unique unlike current names
CREATE UNIQUE INDEX simple_username ON uploader ("uniqueName");
-- Emails should be unique for simple accounts, we don't care about discord accounts
DROP INDEX email_idx; CREATE UNIQUE INDEX email_idx ON uploader (email) WHERE ("discordId" IS NULL);
