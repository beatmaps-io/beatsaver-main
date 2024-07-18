DO
$$
DECLARE
    collab1Id INTEGER;
    collab2Id INTEGER;
    map1Id INTEGER;
    map2Id INTEGER;
    map3Id INTEGER;
    map4Id INTEGER;
BEGIN
    SELECT id INTO collab1Id FROM uploader WHERE "uniqueName" = 'collab1';
    SELECT id INTO collab2Id FROM uploader WHERE "uniqueName" = 'collab2';

    SELECt "mapId" INTO map1Id FROM beatmap OFFSET 0 LIMIT 1;
    SELECT "mapId" INTO map2Id FROM beatmap OFFSET 1 LIMIT 1;
    SELECT "mapId" INTO map3Id FROM beatmap OFFSET 2 LIMIT 1;
    SELECT "mapId" INTO map4Id FROM beatmap OFFSET 3 LIMIT 1;

    INSERT INTO collaboration ("mapId", "collaboratorId", "requestedAt", accepted, "uploadedAt") VALUES
         (map1Id, collab1Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00'),
         (map2Id, collab2Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00'),
         (map3Id, collab1Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00'),
         (map3Id, collab2Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00'),
         (map4Id, collab1Id, '2021-01-01 00:00:00', FALSE, '2021-01-01 00:00:00'),
         (map4Id, collab2Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00');
END $$;
