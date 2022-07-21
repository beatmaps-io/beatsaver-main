UPDATE beatmap
    SET automapper = TRUE
    FROM
        beatmap AS b LEFT JOIN versions AS v ON b."mapId" = v."mapId"
    WHERE
        b."mapId" = beatmap."mapId"
        AND state = 'Published'
        AND "sageScore" < 0;
