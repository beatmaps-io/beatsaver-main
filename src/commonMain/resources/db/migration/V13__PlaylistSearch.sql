CREATE INDEX playlist_search ON public.playlist USING gin
    (bs_unaccent(name || ' '::text || description) COLLATE pg_catalog."default" gin_trgm_ops)
    WHERE "deletedAt" IS NULL;