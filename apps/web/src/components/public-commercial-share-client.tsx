'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import {
  loadPublicCommercialShare,
  PublicShareAccessError,
  publicCommercialPdfUrl,
  submitPublicQuoteDecision,
  type PublicCommercialShare,
  type PublicShareAccessKind,
} from '@/lib/public-commercial-share-source';
import styles from './public-commercial-share.module.css';

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

const accessCopy: Record<PublicShareAccessKind, { mark: string; title: string; text: string }> = {
  missing: {
    mark: '?',
    title: 'Documento no encontrado',
    text: 'El enlace es incorrecto o el documento compartido ya no existe. Comprueba que has abierto el enlace completo.',
  },
  expired: {
    mark: '⌛',
    title: 'Enlace caducado',
    text: 'El periodo de acceso ha terminado. Pide al remitente un nuevo enlace si todavía necesitas consultar el documento.',
  },
  revoked: {
    mark: '×',
    title: 'Enlace retirado',
    text: 'El remitente ha desactivado este enlace. El documento ya no puede consultarse desde esta dirección.',
  },
  unavailable: {
    mark: '!',
    title: 'No podemos abrirlo ahora',
    text: 'El servicio no está disponible en este momento. Tu enlace no se ha marcado como aceptado ni rechazado.',
  },
};

function AccessState({ kind, incomplete = false }: { kind: PublicShareAccessKind; incomplete?: boolean }) {
  const copy = incomplete ? {
    mark: '?',
    title: 'Enlace incompleto',
    text: 'Falta el identificador seguro del documento. Vuelve a abrir el enlace original que te envió el profesional.',
  } : accessCopy[kind];
  return <main className={styles.shell}>
    <section className={`${styles.card} ${styles.accessState}`}>
      <span className={styles.eyebrow}>MÁGINA OLIVO · DOCUMENTO COMPARTIDO</span>
      <span className={styles.accessMark} aria-hidden="true">{copy.mark}</span>
      <div><h1>{copy.title}</h1><p>{copy.text}</p></div>
      <div className={styles.accessActions}><Link className="secondary-action action-link" href="/explorar">Explorar Mágina</Link></div>
    </section>
  </main>;
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
  const [accessKind, setAccessKind] = useState<PublicShareAccessKind | null>(null);

  async function reload() {
    if (!token) {
      setLoading(false);
      setShare(null);
      setAccessKind('missing');
      return;
    }
    setLoading(true);
    try {
      const value = await loadPublicCommercialShare(token);
      setShare(value);
      const name = snapshotText(value.customer, 'legal_name') || snapshotText(value.customer, 'display_name');
      setCustomerName(name);
      setAccessKind(null);
      setError(null);
    } catch (cause) {
      console.error('Unable to load public commercial share', cause);
      setShare(null);
      setAccessKind(cause instanceof PublicShareAccessError ? cause.kind : 'unavailable');
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
      if (cause instanceof PublicShareAccessError && cause.kind !== 'unavailable') {
        setShare(null);
        setAccessKind(cause.kind);
      } else {
        setError('No se ha podido registrar la decisión. No se ha guardado ninguna aceptación ni rechazo.');
      }
    } finally {
      setWorking(false);
    }
  }

  if (loading) return <main className={styles.shell}><section className={styles.card} aria-live="polite"><p>Preparando documento…</p></section></main>;
  if (!token) return <AccessState kind="missing" incomplete />;
  if (!share) return <AccessState kind={accessKind ?? 'unavailable'} />;

  const isQuote = share.entity_type === 'professional_quote';
  const issuerName = snapshotText(share.issuer, 'legal_name') || snapshotText(share.issuer, 'workspace_name') || 'Profesional agrícola';
  const customer = snapshotText(share.customer, 'legal_name') || snapshotText(share.customer, 'display_name') || 'Cliente';
  const alreadyDecided = share.decision === 'accepted' || share.decision === 'rejected';
  const quoteExpired = Boolean(isQuote && share.valid_until && new Date(`${share.valid_until.slice(0, 10)}T23:59:59`).getTime() < Date.now());
  const canDecide = share.can_decide && !quoteExpired;

  return <main className={styles.shell}>
    <section className={`${styles.card} ${styles.hero}`}>
      <span className={styles.eyebrow}>MÁGINA OLIVO · DOCUMENTO COMPARTIDO</span>
      <h1>{isQuote ? 'Presupuesto' : 'Factura'} {share.number || ''}</h1>
      <p>{issuerName} → {customer}</p>
      <span className={styles.status}>{alreadyDecided ? share.decision === 'accepted' ? 'Aceptado' : 'Rechazado' : quoteExpired ? 'Fuera de plazo' : 'Enlace válido'}</span>
      <div className={styles.total}><small>Total</small><strong>{money(share.total_eur)}</strong></div>
      <div className={styles.meta}>
        <span>Fecha · {dateLabel(share.issued_on)}</span>
        {isQuote ? <span>Válido hasta · {dateLabel(share.valid_until)}</span> : null}
        <span>Enlace disponible hasta · {dateLabel(share.expires_at)}</span>
      </div>
      <a className={`primary ${styles.action}`} href={publicCommercialPdfUrl(token)} target="_blank" rel="noreferrer">Ver PDF recibido</a>
    </section>

    {share.title ? <section className={styles.card}><h2>{share.title}</h2></section> : null}

    {isQuote ? <section className={styles.card}>
      <div className="section-head"><div><h2>Tu decisión</h2><small>La decisión queda ligada a esta versión concreta del presupuesto.</small></div></div>
      {alreadyDecided ? <div className={styles.decisionResult}><strong>{share.decision === 'accepted' ? 'Presupuesto aceptado ✓' : 'Presupuesto rechazado'}</strong><p>Registrado {share.decided_at ? new Date(share.decided_at).toLocaleString('es-ES') : ''}.</p></div> : quoteExpired ? <div className={styles.decisionResult}><strong>Presupuesto fuera de plazo</strong><p>Puedes consultar el PDF recibido, pero esta propuesta ya ha superado su fecha de validez. Contacta con el profesional para solicitar una nueva versión.</p></div> : canDecide ? <>
        <label className="record-field"><span>Nombre / razón social</span><input className="record-control" value={customerName} onChange={(event) => setCustomerName(event.target.value)} maxLength={200} /></label>
        <label className="record-field"><span>Comentario opcional</span><textarea className="record-control" value={note} onChange={(event) => setNote(event.target.value)} maxLength={2000} rows={4} /></label>
        <div className={`record-actions ${styles.decisionActions}`}><button className="primary" type="button" disabled={working} onClick={() => void decide('accepted')}>{working ? 'Registrando…' : 'Aceptar presupuesto'}</button><button className="secondary-action" type="button" disabled={working} onClick={() => void decide('rejected')}>Rechazar</button></div>
        <p className="form-help">Aceptar no genera un cobro ni un trabajo automáticamente. El profesional deberá convertir después el presupuesto aceptado en trabajo.</p>
      </> : <div className={styles.decisionResult}><strong>Decisión todavía no disponible</strong><p>El profesional aún no ha confirmado el envío de esta versión. Puedes consultar el PDF, pero no decidir todavía.</p></div>}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
    </section> : null}

    <footer className={styles.footer}>Este enlace es temporal y puede ser revocado por el remitente. La apertura del enlace no implica aceptación ni recepción certificada.</footer>
  </main>;
}
