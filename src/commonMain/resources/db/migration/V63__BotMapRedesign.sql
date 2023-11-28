CREATE TYPE public.aiDeclarationType AS ENUM
(
    'Admin',
    'Uploader',
    'SageScore',
    'None'
);

ALTER TABLE beatmap
    ADD "declaredAi" aiDeclarationType;

UPDATE beatmap
SET "declaredAi" = 'SageScore'
FROM beatmap AS b
         LEFT JOIN versions AS v ON b."mapId" = v."mapId"
WHERE beatmap."mapId" = b."mapId"
  AND v.state = 'Published'
  AND v."sageScore" < 0;