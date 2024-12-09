ALTER TABLE public.versions
    ADD COLUMN bpm real,
    ADD COLUMN duration integer,
    ADD COLUMN "songName" text,
    ADD COLUMN "songSubName" text,
    ADD COLUMN "songAuthorName" text,
    ADD COLUMN "levelAuthorName" text;

ALTER TABLE public.beatmap
    DROP COLUMN me,
    DROP COLUMN chroma,
    DROP COLUMN noodle,
    DROP COLUMN cinema;

ALTER TABLE public.difficulty
    DROP COLUMN me,
    DROP COLUMN chroma,
    DROP COLUMN ne,
    DROP COLUMN cinema;
