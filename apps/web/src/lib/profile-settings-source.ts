import { apiFetch } from './api-client';

export type PublicProfileRole =
  | 'agricultor'
  | 'propietario'
  | 'trabajador'
  | 'profesional_agricola'
  | 'tecnico'
  | 'empresa'
  | 'otro';

export type ProfileSettingsInput = {
  display_name: string | null;
  municipality: string | null;
  bio: string | null;
  public_role: PublicProfileRole | null;
  visibility: 'private' | 'public';
};

export type PersonalPreferencesInput = {
  theme: 'system' | 'light' | 'dark';
  unit_system: 'metric';
  preferred_municipality: string | null;
  locale: string;
  community_notifications: boolean;
  weather_alerts: boolean;
};

export function optionalText(value: string) {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

export async function saveProfileSettings(input: ProfileSettingsInput) {
  return apiFetch('/api/v1/me/profile', {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export async function savePersonalPreferences(input: PersonalPreferencesInput) {
  return apiFetch('/api/v1/me/preferences', {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}
