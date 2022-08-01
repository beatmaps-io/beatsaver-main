CREATE SEQUENCE public."oauthClients_id_seq"
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 2147483647
    CACHE 1;

ALTER SEQUENCE public."oauthClients_id_seq" OWNER TO beatmaps;

CREATE TABLE public.oauthClients
(
    "id" integer NOT NULL DEFAULT nextval('"oauthClients_id_seq"'::regclass),
    "clientId" character varying(255) COLLATE pg_catalog."default" NOT NULL,
    "secret" character varying(255) COLLATE pg_catalog."default" NOT NULL,
    "name" character varying(255) COLLATE pg_catalog."default" NOT NULL,
    "scopes" text,
    "redirectUrl" text,
    "iconUrl" text
) TABLESPACE pg_default;

ALTER TABLE public.oauthClients OWNER to beatmaps;