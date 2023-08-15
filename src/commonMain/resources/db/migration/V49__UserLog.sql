ALTER TABLE public.uploader ADD COLUMN "emailChangedAt" timestamp with time zone DEFAULT now() NOT NULL;

CREATE SEQUENCE public."userlog_logId_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER TABLE public."userlog_logId_seq" OWNER TO beatmaps;

CREATE TABLE public.userlog (
    "logId" integer DEFAULT nextval('public."userlog_logId_seq"'::regclass) NOT NULL,
    "userId" integer NOT NULL,
    "mapId" integer,
    "when" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    type smallint NOT NULL,
    action text NOT NULL,
    CONSTRAINT userlog_pkey PRIMARY KEY ("logId"),
    CONSTRAINT "userlog_mapFK" FOREIGN KEY ("mapId") REFERENCES public.beatmap("mapId") ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT "userlog_userFK" FOREIGN KEY ("userId") REFERENCES public.uploader(id) ON UPDATE CASCADE ON DELETE CASCADE
);
ALTER TABLE public.userlog OWNER TO beatmaps;

CREATE INDEX IF NOT EXISTS userlog_user ON public.userlog USING btree ("userId" ASC NULLS LAST);
