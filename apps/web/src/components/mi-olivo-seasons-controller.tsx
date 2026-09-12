'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-seasons-controller.module.css';

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
  summary: CampaignSummary | null;
};

type CampaignMoment = {
  key: 'planned' | 'active' | 'active-before-start' | 'active-after-end' | 'closed' | 'unknown';
  label: string;
  detail: string;
};

function parseDateOnly(value?: string | null) {
  if (!value) return null;
  const date = new Date(`${value}T12:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

function campaignMoment(campaign: Campaign): CampaignMoment {
  if (campaign.status === 'planned') {
    return {
      key: 'planned',
      label: 'Campaña planificada',
      detail: 'La ambientación refleja únicamente que la campaña está planificada.',
    };
  }

  if (campaign.status === 'closed') {
    return {
      key: 'closed',
      label: 'Campaña cerrada',
      detail: 'Esta campaña ya forma parte de la memoria histórica de tu olivo.',
    };
  }

  if (campaign.status === 'active') {
    const today = new Date();
    const start = parseDateOnly(campaign.start_date);
    const end = parseDateOnly(campaign.end_date);

    if (start && today < start) {
      return {
        key: 'active-before-start',
        label: 'Activa · antes de la fecha de inicio',
        detail: 'El estado sigue siendo activo, aunque la fecha de inicio registrada todavía no ha llegado.',
      };
    }

    if (end && today > end) {
      return {
        key: 'active-after-end',
        label: 'Activa · fecha final superada',
        detail: 'La campaña continúa activa, pero su fecha final registrada ya ha pasado. Conviene revisar su estado.',
      };
    }

    return {
      key: 'active',
      label: 'Campaña activa',
      detail: 'Estás dentro del periodo registrado de una campaña activa. No mostramos porcentajes de avance inventados.',
    };
  }

  return {
    key: 'unknown',
    label: 'Campaña registrada',
    detail: 'Mostramos el estado guardado sin inferir una fase agrícola.',
  };
}

function formatDate(value?: string | null) {
  const date = parseDateOnly(value);
  if (!date) return 'Sin fecha';
  return new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
}

function formatKg(value: number) {
  return new Intl.NumberFormat('es-ES', { maximumFractionDigits: 0 }).format(value);
}

function formatYield(value: number | null) {
  if (value === null) return 'Pendiente';
  return `${new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 }).format(value)} %`;
}

export function MiOlivoSeasonsController() {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [campaigns, setCampaigns] = useState<LoadedCampaign[]>([]);
  const [loading, setLoading] = useState(false);
  const [unavailable, setUnavailable] = useState(false);

  const load = useCallback(async () => {
    if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) {
      setCampaigns([]);
      setUnavailable(false);
      return;
    }

    setLoading(true);
    setUnavailable(false);
    try {
      const response = await apiFetch<{ campaigns: Campaign[] }>('/api/v1/campaigns', {
        workspaceId: selectedWorkspaceId,
      });

      const selected = response.campaigns
        .filter((campaign) => campaign.status === 'active' || campaign.status === 'planned' || campaign.status === 'closed')
        .slice(0, 6);

      const summaries = await Promise.allSettled(
        selected.map((campaign) => apiFetch<CampaignSummary>(`/api/v1/campaigns/${campaign.id}/summary`, {
          workspaceId: selectedWorkspaceId,
        })),
      );

      setCampaigns(selected.map((campaign, index) => {
        const result = summaries[index];
        return {
          campaign,
          summary: result?.status === 'fulfilled' ? result.value : null,
        };
      }));
    } catch (error) {
      console.warn('Unable to load Mi Olivo campaign memory', error);
      setCampaigns([]);
      setUnavailable(true);
    } finally {
      setLoading(false);
    }
  }, [apiConfigured, selectedWorkspaceId, status]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    const refresh = () => { void load(); };
    window.addEventListener('magina:mi-olivo-award', refresh);
    return () => window.removeEventListener('magina:mi-olivo-award', refresh);
  }, [load]);

  const current = useMemo(
    () => campaigns.find(({ campaign }) => campaign.status === 'active')
      ?? campaigns.find(({ campaign }) => campaign.status === 'planned')
      ?? campaigns[0]
      ?? null,
    [campaigns],
  );

  const closed = useMemo(
    () => campaigns.filter(({ campaign }) => campaign.status === 'closed').slice(0, 4),
    [campaigns],
  );

  if (status !== 'authenticated' || !apiConfigured) return null;

  const moment = current ? campaignMoment(current.campaign) : null;

  return (
    <section className={styles.shell} aria-label="Memoria real de campañas de Mi Olivo">
      <div className={styles.heading}>
        <div>
          <span className={styles.eyebrow}>MI OLIVO · V6</span>
          <h2>Tu olivo guarda memoria</h2>
          <p>El momento visual y los anillos salen de campañas reales. No crean puntos, no cambian rendimientos y no sustituyen al registro agrícola.</p>
        </div>
        <Link href="/mi-campo/campana" className={styles.link}>Ver campañas →</Link>
      </div>

      {loading && campaigns.length === 0 ? <div className={styles.softState}>Leyendo tus campañas…</div> : null}

      {!loading && unavailable ? (
        <div className={styles.softState}>
          <strong>La memoria de campañas no está disponible ahora mismo.</strong>
          <span>Mi Olivo, tus misiones y tus datos de campo siguen funcionando con normalidad.</span>
        </div>
      ) : null}

      {!loading && !unavailable && campaigns.length === 0 ? (
        <div className={styles.softState}>
          <strong>Aún no hay campañas que recordar.</strong>
          <span>Cuando registres una campaña, este espacio empezará a construir la historia de tu olivo.</span>
        </div>
      ) : null}

      {current && moment ? (
        <div className={styles.grid}>
          <article className={`${styles.momentCard} ${styles[`moment_${moment.key}`]}`} data-campaign-moment={moment.key}>
            <div className={styles.momentSky} aria-hidden="true">
              <span className={styles.orbit} />
              <span className={styles.hillFar} />
              <span className={styles.hillNear} />
              <span className={styles.miniTree}>♧</span>
            </div>
            <div className={styles.momentCopy}>
              <span className={styles.eyebrow}>MOMENTO DE CAMPAÑA</span>
              <h3>{moment.label}</h3>
              <strong>{current.campaign.name}</strong>
              <p>{moment.detail}</p>
              <div className={styles.dateRow}>
                <span>Inicio <strong>{formatDate(current.campaign.start_date)}</strong></span>
                <span>Fin <strong>{formatDate(current.campaign.end_date)}</strong></span>
              </div>
              {current.summary ? (
                <div className={styles.currentStats}>
                  <span><small>Entregado</small><strong>{formatKg(current.summary.delivered_kg)} kg</strong></span>
                  <span><small>Entregas</small><strong>{current.summary.delivery_count}</strong></span>
                  <span><small>Fincas</small><strong>{current.summary.field_count}</strong></span>
                </div>
              ) : (
                <div className={styles.summaryUnavailable}>El resumen de esta campaña no está disponible; no se estiman cifras.</div>
              )}
            </div>
          </article>

          <article className={styles.memoryCard}>
            <div className={styles.memoryHeading}>
              <div>
                <span className={styles.eyebrow}>ANILLOS DEL OLIVO</span>
                <h3>Campañas cerradas</h3>
              </div>
              <span className={styles.ringCount}>{closed.length}</span>
            </div>

            {closed.length === 0 ? (
              <div className={styles.emptyMemory}>
                <div className={styles.emptyRings} aria-hidden="true"><span /><span /><span /></div>
                <strong>El primer anillo llegará al cerrar una campaña.</strong>
                <p>No cerramos campañas automáticamente ni inferimos que hayan terminado.</p>
              </div>
            ) : (
              <div className={styles.ringList}>
                {closed.map(({ campaign, summary }, index) => (
                  <article className={styles.ring} key={campaign.id} aria-label={`Memoria de campaña ${campaign.name}`}>
                    <div className={styles.ringMark} aria-hidden="true"><span>{index + 1}</span></div>
                    <div className={styles.ringMain}>
                      <div className={styles.ringTitle}><strong>{campaign.name}</strong><small>Cerrada</small></div>
                      <div className={styles.ringDates}>{formatDate(campaign.start_date)} · {formatDate(campaign.end_date)}</div>
                      {summary ? (
                        <div className={styles.ringStats}>
                          <span><b>{formatKg(summary.delivered_kg)} kg</b> entregados</span>
                          <span><b>{summary.delivery_count}</b> entregas</span>
                          <span><b>{summary.field_count}</b> fincas</span>
                          <span><b>{formatYield(summary.weighted_yield_percent)}</b> rendimiento</span>
                        </div>
                      ) : <small className={styles.noSummary}>Sin resumen disponible</small>}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </article>
        </div>
      ) : null}
    </section>
  );
}
