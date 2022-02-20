-- Emails should be unique, discord only accounts won't have emails
DROP INDEX email_idx; CREATE UNIQUE INDEX email_idx ON uploader (email) WHERE (active OR "verifyToken" IS NOT NULL);
