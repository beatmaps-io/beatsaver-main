CREATE SEQUENCE public."playlist_playlistId_seq"
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 2147483647
    CACHE 1;

ALTER SEQUENCE public."playlist_playlistId_seq" OWNER TO beatmaps;

CREATE TABLE public.playlist
(
    "playlistId" integer NOT NULL DEFAULT nextval('"playlist_playlistId_seq"'::regclass),
    name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    owner integer NOT NULL,
    "createdAt" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    public boolean NOT NULL DEFAULT true,
    description text COLLATE pg_catalog."default" NOT NULL DEFAULT ''::text,
    "deletedAt" timestamp with time zone,
    "songsChangedAt" timestamp with time zone,
    CONSTRAINT playlist_pkey PRIMARY KEY ("playlistId"),
    CONSTRAINT owner_fk FOREIGN KEY (owner)
        REFERENCES public.uploader (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION
) TABLESPACE pg_default;

ALTER TABLE public.playlist OWNER to beatmaps;

CREATE SEQUENCE public.playlist_map_id_seq
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1;

ALTER SEQUENCE public.playlist_map_id_seq OWNER TO beatmaps;

CREATE TABLE public.playlist_map
(
    id bigint NOT NULL DEFAULT nextval('playlist_map_id_seq'::regclass),
    "playlistId" integer NOT NULL,
    "mapId" integer NOT NULL,
    "order" real NOT NULL,
    CONSTRAINT playlist_map_pkey PRIMARY KEY (id),
    CONSTRAINT map_fk FOREIGN KEY ("mapId")
        REFERENCES public.beatmap ("mapId") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT playlist_fk FOREIGN KEY ("playlistId")
        REFERENCES public.playlist ("playlistId") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
) TABLESPACE pg_default;

ALTER TABLE public.playlist_map OWNER to beatmaps;

CREATE UNIQUE INDEX link
    ON public.playlist_map USING btree
    ("playlistId" ASC NULLS LAST, "mapId" ASC NULLS LAST)
    TABLESPACE pg_default;