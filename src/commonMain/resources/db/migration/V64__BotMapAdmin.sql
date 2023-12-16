UPDATE beatmap
SET "declaredAi" = 'Admin'
WHERE NOT beatmap.ai AND beatmap.automapper;
