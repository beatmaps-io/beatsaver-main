CREATE EXTENSION IF NOT EXISTS btree_gin WITH SCHEMA public; COMMENT ON EXTENSION btree_gin IS 'support for indexing common datatypes in GIN';
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public; COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';

CREATE TYPE public.characteristic AS ENUM (
    'Standard',
    'NoArrows',
    'OneSaber',
    '90Degree',
    '360Degree',
    'Lawless',
    'Lightshow'
); ALTER TYPE public.characteristic OWNER TO beatmaps;

CREATE TYPE public.diff AS ENUM (
    'Easy',
    'Normal',
    'Hard',
    'Expert',
    'ExpertPlus'
); ALTER TYPE public.diff OWNER TO beatmaps;

CREATE TYPE public.mapstate AS ENUM (
    'Uploaded',
    'Testplay',
    'Published',
    'Feedback'
); ALTER TYPE public.mapstate OWNER TO beatmaps;

CREATE FUNCTION public.corrected_similarity(string_a text, string_b text) RETURNS real
    LANGUAGE plpgsql
    AS $$
DECLARE
  base_score FLOAT4;
BEGIN
  base_score := substring_similarity(string_a, string_b);
  -- a good standard similarity score can raise the base_score
  RETURN base_score + ((1.0 - base_score) * SIMILARITY(string_a, string_b));
END; $$; ALTER FUNCTION public.corrected_similarity(string_a text, string_b text) OWNER TO beatmaps;

CREATE FUNCTION public.is_minimally_substring_similar(string_a text, string_b text) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
BEGIN
  RETURN corrected_similarity(string_a, string_b) >= 0.5;
END; $$; ALTER FUNCTION public.is_minimally_substring_similar(string_a text, string_b text) OWNER TO beatmaps;

CREATE SEQUENCE public.beatmap_key_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.beatmap (
    "mapId" integer DEFAULT nextval('public.beatmap_key_seq'::regclass) NOT NULL,
    name text NOT NULL,
    description text NOT NULL,
    uploader integer NOT NULL,
    bpm real NOT NULL,
    duration integer NOT NULL,
    "songName" text NOT NULL,
    "songSubName" text NOT NULL,
    "songAuthorName" text NOT NULL,
    "levelAuthorName" text NOT NULL,
    uploaded timestamp with time zone,
    automapper boolean NOT NULL,
    plays integer DEFAULT 0 NOT NULL,
    downloads integer DEFAULT 0 NOT NULL,
    "deletedAt" timestamp with time zone,
    bsupvote integer DEFAULT 0 NOT NULL,
    bsdownvote integer DEFAULT 0 NOT NULL,
    bsdownload integer DEFAULT 0 NOT NULL,
    score numeric(4,4) DEFAULT 0.5 NOT NULL,
    upvote integer DEFAULT 0 NOT NULL,
    downvote integer DEFAULT 0 NOT NULL,
    chroma boolean DEFAULT false NOT NULL,
    "minNps" numeric(8,3) DEFAULT 0 NOT NULL,
    "maxNps" numeric(8,3) DEFAULT 0 NOT NULL,
    "lastVoteAt" timestamp with time zone,
    noodle boolean DEFAULT false NOT NULL,
    ranked boolean DEFAULT false NOT NULL,
    "curatedAt" timestamp with time zone,
    "curatedBy" integer,
    "fullSpread" boolean DEFAULT false NOT NULL,
    "rankedAt" timestamp with time zone,
    "qualifiedAt" timestamp with time zone,
    qualified boolean DEFAULT false NOT NULL,
    ai boolean DEFAULT false NOT NULL,
    cinema boolean DEFAULT false NOT NULL,
    me boolean DEFAULT false NOT NULL
); ALTER TABLE public.beatmap OWNER TO beatmaps;

CREATE TABLE public.difficulty (
    "versionId" integer NOT NULL,
    njs real NOT NULL,
    "offsetTime" real NOT NULL,
    notes integer NOT NULL,
    bombs integer NOT NULL,
    obstacles integer NOT NULL,
    nps numeric(8,3) NOT NULL,
    length numeric(10,3) NOT NULL,
    "mapId" integer NOT NULL,
    characteristic public.characteristic NOT NULL,
    difficulty public.diff NOT NULL,
    "difficultyId" integer NOT NULL,
    events integer NOT NULL,
    chroma boolean DEFAULT false NOT NULL,
    me boolean DEFAULT false NOT NULL,
    ne boolean DEFAULT false NOT NULL,
    seconds numeric(10,3) NOT NULL,
    "pReset" integer NOT NULL,
    "pWarn" integer NOT NULL,
    "pError" integer NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    stars numeric(4,2),
    requirements character varying(64)[],
    suggestions character varying(255)[],
    information character varying(255)[],
    warnings character varying(255)[],
    cinema boolean DEFAULT false NOT NULL
); ALTER TABLE public.difficulty OWNER TO beatmaps;

CREATE SEQUENCE public."difficulty_difficultyId_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1; ALTER TABLE public."difficulty_difficultyId_seq" OWNER TO beatmaps;
ALTER SEQUENCE public."difficulty_difficultyId_seq" OWNED BY public.difficulty."difficultyId";
ALTER TABLE ONLY public.difficulty ALTER COLUMN "difficultyId" SET DEFAULT nextval('public."difficulty_difficultyId_seq"'::regclass);

