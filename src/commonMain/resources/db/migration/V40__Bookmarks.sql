CREATE TYPE public.playlistType AS ENUM
(
    'Public',
    'Private',
    'System'
);

ALTER TYPE public.playlistType OWNER TO beatmaps;

ALTER TABLE public.playlist
ADD COLUMN type playlistType NOT NULL DEFAULT 'Public';

UPDATE public.playlist
SET type = 'Private'
WHERE NOT public;

ALTER TABLE public.playlist
DROP COLUMN public;

ALTER TABLE public.uploader
ADD COLUMN "bookmarksId" integer;
