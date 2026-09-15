'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadAttentionSummary } from '@/lib/attention-data-source';
import { loadFinancialAttention } from '@/lib/financial-attention-data-source';
import { loadProfessionalAttention } from '@/lib/professional-attention-data-source';
import { loadHomePriorityPreferences } from '@/lib/home-priority-preferences';
import { buildHomePriorities, HOME_PRIORITY_RULE_VERSION, type HomePriorityItem } from '@/lib/home-priority-rules';

function levelLabel(level: HomePriorityItem['level']) {
  if (level === 'critical') return 'Urgente';
  if (level === 'high') return 'Alta';
  if (level === 'medium') return 'Media';
  return 'Baja';
}

export function HomePriorityCard() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [items, setItems] = useState<HomePriorityItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [unavailableSources, setUnavailableSources] = useState(0);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId) {
      setItems([]);
      setUnavailableSources(0);
      return;
    }
    const workspaceId = selectedWorkspaceId;
    let cancelled = false;

    async function loadPriorities() {
      setLoading(true);
      const results = await Promise.allSettled([
        loadAttentionSummary({ workspaceId, limit: 8 }),
        loadFinancialAttention({ workspaceId, limit: 8 }),
        loadProfessionalAttention(workspaceId, 8),
        loadHomePriorityPreferences(workspaceId),
      ]);
      if (cancelled) return;

      const attention = results[0].status === 'fulfilled' ? results[0].value.items : [];
      const financial = results[1].status === 'fulfilled' ? results[1].value : null;
      const professional = results[2].status === 'fulfilled' ? results[2].value : null;
      const preferences = results[3].status === 'fulfilled' ? results[3].value : undefined;
      const failures = results.filter((result) => result.status === 'rejected').length;

      setItems(buildHomePriorities({ attention, financial, professional, preferences, limit: 4 }));
      setUnavailableSources(failures);
      setLoading(false);
    }

    void loadPriorities().catch((error) => {
      console.warn('Home priorities unavailable', error);
      if (!cancelled) {
        setItems([]);
        setUnavailableSources(4);
        setLoading(false);
      }
    });
    return () => { cancelled = true; };
  }, [apiConfigured, revision, selectedWorkspaceId, status]);

  if (!apiConfigured || status !== 'authenticated') return null;
  if (loading && !items.length) return <section className="section card" aria-busy="true"><p>Ordenando prioridades…</p></section>;

  if (!items.length) {
    return <section className="section card">
      <div className="section-head"><div><h2>{unavailableSources ? 'Prioridades parcialmente disponibles' : 'Sin prioridades urgentes'}</h2><small>{unavailableSources ? 'No se han podido revisar todas las fuentes.' : 'No hay nada que requiera atención inmediata.'}</small></div><Link href="/mi-campo/hoy">Ver Hoy</Link></div>
      {unavailableSources ? <div className="record-actions"><button className="secondary-action" type="button" onClick={() => setRevision((value) => value + 1)}>Reintentar</button></div> : null}
    </section>;
  }

  return <section className="section">
    <div className="section-head"><div><h2>Prioridad ahora</h2><small>Solo lo más importante en este momento</small></div><Link href="/perfil">Ajustar</Link></div>
    <div className="card feed today-list">
      {items.map((item, index) => <div className="feed-row" key={item.id}>
        <div className="feed-copy">
          <strong>{index + 1}. {item.title}</strong>
          <small>{item.subtitle}</small>
          <small>{item.reason}{item.detail ? ` · ${item.detail}` : ''}{item.protected ? ' · prioridad protegida' : ''}</small>
        </div>
        <div className="record-actions">
          <span className="pending-pill">{levelLabel(item.level)}</span>
          <Link className="secondary-action action-link" href={item.href}>{item.actionLabel}</Link>
        </div>
      </div>)}
    </div>
    {unavailableSources ? <div className="card"><p>Hay {unavailableSources} fuente{unavailableSources === 1 ? '' : 's'} sin respuesta. Las prioridades visibles proceden de las fuentes que sí han cargado.</p><button className="secondary-action" type="button" onClick={() => setRevision((value) => value + 1)}>Reintentar fuentes</button></div> : null}
    <p className="subtle">Ordenado con reglas {HOME_PRIORITY_RULE_VERSION}. Las preferencias pueden reducir economía, documentos y seguimiento profesional, pero nunca ocultar tareas atrasadas ni recomendaciones “Evitar”.</p>
  </section>;
}
