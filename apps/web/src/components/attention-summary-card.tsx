'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadAttentionSummary, type AttentionSummary } from '@/lib/attention-data-source';

function label(value?: string) {
  if (value === 'avoid') return 'Evitar';
  if (value === 'caution') return 'Precaución';
  if (value === 'good') return 'Favorable';
  return 'Pendiente';
}

export function AttentionSummaryCard({ fieldId, compact = false, inferFieldFromQuery = false }: { fieldId?: string; compact?: boolean; inferFieldFromQuery?: boolean }) {
  const params = useSearchParams();
  const inferredFieldId = inferFieldFromQuery ? params.get('id') ?? undefined : undefined;
  const effectiveFieldId = fieldId ?? inferredFieldId;
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [data, setData] = useState<AttentionSummary | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId) {
      setData(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    loadAttentionSummary({ workspaceId: selectedWorkspaceId, fieldId: effectiveFieldId, limit: compact ? 3 : 5 })
      .then((result) => { if (!cancelled) setData(result); })
      .catch((error) => { console.warn('Attention summary unavailable', error); if (!cancelled) setData(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [apiConfigured, compact, effectiveFieldId, selectedWorkspaceId, status]);

  if (!apiConfigured) return null;
  if (loading && !data) return <section className="card"><p>Cargando lo importante…</p></section>;
  if (!data || !data.items.length) return null;

  return <section className="section">
    <div className="section-head"><h2>{effectiveFieldId ? 'Lo importante ahora' : 'Tu campo hoy'}</h2><Link href="/mi-campo/hoy">Ver agenda</Link></div>
    <div className="card feed today-list">
      {data.items.map((item) => <div className="feed-row" key={item.id}>
        <div className="feed-copy">
          <strong>{item.title}</strong>
          <small>{effectiveFieldId ? item.scheduledAt.slice(0, 16).replace('T', ' ') : `${item.fieldName} · ${item.scheduledAt.slice(0, 16).replace('T', ' ')}`}</small>
          {item.advisory ? <small>
            {label(item.advisory.suitability)} · {item.advisory.summary}
            {item.advisory.radar?.fresh && item.advisory.radar.precipitationDetected ? ` · Radar: eco a ${item.advisory.radar.nearestEchoDistanceKm ?? '?'} km` : ''}
          </small> : null}
        </div>
        <span className="pending-pill">{item.overdue ? 'Atrasada' : item.advisory ? label(item.advisory.suitability) : 'Próxima'}</span>
      </div>)}
    </div>
    {data.counts.important > 0 ? <p className="subtle">{data.counts.important} elemento{data.counts.important === 1 ? '' : 's'} requieren atención. Previsión y radar observado se muestran como evidencias separadas.</p> : null}
  </section>;
}
