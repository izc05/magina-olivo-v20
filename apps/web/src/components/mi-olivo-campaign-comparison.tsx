'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-campaign-comparison.module.css';

type Campaign = {
  id: string;
  name: string;
  status: string;
  start_date: string;
  end_date?: string | null;
};

type CampaignSummary = {
  campaign: Campaign;
  delivered_kg: number;
  weighted_yield_percent: number | null;
  delivery_count: number;
  field_count: number;
};

type LoadedCampaign = {
  campaign: Campaign;
  summary: CampaignSummary;
};

function parseDate(value?: string | null) {
  if (!value) return 0;
  const date = new Date(`${value}T12:00:00`);
  return Number.isNaN(date.getTime()) ? 0 : date.getTime();
}

function formatNumber(value: number, digits = 0) {
  return new Intl.NumberFormat('es-ES', { maximumFractionDigits: digits }).format(value);
}

function deltaLabel(value: number, suffix = '') {
  if (value === 0) return `Sin cambio${suffix ? ` ${suffix}` : ''}`;
  const sign = value > 0 ? '+' : '−';
  return `${sign}${formatNumber(Math.abs(value), suffix === '%' ? 2 : 0)}${suffix}`;
}

export function MiOlivoCampaignComparison() {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [campaigns, setCampaigns] = useState<LoadedCampaign[]>([]);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) {
        setCampaigns([]);
        return;
      }

      try {
        const payload = await apiFetch<{ campaigns: Campaign[] }>('/api/v1/campaigns', { workspaceId: selectedWorkspaceId });
        const closed = payload.campaigns
          .filter((campaign) => campaign.status === 'closed')
          .sort((left, right) => parseDate(right.end_date) - parseDate(left.end_date))
          .slice(0, 2);

        if (closed.length < 2) {
          if (!cancelled) setCampaigns([]);
          return;
        }

        const summaries = await Promise.allSettled(
          closed.map((campaign) => apiFetch<CampaignSummary>(`/api/v1/campaigns/${campaign.id}/summary`, {
            workspaceId: selectedWorkspaceId,
          })),
        );

        const loaded = closed.flatMap((campaign, index) => {
          const result = summaries[index];
          return result?.status === 'fulfilled' ? [{ campaign, summary: result.value }] : [];
        });

        if (!cancelled) setCampaigns(loaded.length === 2 ? loaded : []);
      } catch (error) {
        console.warn('Unable to load Mi Olivo campaign comparison', error);
        if (!cancelled) setCampaigns([]);
      }
    }

    void load();
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const metrics = useMemo(() => {
    if (campaigns.length !== 2) return null;
    const [latest, previous] = campaigns;
    return {
      latest,
      previous,
      kgDelta: latest.summary.delivered_kg - previous.summary.delivered_kg,
      deliveryDelta: latest.summary.delivery_count - previous.summary.delivery_count,
      fieldDelta: latest.summary.field_count - previous.summary.field_count,
      yieldDelta: latest.summary.weighted_yield_percent !== null && previous.summary.weighted_yield_percent !== null
        ? latest.summary.weighted_yield_percent - previous.summary.weighted_yield_percent
        : null,
    };
  }, [campaigns]);

  if (!metrics) return null;

  return (
    <section className={styles.shell} aria-label="Comparativa real de campañas de Mi Olivo">
      <div className={styles.heading}>
        <div>
          <span className={styles.eyebrow}>MEMORIA COMPARADA</span>
          <h2>Dos campañas, lado a lado</h2>
          <p>Comparamos únicamente valores registrados. Las diferencias son numéricas: no calificamos una campaña como mejor o peor.</p>
        </div>
        <Link href="/mi-campo/campana" className={styles.link}>Ver campañas →</Link>
      </div>

      <div className={styles.campaigns}>
        {[metrics.latest, metrics.previous].map((item, index) => (
          <article className={styles.campaign} key={item.campaign.id}>
            <span>{index === 0 ? 'Más reciente' : 'Anterior'}</span>
            <h3>{item.campaign.name}</h3>
            <div className={styles.stats}>
              <div><small>Entregado</small><strong>{formatNumber(item.summary.delivered_kg)} kg</strong></div>
              <div><small>Entregas</small><strong>{item.summary.delivery_count}</strong></div>
              <div><small>Fincas</small><strong>{item.summary.field_count}</strong></div>
              <div><small>Rendimiento</small><strong>{item.summary.weighted_yield_percent === null ? 'Pendiente' : `${formatNumber(item.summary.weighted_yield_percent, 2)} %`}</strong></div>
            </div>
          </article>
        ))}
      </div>

      <div className={styles.deltas} aria-label={`Diferencias de ${metrics.latest.campaign.name} respecto a ${metrics.previous.campaign.name}`}>
        <div><span>Kg entregados</span><strong>{deltaLabel(metrics.kgDelta, ' kg')}</strong></div>
        <div><span>Entregas</span><strong>{deltaLabel(metrics.deliveryDelta)}</strong></div>
        <div><span>Fincas</span><strong>{deltaLabel(metrics.fieldDelta)}</strong></div>
        <div><span>Rendimiento</span><strong>{metrics.yieldDelta === null ? 'Sin comparación' : deltaLabel(metrics.yieldDelta, '%')}</strong></div>
      </div>
    </section>
  );
}
