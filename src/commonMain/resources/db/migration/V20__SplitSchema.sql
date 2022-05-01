CREATE SCHEMA IF NOT EXISTS counters;
CREATE SCHEMA IF NOT EXISTS dev;

ALTER TABLE public.downloads SET SCHEMA counters;
ALTER TABLE public.vote SET SCHEMA counters;
ALTER TABLE public.plays SET SCHEMA counters;
ALTER TABLE public.testplay SET SCHEMA dev;
