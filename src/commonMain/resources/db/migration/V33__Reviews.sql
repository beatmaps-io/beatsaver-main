CREATE SEQUENCE public."review_reviewId_seq"
    INCREMENT 1
    START 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public."review_reviewId_seq" OWNER TO beatmaps;

CREATE TABLE public.review
(
    "reviewId" integer NOT NULL DEFAULT nextval('public."review_reviewId_seq"'::regclass),
    creator integer NOT NULL,
    "mapId" integer NOT NULL,
    text text NOT NULL,
    sentiment smallint NOT NULL,
    "createdAt" timestamp with time zone NOT NULL,
    "curatedAt" timestamp with time zone,
    "deletedAt" timestamp with time zone,
    PRIMARY KEY ("reviewId"),
    FOREIGN KEY (creator)
        REFERENCES public.uploader (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION,
    FOREIGN KEY ("mapId")
        REFERENCES public.beatmap ("mapId") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION
);

ALTER TABLE public.review OWNER to beatmaps;
ALTER SEQUENCE public."review_reviewId_seq" OWNED BY public.review."reviewId";
ALTER SEQUENCE public."oauthClients_id_seq" OWNED BY public.oauthClients."id";
ALTER SEQUENCE public."playlist_playlistId_seq" OWNED BY public.playlist."playlistId";
ALTER SEQUENCE public."playlist_map_id_seq" OWNED BY public.playlist_map."id";
ALTER SEQUENCE public."follows_followId_seq" OWNED BY public.follows."followId";
ALTER SEQUENCE public."alert_alertId_seq" OWNED BY public.alert."alertId";
ALTER SEQUENCE public."alert_recipient_id_seq" OWNED BY public.alert_recipient."id";
ALTER SEQUENCE public."beatmap_key_seq" OWNED BY public.beatmap."mapId";
