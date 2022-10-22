DO $$
    BEGIN
        BEGIN
            ALTER TABLE public.difficulty
                ADD COLUMN "rankedAt" timestamp with time zone,
                ADD COLUMN "qualifiedAt" timestamp with time zone;
        EXCEPTION
            WHEN duplicate_column THEN RAISE NOTICE 'Columns already exist';
        END;
    END;
$$
