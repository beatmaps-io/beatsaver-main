ALTER TABLE public.versions ADD COLUMN "scheduledAt" timestamp with time zone;
ALTER TYPE mapstate ADD VALUE 'Scheduled';