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

  useEffect(() => {
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId) {
      setItems([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    Promise.all([
      loadAttentionSummary({ workspaceId: selectedWorkspaceId, limit: 8 }),
      loadFinancialAttention({ workspaceId: selectedWorkspaceId, limit: 8 }),
      loadProfessionalAttention(selectedWorkspaceId, 8),
      loadHomePriorityPreferences(selectedWorkspaceId),
    ])
      .then(([attention, financial, professional, preferences]) => {
        if (!cancelled) setItems(buildHomePriorities({ attention: attention.items, financial, professional, preferences, limit: 4 }));
      })
      .catch((error) => {
        console.warn('Home priorities unavailable', error);
        if (!cancelled) setItems([]);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  if (!apiConfigured || status !== 'authenticated') return null;
  if (loading && !items.length) return <section className="card"><p>Ordenando prioridades…</p></section>;
  if (!items.length) return null;

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
    <p className="subtle">Ordenado con reglas {HOME_PRIORITY_RULE_VERSION}. Las preferencias pueden reducir economía, documentos y seguimiento profesional, pero nunca ocultar tareas atrasadas ni recomendaciones “Evitar”.</p>
  </section>;
}
