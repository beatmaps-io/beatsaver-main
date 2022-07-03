CREATE TYPE public.alertType AS ENUM
(
    'Deletion',
    'Review',
    'MapRelease',
    'Curation'
);

ALTER TYPE public.alertType OWNER TO beatmaps;

CREATE SEQUENCE public."alert_alertId_seq"
    INCREMENT 1
    START 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public."alert_alertId_seq" OWNER TO beatmaps;

CREATE TABLE public.alert
(
    "alertId" integer NOT NULL DEFAULT nextval('"alert_alertId_seq"'::regclass),
    head character varying(255) COLLATE pg_catalog."default" NOT NULL,
    body text COLLATE pg_catalog."default" NOT NULL,
    "type" public.alertType NOT NULL,
    "sentAt" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alert_pkey PRIMARY KEY ("alertId")
) TABLESPACE pg_default;

ALTER TABLE public.alert OWNER TO beatmaps;

CREATE SEQUENCE public."alert_recipient_id_seq"
    INCREMENT 1
    START 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.alert_recipient
(
    id integer NOT NULL DEFAULT nextval('"alert_recipient_id_seq"'::regclass),
    "recipientId" integer NOT NULL,
    "alertId" integer NOT NULL,
    "readAt" timestamp with time zone,
    CONSTRAINT alert_recipient_pkey PRIMARY KEY (id),
    CONSTRAINT alert_fk FOREIGN KEY ("alertId")
        REFERENCES public.alert ("alertId") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION,
    CONSTRAINT recipient_fk FOREIGN KEY ("recipientId")
        REFERENCES public.uploader (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE NO ACTION
) TABLESPACE pg_default;

ALTER TABLE public.alert_recipient OWNER to beatmaps;

CREATE UNIQUE INDEX alert_link
    ON public.alert_recipient USING btree
        ("recipientId" ASC NULLS LAST, "alertId" ASC NULLS LAST)
    TABLESPACE pg_default;
