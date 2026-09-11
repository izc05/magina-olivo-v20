'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadApiCampaigns, type CampaignListItem } from '@/lib/campaign-data-source';
import { getDocumentReadUrl, loadFieldDocuments, type FieldDocument } from '@/lib/document-data-source';
import type { DocumentKind } from '@/lib/document-upload-source';
import {
  buildDocumentReviewHref,
  documentKindLabel,
  documentKinds,
  supportsDocumentOcr,
} from '@/lib/document-workflow';
import { useFieldContext, withFieldQuery } from '@/lib/use-field-context';
import styles from './document-workflow.module.css';

const domainLabels: Record<string, string> = {
  expense: 'Gasto vinculado',
  harvest_delivery: 'Entrega vinculada',
  harvest_result: 'Rendimiento vinculado',
  harvest_settlement: 'Liquidación vinculada',
  harvest_collection: 'Cobro vinculado',
};

function formatDocumentDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 10);
  return new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date);
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
    if (!ready || !found || context.source !== 'api' || !selectedWorkspaceId) { setCampaigns([]); return; }
    let cancelled = false;
    loadApiCampaigns(selectedWorkspaceId)
      .then((items) => { if (!cancelled) setCampaigns(items); })
      .catch((cause) => console.warn('Unable to load document campaigns', cause));
    return () => { cancelled = true; };
  }, [context.source, found, ready, selectedWorkspaceId]);

  useEffect(() => {
    if (!ready || !found || context.source !== 'api' || !selectedWorkspaceId) { setDocuments([]); return; }
    let cancelled = false;
    setLoading(true);
    setError(null);
    loadFieldDocuments(selectedWorkspaceId, context.id, {
      kind: kind || undefined,
      campaignId: campaignFilter !== 'all' && campaignFilter !== 'unassigned' ? campaignFilter : undefined,
      unassigned: campaignFilter === 'unassigned',
    }).then((items) => { if (!cancelled) setDocuments(items); })
      .catch((cause) => { console.warn('Unable to load farm documents', cause); if (!cancelled) setError('No se han podido cargar los documentos.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [campaignFilter, context.id, context.source, found, kind, ready, selectedWorkspaceId]);

  async function openDocument(documentId: string) {
    if (!selectedWorkspaceId) return;
    const popup = window.open('', '_blank');
    try {
      setOpeningId(documentId);
      setError(null);
      const access = await getDocumentReadUrl(selectedWorkspaceId, documentId);
      if (popup) popup.location.href = access.url;
      else window.location.href = access.url;
    } catch (cause) {
      console.error('Unable to open document', cause);
      popup?.close();
      setError('El archivo original no está disponible para lectura en este momento.');
    } finally {
      setOpeningId(null);
    }
  }

  if (!ready || !found || context.source !== 'api') return null;

  const uploadHref = withFieldQuery('/mi-campo/documentos/nuevo', context.id, context.source);
  const filtersActive = Boolean(kind) || campaignFilter !== 'all';
  const visibleDocuments = filtersActive ? documents : documents.slice(0, 5);

  return <section className="section">
    <div className="section-head"><div><h2>{filtersActive ? 'Documentos' : 'Documentos recientes'}</h2><p className="subtle">Originales privados vinculados a esta finca. OCR y registros se revisan por separado.</p></div><Link href={uploadHref} className="detail-link">＋ Añadir documento</Link></div>
    <div className="card record-fields">
      <label className="record-field"><span>Tipo</span><select className="record-control" value={kind} onChange={(event) => setKind(event.target.value as '' | DocumentKind)}><option value="">Todos</option>{documentKinds.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
      <label className="record-field"><span>Campaña</span><select className="record-control" value={campaignFilter} onChange={(event) => setCampaignFilter(event.target.value)}><option value="all">Todas</option><option value="unassigned">Sin asignar</option>{campaigns.map((campaign) => <option key={campaign.id} value={campaign.id}>{campaign.name}{campaign.status === 'active' ? ' · activa' : ''}</option>)}</select></label>
    </div>

    {error ? <p className="form-error" role="alert">{error}</p> : null}
    {loading ? <section className="card" aria-live="polite"><p>Cargando documentos…</p></section> : null}
    {!loading && documents.length === 0 ? <section className={`card ${styles.flowCard}`}><div><h3>{filtersActive ? 'No hay documentos con estos filtros' : 'Aún no hay documentos'}</h3><p>{filtersActive ? 'Prueba otro tipo o campaña.' : 'Guarda aquí albaranes, facturas, liquidaciones, fotos y demás evidencias de la finca.'}</p></div>{!filtersActive ? <Link className="primary action-link" href={uploadHref}>Añadir primer documento</Link> : null}</section> : null}

    {visibleDocuments.length ? <div className="card feed today-list">{visibleDocuments.map((document) => {
      const canAnalyze = supportsDocumentOcr(document.kind);
      const reviewHref = buildDocumentReviewHref(document.id, context.id, context.source);
      return <div className="feed-row" key={document.id}>
        <div className="feed-copy"><strong>{document.title}</strong><small>{documentKindLabel(document.kind)} · {formatDocumentDate(document.created_at)}</small><div className={styles.catalogMeta}><span className={styles.catalogTag}>{document.campaign_name ?? 'Sin campaña'}</span>{document.domain_type ? <span className={styles.catalogTag}>{domainLabels[document.domain_type] ?? 'Registro vinculado'} ✓</span> : <span className={styles.catalogTag}>{canAnalyze ? 'OCR opcional · revisión humana' : 'Evidencia documental'}</span>}</div></div>
        <div className="record-actions">{canAnalyze ? <Link className="secondary-action action-link" href={reviewHref}>{document.domain_type ? 'Ver lectura' : 'Analizar / revisar'}</Link> : null}<button type="button" className="secondary-action" onClick={() => void openDocument(document.id)} disabled={openingId === document.id}>{openingId === document.id ? 'Abriendo…' : 'Abrir original'}</button></div>
      </div>;
    })}</div> : null}

    {!filtersActive && documents.length > 5 ? <p className="subtle">Mostrando los 5 más recientes. Usa los filtros para consultar el resto del catálogo.</p> : null}
  </section>;
}
