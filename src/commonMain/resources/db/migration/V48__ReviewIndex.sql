CREATE INDEX review_top_createdat ON public.review USING btree ("createdAt") WHERE "deletedAt" IS NULL;