CREATE TABLE public.downloads (
    "downloadId" bigint NOT NULL,
    hash character(40) NOT NULL,
    processed boolean DEFAULT false NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    remote character varying(15)
); ALTER TABLE public.downloads OWNER TO beatmaps;

CREATE SEQUENCE public."downloads_downloadId_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1; ALTER TABLE public."downloads_downloadId_seq" OWNER TO beatmaps;
ALTER SEQUENCE public."downloads_downloadId_seq" OWNED BY public.downloads."downloadId";
ALTER TABLE ONLY public.downloads ALTER COLUMN "downloadId" SET DEFAULT nextval('public."downloads_downloadId_seq"'::regclass);

CREATE TABLE public.modlog (
    "logId" integer NOT NULL,
    "userId" integer NOT NULL,
    "mapId" integer NOT NULL,
    "when" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    type smallint NOT NULL,
    action text NOT NULL
); ALTER TABLE public.modlog OWNER TO beatmaps;

CREATE SEQUENCE public."modlog_logId_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1; ALTER TABLE public."modlog_logId_seq" OWNER TO beatmaps;
ALTER SEQUENCE public."modlog_logId_seq" OWNED BY public.modlog."logId";
ALTER TABLE ONLY public.modlog ALTER COLUMN "logId" SET DEFAULT nextval('public."modlog_logId_seq"'::regclass);

CREATE TABLE public.plays (
    "Id" integer NOT NULL,
    "mapId" integer NOT NULL,
    "userId" bigint NOT NULL,
    "createdAt" timestamp with time zone DEFAULT '2020-12-23 15:50:29.645841+00'::timestamp with time zone NOT NULL
); ALTER TABLE public.plays OWNER TO beatmaps;

CREATE SEQUENCE public."plays_Id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1; ALTER TABLE public."plays_Id_seq" OWNER TO beatmaps;
ALTER SEQUENCE public."plays_Id_seq" OWNED BY public.plays."Id";
ALTER TABLE ONLY public.plays ALTER COLUMN "Id" SET DEFAULT nextval('public."plays_Id_seq"'::regclass);

CREATE TABLE public.testplay (
    "testplayId" integer NOT NULL,
    feedback text,
    video character varying(255),
    "versionId" integer NOT NULL,
    "userId" integer NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "feedbackAt" timestamp with time zone
); ALTER TABLE public.testplay OWNER TO beatmaps;

CREATE SEQUENCE public.testplay_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1; ALTER TABLE public.testplay_id_seq OWNER TO beatmaps;
ALTER SEQUENCE public.testplay_id_seq OWNED BY public.testplay."testplayId";
ALTER TABLE ONLY public.testplay ALTER COLUMN "testplayId" SET DEFAULT nextval('public.testplay_id_seq'::regclass);

CREATE TABLE public.uploader (
    id integer NOT NULL,
    name text DEFAULT '0'::text NOT NULL,
    hash character(24) DEFAULT NULL::bpchar,
    email text,
    "createdAt" timestamp without time zone DEFAULT now() NOT NULL,
    "steamId" bigint,
    "oculusId" bigint,
    testplay boolean DEFAULT false NOT NULL,
    avatar text,
    "discordId" bigint,
    admin boolean DEFAULT false NOT NULL,
    "uploadLimit" smallint DEFAULT 15 NOT NULL,
    upvotes integer DEFAULT 0 NOT NULL,
    password character(60)
); ALTER TABLE public.uploader OWNER TO beatmaps;

CREATE SEQUENCE public.uploader_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1; ALTER TABLE public.uploader_id_seq OWNER TO beatmaps;
ALTER SEQUENCE public.uploader_id_seq OWNED BY public.uploader.id;
ALTER TABLE ONLY public.uploader ALTER COLUMN id SET DEFAULT nextval('public.uploader_id_seq'::regclass);

CREATE TABLE public.versions (
    "versionId" integer NOT NULL,
    "mapId" integer NOT NULL,
    hash character(40) NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    feedback text,
    "testplayAt" timestamp with time zone,
    state public.mapstate DEFAULT 'Published'::public.mapstate NOT NULL,
    key64 character varying(8),
    "sageScore" smallint
); ALTER TABLE public.versions OWNER TO beatmaps;

CREATE SEQUENCE public."versions_versionId_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1; ALTER TABLE public."versions_versionId_seq" OWNER TO beatmaps;
ALTER SEQUENCE public."versions_versionId_seq" OWNED BY public.versions."versionId";
ALTER TABLE ONLY public.versions ALTER COLUMN "versionId" SET DEFAULT nextval('public."versions_versionId_seq"'::regclass);

CREATE TABLE public.vote (
    "Id" bigint NOT NULL,
    "mapId" integer NOT NULL,
    "userId" bigint NOT NULL,
    "createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    steam boolean DEFAULT true NOT NULL,
    vote boolean NOT NULL
); ALTER TABLE public.vote OWNER TO beatmaps;

CREATE SEQUENCE public."vote_Id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1; ALTER TABLE public."vote_Id_seq" OWNER TO beatmaps;
ALTER SEQUENCE public."vote_Id_seq" OWNED BY public.vote."Id";
ALTER TABLE ONLY public.vote ALTER COLUMN "Id" SET DEFAULT nextval('public."vote_Id_seq"'::regclass);
