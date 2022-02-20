INSERT INTO beatmap (name, description, uploader, bpm, duration, "songName", "songSubName", "songAuthorName", "levelAuthorName", uploaded, automapper, ranked, qualified, score, upvote, downvote) VALUES
    ('test map', 'this is a test map', 1, 128, 120, 'test map', '', 'test', 'test', CURRENT_TIMESTAMP, FALSE, FALSE, FALSE, 0.5, 2, 2),
    ('beat sage map', 'this is a beat sage map', 1, 120, 120, 'beat sage map', '', 'test', 'beat sage', CURRENT_TIMESTAMP, TRUE, FALSE, FALSE, 0.359, 0, 2),
    ('ranked map', 'this is a ranked map', 2, 180, 180, 'ranked map', '', 'test', 'admin', CURRENT_TIMESTAMP, FALSE, TRUE, FALSE, 0.625, 4, 1),
    ('qualified map', 'this is a qualified map', 2, 150, 200, 'qualified map', '', 'test', 'admin', CURRENT_TIMESTAMP, FALSE, FALSE, TRUE, 0.742, 8, 0);
INSERT INTO versions ("mapId", hash, state) VALUES
    ((SELECT "mapId" FROM beatmap WHERE name = 'test map'), 'a', 'Published'),
    ((SELECT "mapId" FROM beatmap WHERE name = 'beat sage map'), 'b', 'Published'),
    ((SELECT "mapId" FROM beatmap WHERE name = 'ranked map'), 'c', 'Published'),
    ((SELECT "mapId" FROM beatmap WHERE name = 'qualified map'), 'd', 'Published');
INSERT INTO difficulty ("versionId", njs, "offsetTime", notes, bombs, obstacles, nps, length, "mapId", characteristic, difficulty, events, seconds, "pReset", "pWarn", "pError") VALUES
    ((SELECT "versionId" FROM versions WHERE hash = 'a'), 20, 0, 500, 50, 5, 6.5, 120, (SELECT "mapId" FROM versions WHERE hash = 'a'), 'Standard', 'ExpertPlus', 5000, 120, 0, 0, 0),
    ((SELECT "versionId" FROM versions WHERE hash = 'b'), 18, 0, 350, 0, 0, 6.5, 120, (SELECT "mapId" FROM versions WHERE hash = 'b'), 'Standard', 'Expert', 0, 120, 5, 10, 15),
    ((SELECT "versionId" FROM versions WHERE hash = 'b'), 18, 0, 180, 0, 0, 6.5, 120, (SELECT "mapId" FROM versions WHERE hash = 'b'), 'OneSaber', 'Expert', 0, 120, 5, 10, 15),
    ((SELECT "versionId" FROM versions WHERE hash = 'b'), 18, 0, 350, 0, 0, 6.5, 120, (SELECT "mapId" FROM versions WHERE hash = 'b'), 'NoArrows', 'Expert', 0, 120, 5, 10, 15),
    ((SELECT "versionId" FROM versions WHERE hash = 'c'), 22, 0, 700, 70, 7, 6.5, 180, (SELECT "mapId" FROM versions WHERE hash = 'c'), 'Standard', 'ExpertPlus', 700, 180, 0, 0, 0),
    ((SELECT "versionId" FROM versions WHERE hash = 'c'), 18, 0, 550, 50, 6, 6.5, 180, (SELECT "mapId" FROM versions WHERE hash = 'c'), 'Standard', 'Expert', 550, 180, 0, 0, 0),
    ((SELECT "versionId" FROM versions WHERE hash = 'c'), 15, 0, 300, 20, 4, 6.5, 180, (SELECT "mapId" FROM versions WHERE hash = 'c'), 'Standard', 'Hard', 300, 180, 0, 0, 0),
    ((SELECT "versionId" FROM versions WHERE hash = 'd'), 21, 0, 750, 75, 5, 6.5, 200, (SELECT "mapId" FROM versions WHERE hash = 'd'), 'Standard', 'ExpertPlus', 750, 200, 0, 0, 0),
    ((SELECT "versionId" FROM versions WHERE hash = 'd'), 16, 0, 600, 75, 5, 6.5, 200, (SELECT "mapId" FROM versions WHERE hash = 'd'), 'Standard', 'Expert', 600, 200, 0, 0, 0),
    ((SELECT "versionId" FROM versions WHERE hash = 'd'), 12, 0, 300, 75, 5, 6.5, 200, (SELECT "mapId" FROM versions WHERE hash = 'd'), 'Standard', 'Hard', 300, 200, 0, 0, 0);
