UPDATE beatmap
SET "declaredAi" = 'Admin'
FROM beatmap AS b
         LEFT JOIN versions AS v ON b."mapId" = v."mapId"
WHERE beatmap."mapId" = b."mapId"
  AND v.state = 'Published'
  AND v."sageScore" > -4
  AND NOT beatmap.ai
  AND beatmap.automapper;
