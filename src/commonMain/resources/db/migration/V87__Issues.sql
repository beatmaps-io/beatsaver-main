DO $$ BEGIN
    CREATE TYPE public.issueType AS ENUM (
        'MapperApplication',
        'MapReport',
        'UserReport',
        'PlaylistReport',
        'ReviewReport'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

ALTER TYPE public.issueType OWNER TO beatmaps;

ALTER TYPE public.alerttype ADD VALUE IF NOT EXISTS 'Issue';

CREATE TABLE IF NOT EXISTS public.issue
(
    "issueId" integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    creator integer NOT NULL,
    type issueType NOT NULL,
    data json NOT NULL,
    "createdAt" timestamp with time zone NOT NULL,
    "updatedAt" timestamp with time zone NOT NULL,
    "closedAt" timestamp with time zone,
    CONSTRAINT creator_fk FOREIGN KEY (creator)
        REFERENCES public.uploader (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
) TABLESPACE pg_default;

ALTER TABLE public.issue OWNER to beatmaps;

CREATE TABLE IF NOT EXISTS public.issue_comment
(
    "commentId" integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "issueId" integer NOT NULL,
    "user" integer NOT NULL,
    public boolean NOT NULL,
    text text NOT NULL,
    "createdAt" timestamp with time zone NOT NULL,
    "updatedAt" timestamp with time zone NOT NULL,
    "deletedAt" timestamp with time zone,
    CONSTRAINT issue_fk FOREIGN KEY ("issueId")
        REFERENCES public.issue ("issueId") MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT user_fk FOREIGN KEY ("user")
        REFERENCES public.uploader (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

ALTER TABLE public.issue_comment OWNER to beatmaps;
