CREATE SEQUENCE public."collaboration_collaborationId_seq"
    INCREMENT 1
    START 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public.alert ADD COLUMN "collaborationId" integer DEFAULT NULL;

ALTER TYPE alerttype ADD VALUE 'Collaboration';

CREATE TABLE public.collaboration
(
    "collaborationId" integer NOT NULL DEFAULT nextval('"collaboration_collaborationId_seq"'::regclass),
    "mapId" integer,
    "collaboratorId" integer,
    "accepted" bool DEFAULT false,
    CONSTRAINT collaborator_pkey PRIMARY KEY ("collaborationId"),
    CONSTRAINT map_fk FOREIGN KEY ("mapId")
        REFERENCES public.beatmap ("mapId") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION,
    CONSTRAINT follower_fk FOREIGN KEY ("collaboratorId")
        REFERENCES public.uploader ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION
);

ALTER TABLE public.collaboration OWNER TO beatmaps;

CREATE UNIQUE INDEX collaboration_link
    ON public.collaboration USING btree
        ("mapId" ASC NULLS LAST, "collaboratorId" ASC NULLS LAST)
    TABLESPACE pg_default;
