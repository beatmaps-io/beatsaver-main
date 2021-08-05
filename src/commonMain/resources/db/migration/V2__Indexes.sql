ALTER TABLE ONLY public.beatmap ADD CONSTRAINT beatmap_pkey PRIMARY KEY ("mapId");
ALTER TABLE ONLY public.difficulty ADD CONSTRAINT diff_pk PRIMARY KEY ("difficultyId");
ALTER TABLE ONLY public.difficulty ADD CONSTRAINT diff_unique UNIQUE ("versionId", characteristic, difficulty);
ALTER TABLE ONLY public.downloads ADD CONSTRAINT downloads_pkey PRIMARY KEY ("downloadId");
ALTER TABLE ONLY public.uploader ADD CONSTRAINT hash UNIQUE (hash);
ALTER TABLE ONLY public.modlog ADD CONSTRAINT modlog_pkey PRIMARY KEY ("logId");
ALTER TABLE ONLY public.plays ADD CONSTRAINT plays_pkey PRIMARY KEY ("Id");
ALTER TABLE ONLY public.testplay ADD CONSTRAINT testplay_pkey PRIMARY KEY ("testplayId");
ALTER TABLE ONLY public.uploader ADD CONSTRAINT uploader_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.testplay ADD CONSTRAINT user_version_unique UNIQUE ("userId", "versionId");
ALTER TABLE ONLY public.versions ADD CONSTRAINT versions_pkey PRIMARY KEY ("versionId");
ALTER TABLE ONLY public.vote ADD CONSTRAINT vote_pkey PRIMARY KEY ("Id");
ALTER TABLE ONLY public.vote ADD CONSTRAINT vote_unique UNIQUE ("mapId", "userId", steam);

CREATE UNIQUE INDEX beatsaver_lookup ON public.versions USING btree (key64);
CREATE INDEX testplay_queue_idx ON public.versions USING btree (state, "testplayAt");
CREATE INDEX version_map_idx ON public.versions USING btree ("mapId");
CREATE UNIQUE INDEX versions_hash ON public.versions USING btree (hash);
CREATE INDEX vote_order ON public.uploader USING btree (upvotes);

CREATE INDEX bm_search ON public.beatmap USING gin ((((name || ' '::text) || description)) public.gin_trgm_ops) WHERE ("deletedAt" IS NULL);
CREATE INDEX plays_idx ON public.beatmap USING btree (plays) WHERE ("deletedAt" IS NULL);
CREATE INDEX profile_lookup ON public.beatmap USING btree (uploader, uploaded) WHERE ("deletedAt" IS NULL);
CREATE INDEX top_score ON public.beatmap USING btree (score) WHERE ("deletedAt" IS NULL);
CREATE INDEX votes_since ON public.beatmap USING btree ("lastVoteAt");

CREATE INDEX "diff_createdAt" ON public.difficulty USING btree ("createdAt");
CREATE UNIQUE INDEX diff_lookup ON public.difficulty USING btree ("versionId", characteristic, difficulty);
CREATE INDEX diff_map ON public.difficulty USING btree ("mapId");
CREATE INDEX diff_search ON public.difficulty USING btree (chroma, nps);

CREATE UNIQUE INDEX "discordIdx" ON public.uploader USING btree ("discordId");
CREATE UNIQUE INDEX email_idx ON public.uploader USING btree (email);
CREATE INDEX uploader_search ON public.uploader USING gin (name public.gin_trgm_ops);

CREATE INDEX fki_testplay_user_fk ON public.testplay USING btree ("userId");
CREATE INDEX fki_testplay_version_fk ON public.testplay USING btree ("versionId");

CREATE INDEX processed_idx ON public.downloads USING btree (processed);

CREATE INDEX top_uploaded ON public.beatmap USING btree (uploaded) WHERE ("deletedAt" IS NULL);

ALTER TABLE ONLY public.beatmap ADD CONSTRAINT beatmap_uploader_id_fk FOREIGN KEY (uploader) REFERENCES public.uploader(id) ON UPDATE CASCADE;
ALTER TABLE ONLY public.versions ADD CONSTRAINT beatmap_versions_fk FOREIGN KEY ("mapId") REFERENCES public.beatmap("mapId") ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE ONLY public.difficulty ADD CONSTRAINT diff_map_fk FOREIGN KEY ("mapId") REFERENCES public.beatmap("mapId") ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE ONLY public.difficulty ADD CONSTRAINT diff_versions_fk FOREIGN KEY ("versionId") REFERENCES public.versions("versionId") ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE ONLY public.modlog ADD CONSTRAINT "mapFK" FOREIGN KEY ("mapId") REFERENCES public.beatmap("mapId") ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE ONLY public.testplay ADD CONSTRAINT testplay_user_fk FOREIGN KEY ("userId") REFERENCES public.uploader(id) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE ONLY public.testplay ADD CONSTRAINT testplay_version_fk FOREIGN KEY ("versionId") REFERENCES public.versions("versionId") ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE ONLY public.modlog ADD CONSTRAINT "userFK" FOREIGN KEY ("userId") REFERENCES public.uploader(id);
ALTER TABLE ONLY public.vote ADD CONSTRAINT vote_beatmap_mapid_fk FOREIGN KEY ("mapId") REFERENCES public.beatmap("mapId") ON UPDATE CASCADE ON DELETE CASCADE;
