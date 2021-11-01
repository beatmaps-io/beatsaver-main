ALTER TABLE public.modlog ADD COLUMN "targetUserId" integer DEFAULT NULL, ALTER COLUMN "mapId" DROP NOT NULL;

ALTER TABLE ONLY public.modlog ADD CONSTRAINT "targetFK" FOREIGN KEY ("targetUserId") REFERENCES public.uploader(id);
UPDATE modlog SET "targetUserId" = uploader FROM beatmap b WHERE modlog."mapId" = b."mapId";
ALTER TABLE modlog ALTER COLUMN "targetUserId" SET NOT NULL;