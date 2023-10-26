ALTER TYPE public.playlistType ADD VALUE IF NOT EXISTS 'Search';

ALTER TABLE public.playlist ADD COLUMN IF NOT EXISTS config json;
