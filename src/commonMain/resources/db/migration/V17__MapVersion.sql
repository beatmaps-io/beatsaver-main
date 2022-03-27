ALTER TABLE public.difficulty
    ADD COLUMN "schemaVersion" character varying(10) NOT NULL DEFAULT '2.2.0',
    ADD COLUMN "arcs" integer NOT NULL DEFAULT 0,
    ADD COLUMN "chains" integer NOT NULL DEFAULT 0;