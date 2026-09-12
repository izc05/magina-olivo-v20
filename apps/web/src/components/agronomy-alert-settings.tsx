'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { apiFetch } from '@/lib/api-client';

type Preferences = {
  enabled: boolean;
  notify_caution: boolean;
  notify_avoid: boolean;
  lead_hours: number;
};

const DEFAULTS: Preferences = {
  enabled: false,
  notify_caution: false,
  notify_avoid: true,
  lead_hours: 24,
};

export function AgronomyAlertSettings() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [preferences, setPreferences] = useState<Preferences>(DEFAULTS);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const available = apiConfigured && status === 'authenticated' && Boolean(selectedWorkspaceId);

  useEffect(() => {
    if (!available || !selectedWorkspaceId) {
      setPreferences(DEFAULTS);
      return;
    }
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const data = await apiFetch<Preferences>('/api/v1/agronomy-alerts/preferences', { workspaceId: selectedWorkspaceId });
        if (!cancelled) setPreferences(data);
      } catch (error) {
        console.warn('Unable to load agronomy alert preferences', error);
        if (!cancelled) setMessage('No se han podido cargar los avisos agronómicos.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [available, selectedWorkspaceId]);

  async function save(next: Preferences) {
    if (!available || !selectedWorkspaceId) return;
    setPreferences(next);
    setSaving(true);
    setMessage(null);
    try {
      const saved = await apiFetch<Preferences>('/api/v1/agronomy-alerts/preferences', {
        workspaceId: selectedWorkspaceId,
        method: 'PUT',
        body: JSON.stringify(next),
      });
      setPreferences(saved);
      setMessage('Preferencias guardadas.');
    } catch (error) {
      console.warn('Unable to save agronomy alert preferences', error);
      setMessage('No se han podido guardar los cambios.');
    } finally {
      setSaving(false);
    }
  }

  if (!available) {
    return <section className="card profile-card premium-profile-card">
      <div className="profile-card-head"><h3>Avisos agronómicos</h3><span>Con servidor</span></div>
      <p>Podrás recibir avisos relacionados con tareas previstas cuando Mágina tenga previsión y contexto radar suficientemente recientes.</p>
      <small>En la preview no se activan notificaciones reales.</small>
    </section>;
  }

  return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Avisos agronómicos</h3><span>{loading ? 'Cargando…' : saving ? 'Guardando…' : preferences.enabled ? 'Activos' : 'Desactivados'}</span></div>
    <p>Solo se generan para tareas programadas y nunca completan ni cancelan una labor automáticamente.</p>

    <label className="profile-line">
      <span>Activar avisos</span>
      <input type="checkbox" checked={preferences.enabled} onChange={(event) => void save({ ...preferences, enabled: event.target.checked })} />
    </label>

    <label className="profile-line">
      <span>Avisar cuando conviene evitar</span>
      <input type="checkbox" checked={preferences.notify_avoid} disabled={!preferences.enabled} onChange={(event) => void save({ ...preferences, notify_avoid: event.target.checked })} />
    </label>

    <label className="profile-line">
      <span>Avisar también en precaución</span>
      <input type="checkbox" checked={preferences.notify_caution} disabled={!preferences.enabled} onChange={(event) => void save({ ...preferences, notify_caution: event.target.checked })} />
    </label>

    <label className="profile-line">
      <span>Antelación</span>
      <select value={preferences.lead_hours} disabled={!preferences.enabled} onChange={(event) => void save({ ...preferences, lead_hours: Number(event.target.value) })}>
        <option value={6}>6 horas</option>
        <option value={12}>12 horas</option>
        <option value={24}>24 horas</option>
        <option value={48}>48 horas</option>
        <option value={72}>72 horas</option>
      </select>
    </label>

    <small>Por defecto solo se propone avisar en “Evitar”. El radar observado se usa como evidencia adicional y nunca como una ETA de lluvia.</small>
    {message ? <p className="subtle" aria-live="polite">{message}</p> : null}
  </section>;
}
