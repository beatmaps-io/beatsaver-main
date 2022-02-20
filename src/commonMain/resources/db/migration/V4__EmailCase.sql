-- Emails are also case insensitive
ALTER TABLE uploader ALTER COLUMN email TYPE citext;
