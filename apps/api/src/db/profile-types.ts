import type { ColumnType } from 'kysely';

export type ProfileGeneratedTimestamp = ColumnType<Date, Date | string | undefined, Date | string>;

export interface UserProfileTable {
  user_id: string;
  display_name_override: string | null;
  municipality: string | null;
  bio: string | null;
  public_role: 'agricultor' | 'propietario' | 'trabajador' | 'profesional_agricola' | 'tecnico' | 'empresa' | 'otro' | null;
  visibility: 'private' | 'public';
  created_at: ProfileGeneratedTimestamp;
  updated_at: ProfileGeneratedTimestamp;
}

export interface UserPreferenceTable {
  user_id: string;
  theme: 'system' | 'light' | 'dark';
  unit_system: 'metric';
  preferred_municipality: string | null;
  locale: string;
  community_notifications: boolean;
  weather_alerts: boolean;
  created_at: ProfileGeneratedTimestamp;
  updated_at: ProfileGeneratedTimestamp;
}

export interface ProfileDatabase {
  user_profiles: UserProfileTable;
  user_preferences: UserPreferenceTable;
}
