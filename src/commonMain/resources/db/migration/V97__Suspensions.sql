CREATE TYPE public.suspendType AS ENUM
(
    'Upload',
    'Review'
);

CREATE TABLE public.suspensions
(
    "suspensionId" integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "userId" integer NOT NULL,
    "moderatorId" integer NOT NULL,
    "createdAt" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expireAt" timestamp with time zone NOT NULL DEFAULT 'infinity',
    "type" public.suspendType NOT NULL,
    reason text,
    "revokedAt" timestamp with time zone,
    CONSTRAINT suspensions_user_fk FOREIGN KEY ("userId")
        REFERENCES public.uploader (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT suspensions_moderator_fk FOREIGN KEY ("moderatorId")
        REFERENCES public.uploader (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

ALTER TABLE public.suspensions OWNER TO beatmaps;

CREATE INDEX suspensions_active ON public.suspensions USING btree ("userId", "expireAt");

INSERT INTO suspensions ("userId", "moderatorId", "createdAt", "expireAt", type, reason, "revokedAt")
    SELECT id, (SELECT id FROM uploader WHERE admin LIMIT 1), NOW(), 'infinity', 'Upload', '', NULL FROM uploader WHERE "suspendedAt" IS NOT NULL;

INSERT INTO suspensions ("userId", "moderatorId", "createdAt", "expireAt", type, reason, "revokedAt")
    SELECT id, (SELECT id FROM uploader WHERE admin LIMIT 1), NOW(), 'infinity', 'Review', '', NULL FROM uploader WHERE "suspendedAt" IS NOT NULL;
