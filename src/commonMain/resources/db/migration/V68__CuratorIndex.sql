CREATE INDEX IF NOT EXISTS curator_list ON public.uploader USING btree (name) WHERE (curator);
