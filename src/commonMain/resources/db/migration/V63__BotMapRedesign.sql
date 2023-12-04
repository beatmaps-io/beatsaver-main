CREATE TYPE public.aiDeclarationType AS ENUM
(
    'Admin',
    'Uploader',
    'SageScore',
    'None'
);

ALTER TABLE beatmap
    ADD "declaredAi" aiDeclarationType NOT NULL DEFAULT 'None';

UPDATE beatmap
SET "declaredAi" = 'SageScore'
FROM beatmap AS b
         LEFT JOIN versions AS v ON b."mapId" = v."mapId"
WHERE beatmap."mapId" = b."mapId"
  AND v.state = 'Published'
  AND v."sageScore" < 0;

UPDATE beatmap
SET "declaredAi" = 'Uploader'
WHERE beatmap.ai