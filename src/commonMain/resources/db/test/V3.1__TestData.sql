INSERT INTO uploader (name, email, password, active, testplay, admin, "uniqueName") VALUES
    ('test', 'test@test.com', '$2a$12$3zoAVlxDlvGtoVywB04Amep3qTHmgaXivA3CMHb0YdZkLEHZxFNzi', TRUE, FALSE, FALSE, 'test'),  -- password: test
    ('admin', 'admin@test.com', '$2a$12$HxGPVg8oEr.igslnwc0RIONm732SVbXKg6Yu6C6kHOtKbbsEmjPCO', TRUE, TRUE, TRUE, 'admin'); -- password: admin

INSERT INTO beatmap (name, description, uploader, bpm, duration, "songName", "songSubName", "songAuthorName", "levelAuthorName", uploaded, automapper, ranked, qualified, score, upvote, downvote) VALUES
    ('test map', 'this is a test map', 1, 128, 120, 'test map', '', 'test', 'test', CURRENT_TIMESTAMP, FALSE, FALSE, FALSE, 0.5, 2, 2),
    ('beat sage map', 'this is a beat sage map', 1, 120, 120, 'beat sage map', '', 'test', 'beat sage', CURRENT_TIMESTAMP, TRUE, FALSE, FALSE, 0.359, 0, 2),
    ('ranked map', 'this is a ranked map', 2, 180, 180, 'ranked map', '', 'test', 'admin', CURRENT_TIMESTAMP, FALSE, TRUE, FALSE, 0.625, 4, 1),
    ('qualified map', 'this is a qualified map', 2, 150, 200, 'qualified map', '', 'test', 'admin', CURRENT_TIMESTAMP, FALSE, FALSE, TRUE, 0.742, 8, 0);
INSERT INTO versions ("versionId", "mapId", hash, state) VALUES
    (1, 1, 'a', 'Published'),   -- test map
    (2, 2, 'b', 'Published'),   -- beat sage map
    (3, 3, 'c', 'Published'),   -- ranked map
    (4, 4, 'd', 'Published');   -- qualified map
INSERT INTO difficulty ("versionId", njs, "offsetTime", notes, bombs, obstacles, nps, length, "mapId", characteristic, difficulty, "difficultyId", events, seconds, "pReset", "pWarn", "pError") VALUES
    (1, 20, 0, 500, 50, 5, 6.5, 120, 1, 'Standard', 'ExpertPlus', 1, 5000, 120, 0, 0, 0),   -- test map
    (2, 18, 0, 350, 0, 0, 6.5, 120, 2, 'Standard', 'Expert', 2, 0, 120, 5, 10, 15),         -- beat sage map
    (2, 18, 0, 180, 0, 0, 6.5, 120, 2, 'OneSaber', 'Expert', 3, 0, 120, 5, 10, 15),         -- beat sage map
    (2, 18, 0, 350, 0, 0, 6.5, 120, 2, 'NoArrows', 'Expert', 4, 0, 120, 5, 10, 15),         -- beat sage map
    (3, 22, 0, 700, 70, 7, 6.5, 180, 3, 'Standard', 'ExpertPlus', 5, 700, 180, 0, 0, 0),    -- ranked map
    (3, 18, 0, 550, 50, 6, 6.5, 180, 3, 'Standard', 'Expert', 6, 550, 180, 0, 0, 0),        -- ranked map
    (3, 15, 0, 300, 20, 4, 6.5, 180, 3, 'Standard', 'Hard', 7, 300, 180, 0, 0, 0),          -- ranked map
    (4, 21, 0, 750, 75, 5, 6.5, 200, 4, 'Standard', 'ExpertPlus', 8, 750, 200, 0, 0, 0),    -- qualified map
    (4, 16, 0, 600, 75, 5, 6.5, 200, 4, 'Standard', 'Expert', 9, 600, 200, 0, 0, 0),        -- qualified map
    (4, 12, 0, 300, 75, 5, 6.5, 200, 4, 'Standard', 'Hard', 10, 300, 200, 0, 0, 0);         -- qualified map
