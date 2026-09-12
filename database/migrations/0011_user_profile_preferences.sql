BEGIN;

CREATE TABLE user_profiles (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  display_name_override TEXT CHECK (display_name_override IS NULL OR char_length(display_name_override) BETWEEN 2 AND 80),
  municipality TEXT CHECK (municipality IS NULL OR char_length(municipality) <= 120),
  bio TEXT CHECK (bio IS NULL OR char_length(bio) <= 500),
  public_role TEXT CHECK (public_role IS NULL OR public_role IN ('agricultor','propietario','trabajador','profesional_agricola','tecnico','empresa','otro')),
  visibility TEXT NOT NULL DEFAULT 'private' CHECK (visibility IN ('private','public')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_preferences (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  theme TEXT NOT NULL DEFAULT 'system' CHECK (theme IN ('system','light','dark')),
  unit_system TEXT NOT NULL DEFAULT 'metric' CHECK (unit_system IN ('metric')),
  preferred_municipality TEXT CHECK (preferred_municipality IS NULL OR char_length(preferred_municipality) <= 120),
  locale TEXT NOT NULL DEFAULT 'es-ES' CHECK (char_length(locale) BETWEEN 2 AND 20),
  community_notifications BOOLEAN NOT NULL DEFAULT true,
  weather_alerts BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
