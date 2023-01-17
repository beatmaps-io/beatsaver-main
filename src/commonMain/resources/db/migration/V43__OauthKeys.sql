ALTER TABLE public.oauthclients ADD PRIMARY KEY (id);
CREATE UNIQUE INDEX IF NOT EXISTS "oauthClientId" ON public.oauthclients USING btree ("clientId");

ALTER TABLE ONLY public.oa_access_token ADD CONSTRAINT "clientFK" FOREIGN KEY ("client_id") REFERENCES public.oauthclients("clientId");
ALTER TABLE ONLY public.oa_refresh_token ADD CONSTRAINT "clientFK" FOREIGN KEY ("client_id") REFERENCES public.oauthclients("clientId");

CREATE INDEX IF NOT EXISTS access_token_client ON public.oa_access_token USING btree ("client_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS refresh_token_client ON public.oa_refresh_token USING btree ("client_id" ASC NULLS LAST);

CREATE INDEX IF NOT EXISTS access_token_user ON public.oa_access_token USING btree ("user_name" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS refresh_token_user ON public.oa_refresh_token USING btree ("user_name" ASC NULLS LAST);

ALTER TABLE ONLY public.oa_access_token ADD CONSTRAINT "userFK" FOREIGN KEY ("user_name") REFERENCES public.uploader(id);
ALTER TABLE ONLY public.oa_refresh_token ADD CONSTRAINT "userFK" FOREIGN KEY ("user_name") REFERENCES public.uploader(id);
