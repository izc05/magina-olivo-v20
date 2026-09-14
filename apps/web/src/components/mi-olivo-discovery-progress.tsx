'use client';

import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-discovery-compass.module.css';

type DiscoverySummary = {
  rule_version: string;
  enabled: boolean;
  points: number;
  discoveries: number;
  worlds_discovered: number;
  counts: {
    sections: number;
    mills: number;
    businesses: number;
    experiences: number;
    heritage: number;
    routes: number;
    market_checks: number;
  };
  today: { earned: number; cap: number; remaining: number };
  weekly: { earned: number; goal: number; percent: number };
  rhythm: {
    active_weeks: number;
    grace_active: boolean;
    label: string;
    message: string;
  };
};

export function MiOlivoDiscoveryProgress() {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [summary, setSummary] = useState<DiscoverySummary | null>(null);

  const load = useCallback(async () => {
    if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) {
      setSummary(null);
      return;
    }
    try {
      const value = await apiFetch<DiscoverySummary>('/api/v1/mi-olivo/discovery-summary', {
        workspaceId: selectedWorkspaceId,
      });
      setSummary(value);
    } catch (error) {
      console.debug('Mi Olivo Discovery summary is not available.', error);
      setSummary(null);
    }
  }, [apiConfigured, selectedWorkspaceId, status]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    const refresh = () => { void load(); };
    window.addEventListener('magina:mi-olivo-award', refresh);
    return () => window.removeEventListener('magina:mi-olivo-award', refresh);
  }, [load]);

  if (!summary) return null;

  return (
    <div className={styles.progressPanel} aria-label="Huella de exploración de Mi Olivo">
      <div className={styles.progressHeadline}>
        <div>
          <span className={styles.kicker}>TU HUELLA DE EXPLORACIÓN</span>
          <strong>{summary.discoveries} descubrimientos únicos</strong>
          <span>{summary.rhythm.label}</span>
        </div>
        <div className={styles.discoveryPoints}>
          <strong>{summary.points}</strong>
          <span>🫒 por descubrir Mágina</span>
        </div>
      </div>

      <div className={styles.progressStats}>
        <div><span>Mundos abiertos</span><strong>{summary.worlds_discovered}</strong></div>
        <div><span>Almazaras</span><strong>{summary.counts.mills}</strong></div>
        <div><span>Empresas</span><strong>{summary.counts.businesses}</strong></div>
        <div><span>Experiencias</span><strong>{summary.counts.experiences}</strong></div>
        <div><span>Rutas</span><strong>{summary.counts.routes}</strong></div>
        <div><span>Patrimonio</span><strong>{summary.counts.heritage}</strong></div>
      </div>

      <div className={styles.weeklyDiscovery}>
        <div>
          <span>Exploración esta semana</span>
          <strong>{summary.weekly.earned}/{summary.weekly.goal} 🫒</strong>
        </div>
        <div className={styles.progressTrack} aria-label={`${summary.weekly.percent}% del objetivo semanal de exploración`}>
          <span style={{ width: `${summary.weekly.percent}%` }} />
        </div>
        <small>{summary.rhythm.message}</small>
      </div>
    </div>
  );
}
