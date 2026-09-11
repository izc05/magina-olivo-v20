'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import type { AuthUser, UserPreferences, UserProfile } from './auth-provider';
import {
  optionalText,
  savePersonalPreferences,
  saveProfileSettings,
  type PublicProfileRole,
} from '@/lib/profile-settings-source';
import styles from './profile-settings-editor.module.css';

type SaveState = 'idle' | 'saving' | 'saved' | 'error';

const roleOptions: Array<{ value: PublicProfileRole; label: string }> = [
  { value: 'agricultor', label: 'Agricultor' },
  { value: 'propietario', label: 'Propietario' },
  { value: 'trabajador', label: 'Trabajador del campo' },
  { value: 'profesional_agricola', label: 'Profesional agrícola' },
  { value: 'tecnico', label: 'Técnico' },
  { value: 'empresa', label: 'Empresa' },
  { value: 'otro', label: 'Otro perfil' },
];

function statusLabel(state: SaveState) {
  if (state === 'saving') return 'Guardando…';
  if (state === 'saved') return 'Guardado ✓';
  if (state === 'error') return 'Revisar';
  return 'Editable';
}

export function ProfileSettingsEditor({
  user,
  profile,
  preferences,
  onSaved,
}: {
  user: AuthUser;
  profile: UserProfile | null;
  preferences: UserPreferences | null;
  onSaved: () => Promise<void>;
}) {
  const [displayName, setDisplayName] = useState(user.display_name);
  const [municipality, setMunicipality] = useState(profile?.municipality ?? '');
  const [bio, setBio] = useState(profile?.bio ?? '');
  const [publicRole, setPublicRole] = useState<PublicProfileRole | ''>(profile?.public_role ?? '');
  const [visibility, setVisibility] = useState<'private' | 'public'>(profile?.visibility ?? 'private');
  const [profileState, setProfileState] = useState<SaveState>('idle');
  const [profileError, setProfileError] = useState<string | null>(null);

  const [theme, setTheme] = useState<UserPreferences['theme']>(preferences?.theme ?? 'system');
  const [preferredMunicipality, setPreferredMunicipality] = useState(preferences?.preferred_municipality ?? '');
  const [communityNotifications, setCommunityNotifications] = useState(preferences?.community_notifications ?? true);
  const [weatherAlerts, setWeatherAlerts] = useState(preferences?.weather_alerts ?? true);
  const [preferenceState, setPreferenceState] = useState<SaveState>('idle');
  const [preferenceError, setPreferenceError] = useState<string | null>(null);

  useEffect(() => {
    setDisplayName(user.display_name);
    setMunicipality(profile?.municipality ?? '');
    setBio(profile?.bio ?? '');
    setPublicRole(profile?.public_role ?? '');
    setVisibility(profile?.visibility ?? 'private');
  }, [profile, user.display_name]);

  useEffect(() => {
    setTheme(preferences?.theme ?? 'system');
    setPreferredMunicipality(preferences?.preferred_municipality ?? '');
    setCommunityNotifications(preferences?.community_notifications ?? true);
    setWeatherAlerts(preferences?.weather_alerts ?? true);
  }, [preferences]);

  const profileDirty = useMemo(() => (
    displayName.trim() !== user.display_name
    || optionalText(municipality) !== (profile?.municipality ?? null)
    || optionalText(bio) !== (profile?.bio ?? null)
    || (publicRole || null) !== (profile?.public_role ?? null)
    || visibility !== (profile?.visibility ?? 'private')
  ), [bio, displayName, municipality, profile, publicRole, user.display_name, visibility]);

  const preferencesDirty = useMemo(() => (
    theme !== (preferences?.theme ?? 'system')
    || optionalText(preferredMunicipality) !== (preferences?.preferred_municipality ?? null)
    || communityNotifications !== (preferences?.community_notifications ?? true)
    || weatherAlerts !== (preferences?.weather_alerts ?? true)
  ), [communityNotifications, preferredMunicipality, preferences, theme, weatherAlerts]);

  async function submitProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedName = displayName.trim();
    if (normalizedName.length < 2) {
      setProfileState('error');
      setProfileError('El nombre debe tener al menos 2 caracteres.');
      return;
    }

    setProfileState('saving');
    setProfileError(null);
    try {
      await saveProfileSettings({
        display_name: normalizedName,
        municipality: optionalText(municipality),
        bio: optionalText(bio),
        public_role: publicRole || null,
        visibility,
      });
      await onSaved();
      setProfileState('saved');
    } catch (error) {
      console.error('Unable to save profile settings', error);
      setProfileState('error');
      setProfileError('No se han podido guardar los datos. Comprueba la conexión e inténtalo de nuevo.');
    }
  }

  async function submitPreferences(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPreferenceState('saving');
    setPreferenceError(null);
    try {
      await savePersonalPreferences({
        theme,
        unit_system: 'metric',
        preferred_municipality: optionalText(preferredMunicipality),
        locale: preferences?.locale ?? 'es-ES',
        community_notifications: communityNotifications,
        weather_alerts: weatherAlerts,
      });
      await onSaved();
      setPreferenceState('saved');
    } catch (error) {
      console.error('Unable to save personal preferences', error);
      setPreferenceState('error');
      setPreferenceError('No se han podido guardar las preferencias. Comprueba la conexión e inténtalo de nuevo.');
    }
  }

  return (
    <>
      <form className={`card profile-card premium-profile-card ${styles.card}`} onSubmit={submitProfile}>
        <div className="profile-card-head">
          <div><h3>Identidad y perfil</h3><small>Lo que quieres mostrar sobre ti.</small></div>
          <span aria-live="polite">{statusLabel(profileState)}</span>
        </div>

        <div className={styles.grid}>
          <label className={styles.field}>
            <span>Nombre visible</span>
            <input value={displayName} onChange={(event) => { setDisplayName(event.target.value); setProfileState('idle'); }} minLength={2} maxLength={80} autoComplete="name" />
          </label>
          <label className={styles.field}>
            <span>Municipio</span>
            <input value={municipality} onChange={(event) => { setMunicipality(event.target.value); setProfileState('idle'); }} maxLength={120} placeholder="Ej. Bedmar" />
          </label>
          <label className={styles.field}>
            <span>Actividad</span>
            <select value={publicRole} onChange={(event) => { setPublicRole(event.target.value as PublicProfileRole | ''); setProfileState('idle'); }}>
              <option value="">Sin indicar</option>
              {roleOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
          <label className={styles.field}>
            <span>Visibilidad</span>
            <select value={visibility} onChange={(event) => { setVisibility(event.target.value as 'private' | 'public'); setProfileState('idle'); }}>
              <option value="private">Privada</option>
              <option value="public">Pública</option>
            </select>
          </label>
        </div>

        <label className={styles.field}>
          <span>Presentación <small>{bio.length}/500</small></span>
          <textarea value={bio} onChange={(event) => { setBio(event.target.value); setProfileState('idle'); }} maxLength={500} rows={4} placeholder="Cuéntanos brevemente tu relación con el olivar o tu actividad." />
        </label>

        <p className={styles.hint}>{visibility === 'public' ? 'Tu nombre, municipio, actividad y presentación podrán formar parte de superficies públicas cuando estén habilitadas.' : 'Tu perfil está en privado. Tus fincas y datos de trabajo siguen siendo privados en ambos casos.'}</p>
        {profileError ? <p className={styles.error} role="alert">{profileError}</p> : null}
        <button className={styles.primaryButton} type="submit" disabled={!profileDirty || profileState === 'saving'}>{profileState === 'saving' ? 'Guardando perfil…' : 'Guardar perfil'}</button>
      </form>

      <form className={`card profile-card premium-profile-card ${styles.card}`} onSubmit={submitPreferences}>
        <div className="profile-card-head">
          <div><h3>Preferencias personales</h3><small>Se guardan en tu cuenta.</small></div>
          <span aria-live="polite">{statusLabel(preferenceState)}</span>
        </div>

        <div className={styles.grid}>
          <label className={styles.field}>
            <span>Tema</span>
            <select value={theme} onChange={(event) => { setTheme(event.target.value as UserPreferences['theme']); setPreferenceState('idle'); }}>
              <option value="system">Automático</option>
              <option value="light">Claro</option>
              <option value="dark">Oscuro</option>
            </select>
          </label>
          <label className={styles.field}>
            <span>Municipio preferido</span>
            <input value={preferredMunicipality} onChange={(event) => { setPreferredMunicipality(event.target.value); setPreferenceState('idle'); }} maxLength={120} placeholder="Para tiempo y contenido local" />
          </label>
        </div>

        <label className={styles.toggle}><span><strong>Avisos meteorológicos</strong><small>Usar tus preferencias para avisos de tiempo.</small></span><input type="checkbox" checked={weatherAlerts} onChange={(event) => { setWeatherAlerts(event.target.checked); setPreferenceState('idle'); }} /></label>
        <label className={styles.toggle}><span><strong>Avisos de comunidad</strong><small>Novedades relevantes de la parte pública de Mágina.</small></span><input type="checkbox" checked={communityNotifications} onChange={(event) => { setCommunityNotifications(event.target.checked); setPreferenceState('idle'); }} /></label>

        <p className={styles.hint}>Las unidades de esta Beta son métricas (ha, kg, mm). Las alertas específicas de finca, economía o actividad se configuran en sus tarjetas inferiores.</p>
        {preferenceError ? <p className={styles.error} role="alert">{preferenceError}</p> : null}
        <button className={styles.primaryButton} type="submit" disabled={!preferencesDirty || preferenceState === 'saving'}>{preferenceState === 'saving' ? 'Guardando preferencias…' : 'Guardar preferencias'}</button>
      </form>
    </>
  );
}
