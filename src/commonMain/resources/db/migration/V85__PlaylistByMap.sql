CREATE INDEX IF NOT EXISTS playlist_bymap ON public.playlist_map USING btree ("mapId", "playlistId");
