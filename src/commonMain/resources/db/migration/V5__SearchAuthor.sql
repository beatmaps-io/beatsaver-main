-- Add levelAuthorName to bm_search index
DROP INDEX bm_search;
CREATE INDEX bm_search ON public.beatmap USING gin
    ((name || ' '::text || description || ' '::text || "levelAuthorName") COLLATE pg_catalog."default" gin_trgm_ops)
    WHERE "deletedAt" IS NULL;
