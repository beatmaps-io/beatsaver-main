CREATE TABLE dev.patreon
(
    id serial NOT NULL,
    "type" varchar(255) NOT NULL,
    txt text,
    "when" timestamp with time zone,
    CONSTRAINT dev_patreon_pkey PRIMARY KEY (id)
) TABLESPACE pg_default;

ALTER TABLE dev.patreon OWNER TO beatmaps;

ALTER TABLE ONLY public.uploader ADD CONSTRAINT patreon_unq UNIQUE ("patreonId");
