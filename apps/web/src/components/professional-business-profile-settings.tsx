'use client';

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadProfessionalBusinessProfile, saveProfessionalBusinessProfile, type ProfessionalBusinessProfile } from '@/lib/professional-business-profile-source';

export function ProfessionalBusinessProfileSettings() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [profile, setProfile] = useState<ProfessionalBusinessProfile | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId) {
      setProfile(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    loadProfessionalBusinessProfile(selectedWorkspaceId)
      .then((value) => { if (!cancelled) setProfile(value); })
      .catch((cause) => {
        console.error('Unable to load professional business profile', cause);
        if (!cancelled) setError('No se han podido cargar los datos de facturación.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || saving) return;
    const form = new FormData(event.currentTarget);
    const optional = (name: string) => {
      const value = String(form.get(name) ?? '').trim();
      return value || null;
    };
    try {
      setSaving(true);
      setSaved(false);
      setError(null);
      const next = await saveProfessionalBusinessProfile(selectedWorkspaceId, {
        legal_name: optional('legal_name'),
        tax_id: optional('tax_id'),
        address: optional('address'),
        postal_code: optional('postal_code'),
        municipality: optional('municipality'),
        province: optional('province'),
        email: optional('email'),
        phone: optional('phone'),
        payment_terms: optional('payment_terms'),
        footer_note: optional('footer_note'),
      });
      setProfile(next);
      setSaved(true);
    } catch (cause) {
      console.error('Unable to save professional business profile', cause);
      setError('No se han podido guardar los datos de facturación.');
    } finally {
      setSaving(false);
    }
  }

  if (!apiConfigured || status !== 'authenticated') return null;
  if (loading && !profile) return <section className="card profile-card premium-profile-card"><div className="profile-card-head"><h3>Datos de facturación</h3></div><p>Cargando…</p></section>;

  return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Datos de facturación</h3><span>{saved ? 'Guardado ✓' : 'Profesional'}</span></div>
    <p>Estos datos se usan como emisor en presupuestos y facturas imprimibles.</p>
    <form className="quick-record-form" onSubmit={submit}>
      <div className="record-fields">
        <label className="record-field wide"><span>Nombre / razón social</span><input className="record-control" name="legal_name" defaultValue={profile?.legal_name ?? ''} placeholder={profile?.workspace_name ?? ''} /></label>
        <label className="record-field"><span>NIF / CIF</span><input className="record-control" name="tax_id" defaultValue={profile?.tax_id ?? ''} /></label>
        <label className="record-field"><span>Teléfono</span><input className="record-control" name="phone" defaultValue={profile?.phone ?? ''} /></label>
        <label className="record-field wide"><span>Dirección</span><input className="record-control" name="address" defaultValue={profile?.address ?? ''} /></label>
        <label className="record-field"><span>Código postal</span><input className="record-control" name="postal_code" defaultValue={profile?.postal_code ?? ''} /></label>
        <label className="record-field"><span>Municipio</span><input className="record-control" name="municipality" defaultValue={profile?.municipality ?? ''} /></label>
        <label className="record-field"><span>Provincia</span><input className="record-control" name="province" defaultValue={profile?.province ?? ''} /></label>
        <label className="record-field wide"><span>Email</span><input className="record-control" type="email" name="email" defaultValue={profile?.email ?? ''} /></label>
        <label className="record-field wide"><span>Condiciones / forma de pago</span><textarea className="record-control" name="payment_terms" defaultValue={profile?.payment_terms ?? ''} rows={3} /></label>
        <label className="record-field wide"><span>Pie del documento</span><textarea className="record-control" name="footer_note" defaultValue={profile?.footer_note ?? ''} rows={2} /></label>
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <div className="record-save-bar"><small>No modifica facturas ni presupuestos ya registrados.</small><button className="primary" type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Guardar datos →'}</button></div>
    </form>
  </section>;
}
