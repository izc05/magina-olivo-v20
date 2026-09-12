'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import {
  defaultHomePriorityPreferences,
  loadHomePriorityPreferences,
  saveHomePriorityPreferences,
  type HomePriorityPreferences,
} from '@/lib/home-priority-preferences';

export function HomePrioritySettings() {
  const { status, selectedWorkspaceId } = useAuth();
  const [preferences, setPreferences] = useState<HomePriorityPreferences>(defaultHomePriorityPreferences);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (status !== 'authenticated' || !selectedWorkspaceId) return;
    let cancelled = false;
    setLoading(true);
    loadHomePriorityPreferences(selectedWorkspaceId)
      .then((next) => { if (!cancelled) setPreferences(next); })
      .catch((cause) => { console.warn('Unable to load priority preferences', cause); if (!cancelled) setError('No se han podido cargar las prioridades.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [selectedWorkspaceId, status]);

  async function save(next: HomePriorityPreferences) {
    if (!selectedWorkspaceId) return;
    setPreferences(next);
    setSaving(true);
    setSaved(false);
    setError(null);
    try {
      const stored = await saveHomePriorityPreferences(selectedWorkspaceId, next);
      setPreferences(stored);
      setSaved(true);
      window.setTimeout(() => setSaved(false), 1800);
    } catch (cause) {
      console.error('Unable to save priority preferences', cause);
      setError('No se han podido guardar las prioridades.');
    } finally {
      setSaving(false);
    }
  }

  if (status !== 'authenticated') return null;

  return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Prioridad de Inicio</h3><span>{loading ? 'Cargando…' : saving ? 'Guardando…' : saved ? 'Guardado ✓' : 'Personalizar'}</span></div>
    <p>Decide cuánto peso tienen economía y documentos en “Prioridad ahora”. Las tareas atrasadas y recomendaciones “Evitar” siempre se muestran.</p>

    <label className="form-field"><span>Economía y cobros</span><select value={preferences.economicWeight} onChange={(event) => void save({ ...preferences, economicWeight: event.target.value as HomePriorityPreferences['economicWeight'] })} disabled={loading || saving}><option value="normal">Prioridad normal</option><option value="reduced">Menos presencia en Inicio</option></select></label>

    <label className="form-field"><span>Documentos y OCR</span><select value={preferences.documentWeight} onChange={(event) => void save({ ...preferences, documentWeight: event.target.value as HomePriorityPreferences['documentWeight'] })} disabled={loading || saving}><option value="normal">Prioridad normal</option><option value="reduced">Menos presencia en Inicio</option></select></label>

    <label className="profile-line"><span>Mostrar prioridades bajas</span><input type="checkbox" checked={preferences.showLowPriority} onChange={(event) => void save({ ...preferences, showLowPriority: event.target.checked })} disabled={loading || saving} /></label>

    <small>Esto solo ordena y filtra Inicio. No desactiva agenda, documentos, cobros ni alertas agronómicas.</small>
    {error ? <p className="form-error" role="alert">{error}</p> : null}
  </section>;
}
