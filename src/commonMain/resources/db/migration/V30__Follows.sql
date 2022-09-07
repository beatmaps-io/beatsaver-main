CREATE SEQUENCE public."follows_followId_seq"
    INCREMENT 1
    START 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public."follows_followId_seq" OWNER TO beatmaps;

CREATE TABLE public.follows
(
    "followId" integer NOT NULL DEFAULT nextval('"follows_followId_seq"'::regclass),
    "userId" integer,
    "followerId" integer,
    since timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT follow_pkey PRIMARY KEY ("followId"),
    CONSTRAINT user_fk FOREIGN KEY ("userId")
        REFERENCES public.uploader ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION,
    CONSTRAINT follower_fk FOREIGN KEY ("followerId")
        REFERENCES public.uploader ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION
);

ALTER TABLE public.follows OWNER TO beatmaps;

CREATE UNIQUE INDEX follow_link
    ON public.follows USING btree
        ("userId" ASC NULLS LAST, "followerId" ASC NULLS LAST)
    TABLESPACE pg_default;
