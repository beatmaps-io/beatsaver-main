DROP TYPE IF EXISTS public.environment;
CREATE TYPE public.environment AS ENUM (
    'DefaultEnvironment',
    'TriangleEnvironment',
    'NiceEnvironment',
    'BigMirrorEnvironment',
    'KDAEnvironment',
    'MonstercatEnvironment',
    'CrabRaveEnvironment',
    'DragonsEnvironment',
    'OriginsEnvironment',
    'PanicEnvironment',
    'RocketEnvironment',
    'GreenDayEnvironment',
    'GreenDayGrenadeEnvironment',
    'TimbalandEnvironment',
    'FitBeatEnvironment',
    'LinkinParkEnvironment',
    'BTSEnvironment',
    'KaleidoscopeEnvironment',
    'InterscopeEnvironment',
    'SkrillexEnvironment',
    'BillieEnvironment',
    'HalloweenEnvironment',
    'GagaEnvironment',
    'GlassDesertEnvironment',
    'MultiplayerEnvironment',
    'WeaveEnvironment',
    'PyroEnvironment',
    'EDMEnvironment',
    'TheSecondEnvironment',
    'LizzoEnvironment',
    'TheWeekndEnvironment',
    'RockMixtapeEnvironment',
    'Dragons',
    'Panic',
    'QueenEnvironment',
    'LinkinPark2Environment',
    'TheRollingStonesEnvironment',
    'LatticeEnvironment'
);

ALTER TYPE public.environment OWNER TO beatmaps;

ALTER TABLE public.difficulty
    ADD COLUMN environment public.environment;
