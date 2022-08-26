ALTER TABLE public.playlist
    ADD COLUMN "minNps" numeric(8,3) DEFAULT 0 NOT NULL,
    ADD COLUMN "maxNps" numeric(8,3) DEFAULT 0 NOT NULL,
    ADD COLUMN "totalMaps" integer DEFAULT 0 NOT NULL;

UPDATE playlist p
SET "totalMaps" = sub."totalMaps", "minNps" = sub."minNps", "maxNps" = sub."maxNps"
    FROM (
         SELECT pm."playlistId", COUNT(b.duration) "totalMaps", MIN("minNps") as "minNps", MAX("maxNps") as "maxNps"
         FROM playlist_map pm
                  INNER JOIN beatmap b on b."mapId" = pm."mapId"
                  INNER JOIN versions v on b."mapId" = v."mapId" AND v.state = 'Published'
         GROUP BY pm."playlistId"
     ) sub
WHERE sub."playlistId" = p."playlistId";
