CREATE EXTENSION unaccent;

CREATE OR REPLACE FUNCTION bs_unaccent(str text)
  RETURNS text
AS
$BODY$
    select unaccent($1);
$BODY$
LANGUAGE sql
IMMUTABLE;

-- Remove special characters from bm_search index
CREATE INDEX bm_search2 ON public.beatmap USING gin
    (bs_unaccent(name || ' '::text || description || ' '::text || "levelAuthorName") COLLATE pg_catalog."default" gin_trgm_ops)
    WHERE "deletedAt" IS NULL;
DROP INDEX bm_search;
ALTER INDEX bm_search2 RENAME TO bm_search;
