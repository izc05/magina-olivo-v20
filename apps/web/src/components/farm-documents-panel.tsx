'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadApiCampaigns, type CampaignListItem } from '@/lib/campaign-data-source';
import { getDocumentReadUrl, loadFieldDocuments, type FieldDocument } from '@/lib/document-data-source';
import type { DocumentKind } from '@/lib/document-upload-source';
import { useFieldContext, withFieldQuery } from '@/lib/use-field-context';

const kinds: Array<{ value: DocumentKind; label: string }> = [
  { value: 'invoice', label: 'Facturas' },
  { value: 'purchase_receipt', label: 'Tickets / compras' },
  { value: 'quote', label: 'Presupuestos' },
  { value: 'delivery_ticket', label: 'Albaranes' },
  { value: 'yield_result', label: 'Rendimientos' },
  { value: 'treatment', label: 'Tratamientos' },
  { value: 'fertilization', label: 'Abonado' },
  { value: 'irrigation', label: 'Riego' },
  { value: 'pruning', label: 'Poda' },
  { value: 'observation', label: 'Observaciones' },
  { value: 'work_report', label: 'Partes de trabajo' },
  { value: 'land_reference', label: 'Terreno / referencias' },
  { value: 'photo', label: 'Fotos' },
  { value: 'other', label: 'Otros' },
];

const ocrKinds = new Set<DocumentKind>(['invoice', 'purchase_receipt', 'quote', 'delivery_ticket', 'yield_result']);

function kindLabel(kind: string) {
  return kinds.find((item) => item.value === kind)?.label.replace(/s$/, '') ?? kind;
}

export function FarmDocumentsPanel() {
  const { context, ready, found } = useFieldContext();
  const { selectedWorkspaceId } = useAuth();
  const [documents, setDocuments] = useState<FieldDocument[]>([]);
  const [campaigns, setCampaigns] = useState<CampaignListItem[]>([]);
  const [kind, setKind] = useState<'' | DocumentKind>('');
  const [campaignFilter, setCampaignFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [openingId, setOpeningId] = useState<string | null>(null);

  useEffect(() => {
    if (!ready || !found || context.source !== 'api' || !selectedWorkspaceId) {
      setCampaigns([]);
      return;
    }
    let cancelled = false;
    loadApiCampaigns(selectedWorkspaceId)
      .then((items) => { if (!cancelled) setCampaigns(items); })
      .catch((cause) => console.warn('Unable to load document campaigns', cause));
    return () => { cancelled = true; };
  }, [context.source, found, ready, selectedWorkspaceId]);

  useEffect(() => {
    if (!ready || !found || context.source !== 'api' || !selectedWorkspaceId) {
      setDocuments([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    loadFieldDocuments(selectedWorkspaceId, context.id, {
      kind: kind || undefined,
      campaignId: campaignFilter !== 'all' && campaignFilter !== 'unassigned' ? campaignFilter : undefined,
      unassigned: campaignFilter === 'unassigned',
    })
      .then((items) => { if (!cancelled) setDocuments(items); })
      .catch((cause) => { console.warn('Unable to load farm documents', cause); if (!cancelled) setError('No se han podido cargar los documentos.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [campaignFilter, context.id, context.source, found, kind, ready, selectedWorkspaceId]);

  async function openDocument(documentId: string) {
    if (!selectedWorkspaceId) return;
    const popup = window.open('', '_blank');
    try {
      setOpeningId(documentId);
      const access = await getDocumentReadUrl(selectedWorkspaceId, documentId);
      if (popup) popup.location.href = access.url;
      else window.location.href = access.url;
    } catch (cause) {
      console.error('Unable to open document', cause);
      popup?.close();
      setError('El documento no está disponible para lectura en este momento.');
    } finally {
      setOpeningId(null);
    }
  }

  if (!ready || !found || context.source !== 'api') return null;

  const uploadHref = withFieldQuery('/mi-campo/documentos/nuevo', context.id, context.source);
  const filtersActive = Boolean(kind) || campaignFilter !== 'all';
  const visibleDocuments = filtersActive ? documents : documents.slice(0, 5);

  return <section className="section">
    <div className="section-head"><h2>{filtersActive ? 'Documentos' : 'Documentos recientes'}</h2><Link href={uploadHref} className="detail-link">＋ Añadir documento</Link></div>
    <div className="card record-fields">
      <label className="record-field"><span>Tipo</span><select className="record-control" value={kind} onChange={(event) => setKind(event.target.value as '' | DocumentKind)}><option value="">Todos</option>{kinds.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
      <label className="record-field"><span>Campaña</span><select className="record-control" value={campaignFilter} onChange={(event) => setCampaignFilter(event.target.value)}><option value="all">Todas</option><option value="unassigned">Sin asignar</option>{campaigns.map((campaign) => <option key={campaign.id} value={campaign.id}>{campaign.name}{campaign.status === 'active' ? ' · activa' : ''}</option>)}</select></label>
    </div>
    {error ? <p className="form-error" role="alert">{error}</p> : null}
    {loading ? <section className="card"><p>Cargando documentos…</p></section> : null}
    {!loading && documents.length === 0 ? <section className="card"><p>{filtersActive ? 'No hay documentos con estos filtros.' : 'Aún no hay documentos vinculados a esta finca.'}</p></section> : null}
    {visibleDocuments.length ? <div className="card feed today-list">{visibleDocuments.map((document) => {
      const reviewHref = `/mi-campo/documentos/revisar?documentId=${encodeURIComponent(document.id)}&fieldId=${encodeURIComponent(context.id)}&source=${encodeURIComponent(context.source)}`;
      return <div className="feed-row" key={document.id}><div className="feed-copy"><strong>{document.title}</strong><small>{kindLabel(document.kind)} · {document.created_at.slice(0, 10)}{document.campaign_name ? ` · ${document.campaign_name}` : ' · sin campaña'}{document.domain_type ? ` · vinculado a ${document.domain_type}` : ''}</small></div><div className="record-actions">{ocrKinds.has(document.kind as DocumentKind) ? <Link className="secondary-action action-link" href={reviewHref}>Analizar</Link> : null}<button type="button" className="secondary-action" onClick={() => void openDocument(document.id)} disabled={openingId === document.id}>{openingId === document.id ? 'Abriendo…' : 'Abrir'}</button></div></div>;
    })}</div> : null}
    {!filtersActive && documents.length > 5 ? <p className="subtle">Mostrando los 5 más recientes. Usa los filtros para consultar el catálogo.</p> : null}
  </section>;
}
