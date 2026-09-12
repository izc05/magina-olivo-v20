'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadCommercialNotificationPreferences, saveCommercialNotificationPreferences, type CommercialNotificationPreferences } from '@/lib/commercial-notification-source';

const defaults: CommercialNotificationPreferences = {
  enabled: false,
  notify_overdue_invoices: false,
  overdue_invoice_days: 7,
  notify_expired_quotes: false,
  notify_quote_followup: false,
  quote_followup_days: 7,
  notify_unbilled_work: false,
  unbilled_work_days: 14,
};

export function CommercialNotificationSettings() {
  const { status, selectedWorkspaceId } = useAuth();
  const [value, setValue] = useState(defaults);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (status !== 'authenticated' || !selectedWorkspaceId) return;
    let cancelled = false;
    loadCommercialNotificationPreferences(selectedWorkspaceId)
      .then((next) => { if (!cancelled) setValue(next); })
      .catch(() => undefined);
    return () => { cancelled = true; };
  }, [selectedWorkspaceId, status]);

  if (status !== 'authenticated' || !selectedWorkspaceId) return null;

  async function save() {
    if (!selectedWorkspaceId || saving) return;
    try {
      setSaving(true);
      const next = await saveCommercialNotificationPreferences(selectedWorkspaceId, value);
      setValue(next);
      setMessage('Preferencias comerciales guardadas.');
    } catch {
      setMessage('No se han podido guardar los avisos comerciales.');
    } finally {
      setSaving(false);
    }
  }

  const toggle = (key: keyof CommercialNotificationPreferences) => setValue((current) => ({ ...current, [key]: !current[key] }));

  return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Avisos comerciales</h3><span>{value.enabled ? 'Activados' : 'Desactivados'}</span></div>
    <p>Son independientes de los avisos agrícolas y económicos. Están desactivados por defecto.</p>
    <label className="profile-line"><span>Activar avisos comerciales</span><input type="checkbox" checked={value.enabled} onChange={() => toggle('enabled')} /></label>
    <label className="profile-line"><span>Facturas vencidas</span><input type="checkbox" checked={value.notify_overdue_invoices} onChange={() => toggle('notify_overdue_invoices')} /></label>
    <label className="profile-line"><span>Avisar desde</span><select value={value.overdue_invoice_days} onChange={(e) => setValue((c) => ({ ...c, overdue_invoice_days: Number(e.target.value) }))}><option value={3}>3 días</option><option value={7}>7 días</option><option value={15}>15 días</option><option value={30}>30 días</option></select></label>
    <label className="profile-line"><span>Presupuesto fuera de plazo</span><input type="checkbox" checked={value.notify_expired_quotes} onChange={() => toggle('notify_expired_quotes')} /></label>
    <label className="profile-line"><span>Presupuesto sin respuesta</span><input type="checkbox" checked={value.notify_quote_followup} onChange={() => toggle('notify_quote_followup')} /></label>
    <label className="profile-line"><span>Seguimiento desde</span><select value={value.quote_followup_days} onChange={(e) => setValue((c) => ({ ...c, quote_followup_days: Number(e.target.value) }))}><option value={3}>3 días</option><option value={7}>7 días</option><option value={14}>14 días</option><option value={30}>30 días</option></select></label>
    <label className="profile-line"><span>Trabajos sin facturar</span><input type="checkbox" checked={value.notify_unbilled_work} onChange={() => toggle('notify_unbilled_work')} /></label>
    <label className="profile-line"><span>Avisar desde</span><select value={value.unbilled_work_days} onChange={(e) => setValue((c) => ({ ...c, unbilled_work_days: Number(e.target.value) }))}><option value={7}>7 días</option><option value={14}>14 días</option><option value={30}>30 días</option><option value={60}>60 días</option></select></label>
    <div className="record-actions"><button type="button" className="primary" disabled={saving} onClick={() => void save()}>{saving ? 'Guardando…' : 'Guardar avisos'}</button></div>
    {message ? <small>{message}</small> : null}
  </section>;
}
