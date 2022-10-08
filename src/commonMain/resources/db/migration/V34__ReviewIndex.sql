CREATE INDEX review_map ON public.review USING btree ("mapId", "curatedAt" DESC NULLS LAST, "createdAt" DESC) WHERE ("deletedAt" IS NULL);
CREATE INDEX review_user ON public.review USING btree (creator, "createdAt" DESC) WHERE ("deletedAt" IS NULL);
