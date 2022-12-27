CREATE INDEX IF NOT EXISTS "verifiedMapperIdx"
    ON public.uploader USING btree
    (id ASC NULLS LAST)
    TABLESPACE pg_default
    WHERE "verifiedMapper";
