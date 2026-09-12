'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadFinancialAttention, type FinancialAttentionSummary } from '@/lib/financial-attention-data-source';

function money(value: number) {
  return `${value.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €`;
}

function documentStatusLabel(status: FinancialAttentionSummary['documents'][number]['status']) {
  if (status === 'needs_ocr') return 'Pendiente de analizar';
  if (status === 'processing') return 'Analizando';
  if (status === 'ocr_failed') return 'OCR fallido';
  if (status === 'needs_review') return 'Pendiente de revisar';
  return 'Sin campos estructurados';
}

export function FinancialAttentionCard({ fieldId, inferFieldFromQuery = false, compact = false }: { fieldId?: string; inferFieldFromQuery?: boolean; compact?: boolean }) {
  const params = useSearchParams();
  const inferredFieldId = inferFieldFromQuery ? params.get('id') ?? undefined : undefined;
  const effectiveFieldId = fieldId ?? inferredFieldId;
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [data, setData] = useState<FinancialAttentionSummary | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId) {
      setData(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    loadFinancialAttention({ workspaceId: selectedWorkspaceId, fieldId: effectiveFieldId, limit: compact ? 3 : 6 })
      .then((next) => { if (!cancelled) setData(next); })
      .catch((error) => { console.warn('Financial attention unavailable', error); if (!cancelled) setData(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [apiConfigured, compact, effectiveFieldId, selectedWorkspaceId, status]);

  if (!apiConfigured) return null;
  if (loading && !data) return <section className="card"><p>Cargando pendientes económicos…</p></section>;
  if (!data || (!data.settlements.length && !data.documents.length)) return null;

  return <section className="section">
    <div className="section-head"><h2>{effectiveFieldId ? 'Pendientes de la finca' : 'Pendientes económicos y documentos'}</h2><Link href="/mi-campo/campana">Ver campaña</Link></div>
    <div className="quick-grid">
      {data.pendingCollectionEur > 0 ? <article className="card quick premium-quick"><div><strong>{money(data.pendingCollectionEur)}</strong><small>pendiente de cobrar</small></div></article> : null}
      {data.counts.pendingDocuments > 0 ? <article className="card quick premium-quick"><div><strong>{data.counts.pendingDocuments}</strong><small>documentos pendientes</small></div></article> : null}
    </div>

    {data.settlements.length ? <div className="card feed today-list">
      {data.settlements.map((item) => {
        const href = effectiveFieldId
          ? `/mi-campo/registrar/cobro?fieldId=${encodeURIComponent(effectiveFieldId)}&source=api`
          : '/mi-campo/campana';
        return <div className="feed-row" key={`settlement-${item.id}`}><div className="feed-copy"><strong>{item.title}</strong><small>{item.subtitle || 'Liquidación'} · pendiente {money(item.pendingEur)}</small></div><Link className="secondary-action action-link" href={href}>{effectiveFieldId ? 'Registrar cobro' : 'Ver campaña'}</Link></div>;
      })}
    </div> : null}

    {data.documents.length ? <div className="card feed today-list">
      {data.documents.map((item) => {
        const reviewHref = `/mi-campo/documentos/revisar?documentId=${encodeURIComponent(item.id)}&fieldId=${encodeURIComponent(item.fieldId)}&source=api`;
        return <div className="feed-row" key={`document-${item.id}`}><div className="feed-copy"><strong>{item.title}</strong><small>{item.subtitle} · {documentStatusLabel(item.status)}</small></div><Link className="secondary-action action-link" href={reviewHref}>Revisar</Link></div>;
      })}
    </div> : null}
  </section>;
}
