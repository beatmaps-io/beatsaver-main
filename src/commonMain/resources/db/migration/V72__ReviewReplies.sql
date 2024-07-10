CREATE SEQUENCE public."review_reply_replyId_seq"
    INCREMENT 1
    START 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.review_reply
(
    "replyId" integer NOT NULL DEFAULT nextval('"review_reply_replyId_seq"'::regclass),
    "reviewId" integer NOT NULL,
    "userId" integer NOT NULL,
    "text" text NOT NULL,
    "createdAt" timestamp with time zone NOT NULL,
    "updatedAt" timestamp with time zone,
    "deletedAt" timestamp with time zone,
    CONSTRAINT comment_pkey PRIMARY KEY ("replyId"),
    CONSTRAINT review_fk FOREIGN KEY ("reviewId")
        REFERENCES public.review ("reviewId") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT user_fk FOREIGN KEY ("userId")
        REFERENCES public.uploader ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION
);

ALTER TABLE public.review_reply OWNER TO beatmaps;

CREATE INDEX review_reply_link
    ON public.review_reply USING btree
        ("reviewId" ASC NULLS LAST, "userId" ASC NULLS LAST)
    TABLESPACE pg_default;
