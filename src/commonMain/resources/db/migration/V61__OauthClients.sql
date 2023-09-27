ALTER TABLE public.oauthclients
    ADD COLUMN "scopes_new" character varying(64)[] NOT NULL DEFAULT array[]::varchar[],
    ADD COLUMN "redirectUrl_new" text[] NOT NULL DEFAULT array[]::text[];

UPDATE public.oauthclients SET scopes_new = string_to_array(scopes, ','), "redirectUrl_new" = string_to_array("redirectUrl", ',');

ALTER TABLE public.oauthclients
    DROP COLUMN scopes,
    DROP COLUMN "redirectUrl";
ALTER TABLE public.oauthclients RENAME COLUMN scopes_new TO scopes;
ALTER TABLE public.oauthclients RENAME COLUMN "redirectUrl_new" TO "redirectUrl";
