'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { loadPublicCommercialShare, publicCommercialPdfUrl, submitPublicQuoteDecision, type PublicCommercialShare } from '@/lib/public-commercial-share-source';

function money(value: number) {
  return value.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
}

function dateLabel(value?: string | null) {
  if (!value) return '—';
  const [year, month, day] = value.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

function snapshotText(snapshot: Record<string, unknown> | null, key: string) {
  const value = snapshot?.[key];
  return typeof value === 'string' ? value : '';
}

export function PublicCommercialShareClient() {
  const params = useSearchParams();
  const token = params.get('token');
  const [share, setShare] = useState<PublicCommercialShare | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [customerName, setCustomerName] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function reload() {
    if (!token) {
      setLoading(false);
      setError('El enlace no es válido.');
      return;
    }
    setLoading(true);
    try {
      const value = await loadPublicCommercialShare(token);
      setShare(value);
      const name = snapshotText(value.customer, 'legal_name') || snapshotText(value.customer, 'display_name');
      setCustomerName(name);
      setError(null);
    } catch (cause) {
      console.error('Unable to load public commercial share', cause);
      setShare(null);
      setError('Este enlace no está disponible, ha caducado o ha sido revocado.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void reload(); }, [token]);

  async function decide(decision: 'accepted' | 'rejected') {
    if (!token || !share || working) return;
    setWorking(true);
    setError(null);
    try {
      await submitPublicQuoteDecision(token, { decision, customerName: customerName.trim() || undefined, note: note.trim() || undefined });
      await reload();
    } catch (cause) {
      console.error('Unable to submit public quote decision', cause);
      setError('No se ha podido registrar la decisión. Puede que el envío aún no esté confirmado o el enlace ya no sea válido.');
    } finally {
      setWorking(false);
    }
  }

  if (loading) return <main className="public-doc-shell"><section className="public-doc-card"><p>Preparando documento…</p></section></main>;
  if (!share || !token) return <main className="public-doc-shell"><section className="public-doc-card"><span className="eyebrow">MÁGINA OLIVO</span><h1>Enlace no disponible</h1><p>{error ?? 'No hemos podido abrir este documento.'}</p></section></main>;

  const isQuote = share.entity_type === 'professional_quote';
  const issuerName = snapshotText(share.issuer, 'legal_name') || snapshotText(share.issuer, 'workspace_name') || 'Profesional agrícola';
  const customer = snapshotText(share.customer, 'legal_name') || snapshotText(share.customer, 'display_name') || 'Cliente';
  const alreadyDecided = share.decision === 'accepted' || share.decision === 'rejected';

  return <main className="public-doc-shell">
    <section className="public-doc-card public-doc-hero">
      <span className="eyebrow">MÁGINA OLIVO · DOCUMENTO COMPARTIDO</span>
      <h1>{isQuote ? 'Presupuesto' : 'Factura'} {share.number || ''}</h1>
      <p>{issuerName} → {customer}</p>
      <div className="public-doc-total"><small>Total</small><strong>{money(share.total_eur)}</strong></div>
      <div className="public-doc-meta">
        <span>Fecha · {dateLabel(share.issued_on)}</span>
        {isQuote ? <span>Válido hasta · {dateLabel(share.valid_until)}</span> : null}
      </div>
      <a className="primary public-doc-action" href={publicCommercialPdfUrl(token)} target="_blank" rel="noreferrer">Ver PDF recibido</a>
    </section>

    {share.title ? <section className="public-doc-card"><h2>{share.title}</h2></section> : null}

    {isQuote ? <section className="public-doc-card">
      <div className="section-head"><div><h2>Tu decisión</h2><small>La decisión queda ligada a esta versión concreta del presupuesto.</small></div></div>
      {alreadyDecided ? <div className="public-decision-result"><strong>{share.decision === 'accepted' ? 'Presupuesto aceptado ✓' : 'Presupuesto rechazado'}</strong><p>Registrado {share.decided_at ? new Date(share.decided_at).toLocaleString('es-ES') : ''}.</p></div> : share.can_decide ? <>
        <label className="record-field"><span>Nombre / razón social</span><input className="record-control" value={customerName} onChange={(event) => setCustomerName(event.target.value)} maxLength={200} /></label>
        <label className="record-field"><span>Comentario opcional</span><textarea className="record-control" value={note} onChange={(event) => setNote(event.target.value)} maxLength={2000} rows={4} /></label>
        <div className="record-actions public-decision-actions"><button className="primary" type="button" disabled={working} onClick={() => void decide('accepted')}>{working ? 'Registrando…' : 'Aceptar presupuesto'}</button><button className="secondary-action" type="button" disabled={working} onClick={() => void decide('rejected')}>Rechazar</button></div>
        <p className="form-help">Aceptar no genera un cobro ni un trabajo automáticamente. El profesional deberá convertir después el presupuesto aceptado en trabajo.</p>
      </> : <div className="public-decision-result"><strong>Decisión todavía no disponible</strong><p>El profesional aún no ha confirmado el envío de esta versión. Puedes consultar el PDF, pero no decidir todavía.</p></div>}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
    </section> : null}

    <footer className="public-doc-footer">Este enlace puede caducar o ser revocado. La apertura del enlace no implica aceptación ni recepción certificada.</footer>

    <style jsx global>{`
      .public-doc-shell{min-height:100vh;background:#f1f1ec;padding:18px;font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#263126}.public-doc-card{max-width:720px;margin:0 auto 14px;background:#fff;border:1px solid #dfe4dc;border-radius:18px;padding:22px;box-shadow:0 12px 34px rgba(30,45,30,.07)}.public-doc-hero{padding-top:28px}.eyebrow{font-size:10px;letter-spacing:.14em;font-weight:800;color:#687768}.public-doc-card h1{font-size:30px;margin:8px 0}.public-doc-card h2{margin:0 0 8px}.public-doc-card p{color:#5e6a5e;line-height:1.55}.public-doc-total{display:flex;align-items:end;justify-content:space-between;margin:26px 0 12px;padding:16px 0;border-top:1px solid #dfe4dc;border-bottom:1px solid #dfe4dc}.public-doc-total small{color:#6b766b}.public-doc-total strong{font-size:26px}.public-doc-meta{display:flex;gap:16px;flex-wrap:wrap;font-size:12px;color:#697469;margin:12px 0 18px}.public-doc-action{display:inline-flex;text-decoration:none}.public-decision-actions{margin-top:16px}.public-decision-result{padding:16px;border-radius:12px;background:#f4f7f1}.public-doc-footer{max-width:720px;margin:22px auto;color:#788178;font-size:11px;line-height:1.5;text-align:center}@media(max-width:640px){.public-doc-shell{padding:10px}.public-doc-card{padding:17px;border-radius:14px}.public-doc-card h1{font-size:24px}.public-doc-total strong{font-size:22px}.public-decision-actions{display:grid;grid-template-columns:1fr}.public-decision-actions button{width:100%}}
    `}</style>
  </main>;
}
