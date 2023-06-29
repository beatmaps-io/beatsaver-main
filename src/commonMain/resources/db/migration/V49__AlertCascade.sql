ALTER TABLE public.alert_recipient
    DROP CONSTRAINT alert_fk,
    ADD CONSTRAINT alert_fk FOREIGN KEY ("alertId")
        REFERENCES public.alert ("alertId") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE;
