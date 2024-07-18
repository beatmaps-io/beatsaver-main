DO
$$
DECLARE
    collab1Id INTEGER;
    collab2Id INTEGER;
BEGIN
    SELECT id INTO collab1Id FROM uploader WHERE "uniqueName" = 'collab1';
    SELECT id INTO collab2Id FROM uploader WHERE "uniqueName" = 'collab2';

    INSERT INTO collaboration ("mapId", "collaboratorId", "requestedAt", accepted, "uploadedAt") VALUES
         (1, collab1Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00'),
         (2, collab2Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00'),
         (3, collab1Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00'),
         (3, collab2Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00'),
         (4, collab1Id, '2021-01-01 00:00:00', FALSE, '2021-01-01 00:00:00'),
         (4, collab2Id, '2021-01-01 00:00:00', TRUE, '2021-01-01 00:00:00');
END $$;
