ALTER TABLE public.beatmap
    DROP COLUMN "songName",
    DROP COLUMN "songSubName",
    DROP COLUMN "songAuthorName",
    DROP COLUMN "levelAuthorName",
    DROP COLUMN "fullSpread";

ALTER TABLE public.versions
    ALTER COLUMN bpm SET NOT NULL,
    ALTER COLUMN duration SET NOT NULL,
    ALTER COLUMN "songName" SET NOT NULL,
    ALTER COLUMN "songSubName" SET NOT NULL,
    ALTER COLUMN "songAuthorName" SET NOT NULL,
    ALTER COLUMN "levelAuthorName" SET NOT NULL;
