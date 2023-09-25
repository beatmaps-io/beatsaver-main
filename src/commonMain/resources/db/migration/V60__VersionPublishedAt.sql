ALTER TABLE public.versions ADD COLUMN "lastPublishedAt" timestamp with time zone;

UPDATE public.versions SET "lastPublishedAt" = NOW() WHERE "mapId" IN (SELECT "mapId" FROM versions v WHERE v.state = 'Published')
