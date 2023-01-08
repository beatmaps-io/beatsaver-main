CREATE TABLE IF NOT EXISTS public."oa_access_token" (
    "token_id" varchar(256) NOT NULL,
    "type" varchar(256) NOT NULL,
    "expiration" timestamp NOT NULL,
    "scope" varchar(256) NOT NULL,
    "user_name" integer,
    "metadata" text NOT NULL,
    "client_id" varchar(256) NOT NULL,
    "refresh_token" varchar(256),
    PRIMARY KEY ("token_id")
);

ALTER TABLE public."oa_access_token" OWNER TO beatmaps;

CREATE TABLE IF NOT EXISTS public."oa_refresh_token" (
    "token_id" varchar(256) NOT NULL,
    "expiration" timestamp NOT NULL,
    "scope" varchar(256) NOT NULL,
    "user_name" integer,
    "metadata" text NOT NULL,
    "client_id" varchar(256) NOT NULL,
    PRIMARY KEY ("token_id")
);

ALTER TABLE public."oa_refresh_token" OWNER TO beatmaps;
