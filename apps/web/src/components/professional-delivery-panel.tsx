'use client';

import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { uploadDomainAttachment } from '@/lib/document-upload-source';
import { confirmCommercialDelivery, loadCommercialDeliveries, prepareCommercialDelivery, type CommercialDelivery, type CommercialDeliveryChannel } from '@/lib/professional-delivery-source';
import { generateProfessionalPdf } from '@/lib/professional-pdf-generator';
import { loadProfessionalPrintData, type ProfessionalPrintPayload } from '@/lib/professional-print-source';
import { createProfessionalShareLink, loadProfessionalShareLinks, revokeProfessionalShareLink, type ProfessionalShareLink } from '@/lib/professional-share-link-source';

function safeFilename(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^A-Za-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 120) || 'documento';
}

function dateTime(value: string | null | undefined) {
  if (!value) return '—';
  return new Date(value).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' });
}

export function ProfessionalDeliveryPanel() {
  const params = useSearchParams();
  const rawType = params.get('type');
  const type = rawType === 'invoice' || rawType === 'quote' ? rawType : null;
  const id = params.get('id');
  const { selectedWorkspaceId } = useAuth();
  const [data, setData] = useState<ProfessionalPrintPayload | null>(null);
  const [documentId, setDocumentId] = useState<string | undefined>();
  const [deliveries, setDeliveries] = useState<CommercialDelivery[]>([]);
  const [shareLinks, setShareLinks] = useState<ProfessionalShareLink[]>([]);
  const [channel, setChannel] = useState<CommercialDeliveryChannel>('share');
  const [recipient, setRecipient] = useState('');
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const isInvoice = data?.document_type === 'invoice';
  const entityType = isInvoice ? 'professional_invoice' : 'professional_quote';
  const number = data?.document.number || (isInvoice ? 'Borrador' : 'Sin número');

  async function reloadTracking(entityId: string) {
    if (!selectedWorkspaceId) return;
    try {
      const [nextDeliveries, nextLinks] = await Promise.all([
        loadCommercialDeliveries(selectedWorkspaceId, entityType, entityId),
        loadProfessionalShareLinks(selectedWorkspaceId, entityType, entityId),
      ]);
      setDeliveries(nextDeliveries);
      setShareLinks(nextLinks);
    } catch (cause) {
      console.warn('Unable to load commercial tracking', cause);
    }
  }

  useEffect(() => {
    if (!selectedWorkspaceId || !type || !id) return;
    let cancelled = false;
    loadProfessionalPrintData(selectedWorkspaceId, type, id)
      .then((payload) => {
        if (cancelled) return;
        setData(payload);
        setRecipient(payload.customer.email || payload.customer.phone || '');
      })
      .catch((cause) => {
        console.error('Unable to load commercial delivery document', cause);
        if (!cancelled) setError('No se ha podido preparar la zona de envío.');
      });
    return () => { cancelled = true; };
  }, [id, selectedWorkspaceId, type]);

  useEffect(() => {
    if (!data) return;
    void reloadTracking(data.document.id);
  }, [data?.document.id, selectedWorkspaceId, entityType]);

  const latestPrepared = useMemo(() => deliveries.find((item) => item.status === 'prepared') ?? null, [deliveries]);

  async function ensurePdf() {
    if (!selectedWorkspaceId || !data) throw new Error('workspace_or_document_required');
    if (documentId) return documentId;
    const blob = generateProfessionalPdf(data);
    const filename = `${safeFilename(isInvoice ? `factura-${number}` : `presupuesto-${number}`)}.pdf`;
    const file = new File([blob], filename, { type: 'application/pdf', lastModified: Date.now() });
    const uploaded = await uploadDomainAttachment({
      workspaceId: selectedWorkspaceId,
      domainType: entityType,
      domainRecordId: data.document.id,
      file,
      kind: isInvoice ? 'sales_invoice' : 'sales_quote',
      title: `${isInvoice ? 'Factura emitida' : 'Presupuesto emitido'} ${number}`,
      relation: 'generated_pdf',
    });
    setDocumentId(uploaded.documentId);
    return uploaded.documentId;
  }

  async function prepareAndShare() {
    if (!selectedWorkspaceId || !data || working) return;
    setWorking(true); setError(null); setMessage(null);
    try {
      const docId = await ensurePdf();
      const publicShare = await createProfessionalShareLink({
        workspaceId: selectedWorkspaceId,
        entityType,
        entityId: data.document.id,
        documentId: docId,
        expiresInDays: 7,
      });
      await prepareCommercialDelivery({
        workspaceId: selectedWorkspaceId,
        entityType,
        entityId: data.document.id,
        documentId: docId,
        channel,
        recipient: recipient || undefined,
        note: `Enlace Mágina válido hasta ${publicShare.share.expires_at}`,
      });
      const shareTitle = `${isInvoice ? 'Factura' : 'Presupuesto'} ${number}`;
      const shareText = `${shareTitle} · ${data.customer.legal_name || data.customer.display_name}`;

      if (channel === 'whatsapp') {
        window.open(`https://wa.me/?text=${encodeURIComponent(`${shareText}\n${publicShare.url}`)}`, '_blank', 'noopener,noreferrer');
      } else if (channel === 'email') {
        const subject = encodeURIComponent(shareTitle);
        const body = encodeURIComponent(`${shareText}\n\nEnlace Mágina válido durante 7 días:\n${publicShare.url}`);
        window.location.href = `mailto:${encodeURIComponent(recipient || '')}?subject=${subject}&body=${body}`;
      } else if (channel === 'link') {
        await navigator.clipboard.writeText(publicShare.url);
        setMessage('Enlace Mágina copiado. Puedes revocarlo desde esta misma pantalla.');
      } else if (navigator.share) {
        await navigator.share({ title: shareTitle, text: shareText, url: publicShare.url });
      } else {
        await navigator.clipboard.writeText(publicShare.url);
        setMessage('Tu navegador no ofrece compartir. Se ha copiado el enlace Mágina.');
      }

      setMessage((current) => current ?? 'Compartición preparada. Confirma solo cuando sepas que el documento se envió.');
      await reloadTracking(data.document.id);
    } catch (cause) {
      console.error('Unable to prepare commercial delivery', cause);
      setError('No se ha podido preparar el envío. El presupuesto/factura y su PDF no se han alterado.');
    } finally {
      setWorking(false);
    }
  }

  async function confirm(deliveryId: string, sent: boolean) {
    if (!selectedWorkspaceId || !data || working) return;
    setWorking(true); setError(null);
    try {
      await confirmCommercialDelivery(selectedWorkspaceId, deliveryId, sent);
      setMessage(sent ? 'Envío confirmado y registrado.' : 'Compartición cancelada.');
      await reloadTracking(data.document.id);
    } catch (cause) {
      console.error('Unable to confirm commercial delivery', cause);
      setError('No se ha podido actualizar el estado del envío.');
    } finally {
      setWorking(false);
    }
  }

  async function revoke(shareId: string) {
    if (!selectedWorkspaceId || !data || working) return;
    setWorking(true); setError(null);
    try {
      await revokeProfessionalShareLink(selectedWorkspaceId, shareId);
      setMessage('Enlace revocado. Ya no dará acceso al PDF.');
      await reloadTracking(data.document.id);
    } catch (cause) {
      console.error('Unable to revoke share link', cause);
      setError('No se ha podido revocar el enlace.');
    } finally {
      setWorking(false);
    }
  }

  if (!data || !type || !id) return null;

  return <section className="card no-print" style={{ maxWidth: '210mm', margin: '0 auto 18px' }}>
    <div className="section-head"><div><h2>Compartir y registrar</h2><small>El enlace dura 7 días y puede revocarse. Preparar una app no demuestra recepción.</small></div></div>
    <div className="record-fields">
      <label className="record-field"><span>Canal</span><select className="record-control" value={channel} onChange={(event) => setChannel(event.target.value as CommercialDeliveryChannel)}><option value="share">Compartir…</option><option value="whatsapp">WhatsApp</option><option value="email">Email</option><option value="link">Copiar enlace Mágina</option><option value="other">Otro</option></select></label>
      <label className="record-field wide"><span>Destinatario / referencia</span><input className="record-control" value={recipient} onChange={(event) => setRecipient(event.target.value)} placeholder="email, teléfono o nombre" /></label>
    </div>
    <div className="record-actions"><button className="primary" type="button" onClick={() => void prepareAndShare()} disabled={working}>{working ? 'Preparando…' : 'Preparar y compartir PDF'}</button></div>
    {latestPrepared ? <div className="record-save-bar"><small>Preparado {dateTime(latestPrepared.prepared_at)} · {latestPrepared.channel}{latestPrepared.recipient ? ` · ${latestPrepared.recipient}` : ''}</small><div className="record-actions"><button className="primary" type="button" onClick={() => void confirm(latestPrepared.id, true)} disabled={working}>Confirmar enviado</button><button className="secondary-action" type="button" onClick={() => void confirm(latestPrepared.id, false)} disabled={working}>Cancelar</button></div></div> : null}
    {message ? <p className="form-help">{message}</p> : null}{error ? <p className="form-error" role="alert">{error}</p> : null}
    {shareLinks.length ? <div className="activity-list">{shareLinks.slice(0, 5).map((link) => <article className="activity-item" key={link.id}><div><strong>{link.revoked_at ? 'Enlace revocado' : new Date(link.expires_at).getTime() <= Date.now() ? 'Enlace caducado' : 'Enlace activo'}</strong><small>Caduca {dateTime(link.expires_at)} · {link.access_count} acceso{link.access_count === 1 ? '' : 's'}</small></div>{!link.revoked_at && new Date(link.expires_at).getTime() > Date.now() ? <button className="secondary-action" type="button" onClick={() => void revoke(link.id)} disabled={working}>Revocar</button> : null}</article>)}</div> : null}
    {deliveries.length ? <div className="activity-list">{deliveries.slice(0, 5).map((item) => <article className="activity-item" key={item.id}><div><strong>{item.status === 'confirmed_sent' ? 'Enviado confirmado' : item.status === 'prepared' ? 'Preparado' : 'Cancelado'}</strong><small>{dateTime(item.confirmed_sent_at || item.prepared_at)} · {item.channel}{item.recipient ? ` · ${item.recipient}` : ''}</small></div>{item.document_id ? <small>PDF trazado</small> : <small>Sin PDF vinculado</small>}</article>)}</div> : null}
  </section>;
}
