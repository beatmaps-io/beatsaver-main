UPDATE versions
SET bpm = b.bpm, duration = b.duration, "songName" = b."songName", "songSubName" = b."songSubName",
    "songAuthorName" = b."songAuthorName", "levelAuthorName" = b."levelAuthorName"
    FROM versions v
        LEFT JOIN beatmap b ON v."mapId" = b."mapId"
WHERE versions."versionId" = v."versionId";