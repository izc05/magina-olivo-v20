'use client';

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useAuth } from '@/components/auth-provider';
import { loadApiCampaigns, type CampaignListItem } from '@/lib/campaign-data-source';
import { assignDocumentCampaign } from '@/lib/document-data-source';
import { uploadDomainAttachment, type DocumentKind } from '@/lib/document-upload-source';
import { useFieldContext } from '@/lib/use-field-context';

const kinds: Array<{ value: DocumentKind; label: string }> = [
  { value: 'invoice', label: 'Factura' },
  { value: 'purchase_receipt', label: 'Ticket / justificante de compra' },
  { value: 'quote', label: 'Presupuesto' },
  { value: 'delivery_ticket', label: 'Albarán de entrega' },
  { value: 'yield_result', label: 'Resultado / rendimiento' },
  { value: 'settlement_statement', label: 'Liquidación de cooperativa / almazara' },
  { value: 'collection_receipt', label: 'Justificante de cobro' },
  { value: 'treatment', label: 'Documento de tratamiento' },
  { value: 'fertilization', label: 'Documento de abonado' },
  { value: 'irrigation', label: 'Documento de riego' },
  { value: 'pruning', label: 'Documento de poda' },
  { value: 'observation', label: 'Incidencia / observación' },
  { value: 'work_report', label: 'Parte de trabajo' },
  { value: 'land_reference', label: 'Catastro / SIGPAC / terreno' },
  { value: 'photo', label: 'Fotografía' },
  { value: 'other', label: 'Otro' },
];

export function FarmDocumentUploadClient() {
  const { context, ready, found } = useFieldContext();
  const { selectedWorkspaceId } = useAuth();
  const [kind, setKind] = useState<DocumentKind>('invoice');
  const [title, setTitle] = useState('');
  const [campaignId, setCampaignId] = useState('');
  const [campaigns, setCampaigns] = useState<CampaignListItem[]>([]);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [contextWarning, setContextWarning] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedWorkspaceId || context.source !== 'api') { setCampaigns([]); return; }
    let cancelled = false;
    loadApiCampaigns(selectedWorkspaceId).then((items) => {
      if (cancelled) return;
      setCampaigns(items);
      const active = items.find((item) => item.status === 'active');
      if (active) setCampaignId((current) => current || active.id);
    }).catch((cause) => console.warn('Unable to load campaigns for document', cause));
    return () => { cancelled = true; };
  }, [context.source, selectedWorkspaceId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null); setContextWarning(null);
    if (!selectedWorkspaceId || context.source !== 'api') { setError('La subida documental real necesita una finca del servidor.'); return; }
    const fd = new FormData(event.currentTarget);
    const file = fd.get('file');
    if (!(file instanceof File) || !file.size) { setError('Selecciona un archivo.'); return; }
    try {
      setSaving(true);
      const uploaded = await uploadDomainAttachment({ workspaceId: selectedWorkspaceId, fieldId: context.id, file, kind, title: title.trim() || file.name, relation: 'attachment' });
      if (campaignId) {
        try { await assignDocumentCampaign(selectedWorkspaceId, uploaded.documentId, campaignId); }
        catch (campaignError) { console.warn('Document stored but campaign assignment failed', campaignError); setContextWarning('El documento está guardado y verificado, pero quedó sin campaña asignada. Podrás clasificarlo después.'); }
      }
      setSaved(true);
    } catch (cause) {
      console.error('Unable to upload farm document', cause);
      setError('No se ha podido subir y verificar el documento. El archivo no se dará por válido hasta completar la verificación.');
    } finally { setSaving(false); }
  }

  if (!ready) return <section className="card"><p>Cargando finca…</p></section>;
  if (!found) return <section className="card"><h1>Finca no encontrada</h1><Link href="/mi-campo">Volver a Mi Campo</Link></section>;
  if (saved) return <section className="card record-success"><div className="success-mark">✓</div><h1>Documento guardado</h1><p>El archivo ha quedado vinculado a {context.name} y su subida ha pasado por la comprobación de integridad.</p>{contextWarning ? <p className="form-error" role="status">{contextWarning}</p> : null}<div className="record-actions"><button className="secondary-action" type="button" onClick={() => { setSaved(false); setContextWarning(null); }}>Añadir otro</button><Link className="primary action-link" href={context.returnHref}>Volver a la finca</Link></div></section>;

  return <>
    <header className="page-title mi-campo-title"><div><span className="eyebrow dark">MI CAMPO · DOCUMENTOS</span><h1>Añadir documento</h1><p>{context.name} · guarda el archivo en la finca o adjúntalo desde un registro concreto cuando corresponda.</p></div></header>
    <form className="section card" onSubmit={submit}>
      <label className="form-field"><span>Tipo</span><select value={kind} onChange={(event) => setKind(event.target.value as DocumentKind)}>{kinds.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
      <label className="form-field"><span>Campaña</span><select value={campaignId} onChange={(event) => setCampaignId(event.target.value)}><option value="">Sin asignar</option>{campaigns.map((campaign) => <option key={campaign.id} value={campaign.id}>{campaign.name}{campaign.status === 'active' ? ' · activa' : ''}</option>)}</select><small>No es obligatorio. Si no estás seguro, déjalo sin asignar.</small></label>
      <label className="form-field"><span>Título</span><input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Ej. Liquidación cooperativa enero" maxLength={240} /></label>
      <label className="form-field"><span>Archivo</span><input name="file" type="file" accept="image/*,.pdf" required /><small>Máximo 100 MB. El navegador calcula SHA-256 antes de reservar la subida.</small></label>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <div className="form-actions"><button className="primary" type="submit" disabled={saving}>{saving ? 'Subiendo y verificando…' : 'Guardar documento'}</button><Link className="secondary-action action-link" href={context.returnHref}>Cancelar</Link></div>
    </form>
  </>;
}
