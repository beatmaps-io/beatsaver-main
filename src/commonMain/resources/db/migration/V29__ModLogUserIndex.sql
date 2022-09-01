CREATE INDEX IF NOT EXISTS modlog_target ON public.modlog USING btree ("targetUserId" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS modlog_moderator ON public.modlog USING btree ("userId" ASC NULLS LAST);
