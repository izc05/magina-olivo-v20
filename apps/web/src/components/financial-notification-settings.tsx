'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import {
  loadFinancialNotificationPreferences,
  saveFinancialNotificationPreferences,
  type FinancialNotificationPreferences,
} from '@/lib/financial-notification-preferences';

const defaults: FinancialNotificationPreferences = {
  enabled: false,
  notifySettlements: false,
  settlementMinEur: 1000,
  notifyDocumentReview: false,
  notifyOcrFailure: false,
};

export function FinancialNotificationSettings() {
  const { status, selectedWorkspaceId } = useAuth();
  const [preferences, setPreferences] = useState(defaults);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (status !== 'authenticated' || !selectedWorkspaceId) return;
    let cancelled = false;
    setLoading(true);
    loadFinancialNotificationPreferences(selectedWorkspaceId)
      .then((next) => { if (!cancelled) setPreferences(next); })
      .catch((cause) => { console.warn('Unable to load financial notification preferences', cause); if (!cancelled) setError('No se han podido cargar estos avisos.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [selectedWorkspaceId, status]);

  async function save(next: FinancialNotificationPreferences) {
    if (!selectedWorkspaceId) return;
    setPreferences(next);
    setSaving(true);
    setError(null);
    try {
      setPreferences(await saveFinancialNotificationPreferences(selectedWorkspaceId, next));
    } catch (cause) {
      console.error('Unable to save financial notification preferences', cause);
      setError('No se han podido guardar estos avisos.');
    } finally {
      setSaving(false);
    }
  }

  if (status !== 'authenticated') return null;

  return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Avisos económicos y documentos</h3><span>{loading ? 'Cargando…' : saving ? 'Guardando…' : preferences.enabled ? 'Activados' : 'Desactivados'}</span></div>
    <p>Estos avisos son independientes de “Prioridad ahora” y están desactivados por defecto.</p>

    <label className="profile-line"><span>Activar estos avisos</span><input type="checkbox" checked={preferences.enabled} onChange={(event) => void save({ ...preferences, enabled: event.target.checked })} disabled={loading || saving} /></label>

    <label className="profile-line"><span>Cobros pendientes</span><input type="checkbox" checked={preferences.notifySettlements} onChange={(event) => void save({ ...preferences, notifySettlements: event.target.checked })} disabled={!preferences.enabled || loading || saving} /></label>

    <label className="form-field"><span>Avisar desde</span><select value={String(preferences.settlementMinEur)} onChange={(event) => void save({ ...preferences, settlementMinEur: Number(event.target.value) })} disabled={!preferences.enabled || !preferences.notifySettlements || loading || saving}><option value="500">500 €</option><option value="1000">1.000 €</option><option value="2500">2.500 €</option><option value="5000">5.000 €</option></select></label>

    <label className="profile-line"><span>Documento pendiente de revisión</span><input type="checkbox" checked={preferences.notifyDocumentReview} onChange={(event) => void save({ ...preferences, notifyDocumentReview: event.target.checked })} disabled={!preferences.enabled || loading || saving} /></label>

    <label className="profile-line"><span>OCR fallido</span><input type="checkbox" checked={preferences.notifyOcrFailure} onChange={(event) => void save({ ...preferences, notifyOcrFailure: event.target.checked })} disabled={!preferences.enabled || loading || saving} /></label>

    <small>Una misma liquidación solo genera un aviso por regla. Los documentos pueden volver a avisar si cambian de estado, por ejemplo de lectura fallida a revisión pendiente.</small>
    {error ? <p className="form-error" role="alert">{error}</p> : null}
  </section>;
}
