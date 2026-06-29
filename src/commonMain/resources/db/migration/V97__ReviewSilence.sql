CREATE TABLE public.review_silence
(
    "silenceId" integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "userId" integer NOT NULL,
    "moderatorId" integer NOT NULL,
    "createdAt" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "silencedUntil" timestamp with time zone,
    "durationMinutes" integer,
    reason text,
    "revokedAt" timestamp with time zone,
    CONSTRAINT review_silence_user_fk FOREIGN KEY ("userId")
        REFERENCES public.uploader (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT review_silence_moderator_fk FOREIGN KEY ("moderatorId")
        REFERENCES public.uploader (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

ALTER TABLE public.review_silence OWNER TO beatmaps;

CREATE INDEX review_silence_active ON public.review_silence USING btree ("userId", "silencedUntil") WHERE "revokedAt" IS NULL;
