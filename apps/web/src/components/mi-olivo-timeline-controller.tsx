'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-timeline-controller.module.css';

type LedgerEntry = {
  id: string;
  event_type: string;
  points: number;
  reason: string;
  created_at: string;
};

type MiOlivoPayload = {
  enabled: boolean;
  recent: LedgerEntry[];
};

type Campaign = {
  id: string;
  name: string;
  status: string;
  start_date: string;
  end_date?: string | null;
};

type TimelineItem = {
  id: string;
  timestamp: number;
  date: string;
  eyebrow: string;
  title: string;
  detail: string;
};

function parseDate(value?: string | null) {
  if (!value) return null;
  const date = new Date(value.includes('T') ? value : `${value}T12:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(date: Date) {
  return new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
}

function campaignLabel(status: string) {
  if (status === 'active') return 'Campaña activa';
  if (status === 'closed') return 'Campaña cerrada';
  if (status === 'planned') return 'Campaña planificada';
  return 'Campaña registrada';
}

export function MiOlivoTimelineController() {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [ledger, setLedger] = useState<LedgerEntry[]>([]);
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) {
        setLedger([]);
        setCampaigns([]);
        setLoaded(false);
        return;
      }

      try {
        const [olivo, campaignPayload] = await Promise.all([
          apiFetch<MiOlivoPayload>('/api/v1/mi-olivo', { workspaceId: selectedWorkspaceId }),
          apiFetch<{ campaigns: Campaign[] }>('/api/v1/campaigns', { workspaceId: selectedWorkspaceId }),
        ]);
        if (!cancelled) {
          setLedger(olivo.enabled ? olivo.recent ?? [] : []);
          setCampaigns(campaignPayload.campaigns ?? []);
          setLoaded(true);
        }
      } catch (error) {
        console.warn('Unable to load Mi Olivo timeline', error);
        if (!cancelled) {
          setLedger([]);
          setCampaigns([]);
          setLoaded(false);
        }
      }
    }

    void load();
    const refresh = () => { void load(); };
    window.addEventListener('magina:mi-olivo-award', refresh);
    return () => {
      cancelled = true;
      window.removeEventListener('magina:mi-olivo-award', refresh);
    };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const items = useMemo<TimelineItem[]>(() => {
    const next: TimelineItem[] = [];

    for (const entry of ledger) {
      const date = parseDate(entry.created_at);
      if (!date) continue;
      next.push({
        id: `ledger:${entry.id}`,
        timestamp: date.getTime(),
        date: formatDate(date),
        eyebrow: 'ACTIVIDAD REAL',
        title: entry.reason,
        detail: entry.points > 0 ? `+${entry.points} aceitunas reconocidas` : 'Actividad registrada',
      });
    }

    for (const campaign of campaigns) {
      const start = parseDate(campaign.start_date);
      if (start) {
        next.push({
          id: `campaign-start:${campaign.id}`,
          timestamp: start.getTime(),
          date: formatDate(start),
          eyebrow: campaignLabel(campaign.status).toUpperCase(),
          title: `Inicio registrado · ${campaign.name}`,
          detail: 'Fecha guardada en tu campaña. No inferimos una fase agronómica.',
        });
      }

      const end = parseDate(campaign.end_date);
      if (end && campaign.status === 'closed') {
        next.push({
          id: `campaign-end:${campaign.id}`,
          timestamp: end.getTime(),
          date: formatDate(end),
          eyebrow: 'CAMPAÑA CERRADA',
          title: `Cierre registrado · ${campaign.name}`,
          detail: 'Este cierre forma parte de la memoria real de tu olivo.',
        });
      }
    }

    return next.sort((left, right) => right.timestamp - left.timestamp).slice(0, 8);
  }, [campaigns, ledger]);

  if (status !== 'authenticated' || !apiConfigured || !loaded) return null;

  return (
    <section className={styles.shell} aria-label="Línea temporal real de Mi Olivo">
      <div className={styles.heading}>
        <div>
          <span className={styles.eyebrow}>TU HISTORIA</span>
          <h2>Lo que ya ha pasado</h2>
          <p>Una línea temporal construida con registros reales de Mi Olivo y fechas guardadas de campaña.</p>
        </div>
        <Link href="/mi-campo" className={styles.link}>Abrir Mi Campo →</Link>
      </div>

      {items.length === 0 ? (
        <div className={styles.empty}>
          <strong>Tu historia empieza aquí.</strong>
          <span>Cuando registres actividad, explores Mágina o abras una campaña, aparecerá en este recorrido.</span>
          <div className={styles.emptyActions}>
            <Link href="/mi-campo">Ir a Mi Campo</Link>
            <Link href="/explorar">Explorar Mágina</Link>
          </div>
        </div>
      ) : (
        <ol className={styles.timeline}>
          {items.map((item) => (
            <li className={styles.item} key={item.id}>
              <span className={styles.dot} aria-hidden="true" />
              <div className={styles.card}>
                <div className={styles.meta}><span>{item.eyebrow}</span><time>{item.date}</time></div>
                <strong>{item.title}</strong>
                <p>{item.detail}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
