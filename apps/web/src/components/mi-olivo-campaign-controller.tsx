'use client';

import type { ReactNode } from 'react';
import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-campaign-controller.module.css';

type Campaign = {
  id: string;
  name: string;
  status: string;
};

type CampaignMetrics = {
  delivery_count: number;
  delivered_kg: number;
  has_confirmed_yield: boolean;
};

type CampaignMission = {
  id: string;
  event_type: string;
  title: string;
  detail: string;
  reward: number;
  completed: boolean;
  progress_current: number;
  progress_target: number;
};

type BadgeOption = {
  id: 'none' | 'roots' | 'harvest' | 'explorer' | 'campaign';
  title: string;
  detail: string;
  symbol: string;
  unlocked: boolean;
};

type Appearance = {
  selected_badge: BadgeOption['id'];
  options: BadgeOption[];
};

type CampaignPayload = {
  enabled: boolean;
  rule_version: string;
  campaign: Campaign | null;
  metrics: CampaignMetrics | null;
  missions: CampaignMission[];
  newly_awarded_points: number;
  campaign_olives_earned: number;
  appearance: Appearance;
};

function campaignStatus(status: string) {
  if (status === 'active') return 'Activa';
  if (status === 'planned') return 'Planificada';
  if (status === 'closed') return 'Cerrada';
  return 'Campaña';
}

function progressPercent(mission: CampaignMission) {
  if (mission.progress_target <= 0) return 0;
  return Math.min(100, Math.round((mission.progress_current / mission.progress_target) * 100));
}

function formatProgress(mission: CampaignMission) {
  if (mission.id === 'campaign-1000kg') {
    return `${new Intl.NumberFormat('es-ES', { maximumFractionDigits: 0 }).format(mission.progress_current)} / ${new Intl.NumberFormat('es-ES').format(mission.progress_target)} kg`;
  }
  return `${mission.progress_current} / ${mission.progress_target}`;
}

export function MiOlivoCampaignController({ children }: { children: ReactNode }) {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [data, setData] = useState<CampaignPayload | null>(null);
  const [loading, setLoading] = useState(false);
  const [savingBadge, setSavingBadge] = useState<BadgeOption['id'] | null>(null);

  const load = useCallback(async () => {
    if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) {
      setData(null);
      return;
    }

    setLoading(true);
    try {
      const payload = await apiFetch<CampaignPayload>('/api/v1/mi-olivo/campaign', {
        workspaceId: selectedWorkspaceId,
      });
      setData(payload);
    } catch (error) {
      console.warn('Unable to load Mi Olivo campaign missions', error);
      setData(null);
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

  async function selectBadge(badge: BadgeOption['id']) {
    if (!selectedWorkspaceId || !data) return;
    const option = data.appearance.options.find((candidate) => candidate.id === badge);
    if (!option?.unlocked || badge === data.appearance.selected_badge) return;

    setSavingBadge(badge);
    try {
      const appearance = await apiFetch<Appearance>('/api/v1/mi-olivo/appearance', {
        method: 'PUT',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify({ badge }),
      });
      setData((current) => current ? { ...current, appearance } : current);
    } catch (error) {
      console.warn('Unable to change Mi Olivo badge', error);
    } finally {
      setSavingBadge(null);
    }
  }

  const selectedBadge = data?.appearance.options.find((option) => option.id === data.appearance.selected_badge) ?? null;

  return (
    <>
      {selectedBadge && selectedBadge.id !== 'none' ? (
        <aside className={styles.activeBadge} aria-label="Distintivo activo de Mi Olivo">
          <span className={styles.activeBadgeSymbol} aria-hidden="true">{selectedBadge.symbol}</span>
          <span><small>DISTINTIVO ACTIVO</small><strong>{selectedBadge.title}</strong></span>
        </aside>
      ) : null}

      {data?.newly_awarded_points ? (
        <div className={styles.awardBanner} role="status" aria-live="polite">
          <span aria-hidden="true">🫒</span>
          <strong>+{data.newly_awarded_points} aceitunas de campaña</strong>
          <span>Tu registro real acaba de completar una misión.</span>
        </div>
      ) : null}

      {children}

      {status === 'authenticated' && apiConfigured ? (
        <section className={styles.shell} aria-label="Misiones y personalización de Mi Olivo">
          <div className={styles.missionsCard}>
            <div className={styles.sectionHeading}>
              <div>
                <span className={styles.eyebrow}>MISIONES DE CAMPAÑA</span>
                <h2>Lo que ya haces, contado mejor</h2>
              </div>
              {data?.campaign ? <span className={styles.campaignStatus}>{campaignStatus(data.campaign.status)}</span> : null}
            </div>

            {loading && !data ? <p className={styles.muted}>Leyendo tu campaña…</p> : null}

            {!loading && !data ? (
              <div className={styles.softState}>
                <strong>Mi Olivo sigue funcionando.</strong>
                <span>Las misiones de campaña no están disponibles ahora mismo, pero tu árbol, saldo y actividad no se han visto afectados.</span>
              </div>
            ) : null}

            {data && !data.campaign ? (
              <div className={styles.softState}>
                <strong>Aún no hay una campaña para convertir en misiones.</strong>
                <span>Cuando tengas una campaña, Mi Olivo reconocerá hitos administrativos de lo que ya hayas registrado.</span>
                <Link href="/mi-campo/campana" className={styles.link}>Ir a Campaña →</Link>
              </div>
            ) : null}

            {data?.campaign ? (
              <>
                <div className={styles.campaignSummary}>
                  <div><span>Campaña</span><strong>{data.campaign.name}</strong></div>
                  <div><span>Aceitunas V5</span><strong>{data.campaign_olives_earned} 🫒</strong></div>
                  {data.metrics ? <div><span>Registrado</span><strong>{Math.round(data.metrics.delivered_kg).toLocaleString('es-ES')} kg</strong></div> : null}
                </div>

                {!data.enabled ? (
                  <div className={styles.pausedNote}>
                    Mi Olivo está pausado. El progreso real se sigue mostrando, pero estas misiones no conceden aceitunas hasta que lo reactives.
                  </div>
                ) : null}

                <div className={styles.missionGrid}>
                  {data.missions.map((mission) => {
                    const percent = progressPercent(mission);
                    return (
                      <article className={`${styles.mission} ${mission.completed ? styles.completed : ''}`} key={mission.id}>
                        <div className={styles.missionTop}>
                          <span className={styles.missionState}>{mission.completed ? '✓ Completada' : 'En progreso'}</span>
                          <strong className={styles.reward}>+{mission.reward} 🫒</strong>
                        </div>
                        <h3>{mission.title}</h3>
                        <p>{mission.detail}</p>
                        <div className={styles.progressMeta}><span>{formatProgress(mission)}</span><span>{percent}%</span></div>
                        <div className={styles.progressTrack} aria-label={`${percent}% de ${mission.title}`}>
                          <span style={{ width: `${percent}%` }} />
                        </div>
                      </article>
                    );
                  })}
                </div>
              </>
            ) : null}
          </div>

          {data ? (
            <div className={styles.appearanceCard}>
              <div className={styles.sectionHeading}>
                <div>
                  <span className={styles.eyebrow}>PERSONALIZA</span>
                  <h2>Distintivo de tu olivo</h2>
                </div>
              </div>
              <p className={styles.appearanceIntro}>Son adornos digitales ganados con hitos reales. No cuestan dinero ni consumen tus aceitunas.</p>

              <div className={styles.badgeGrid}>
                {data.appearance.options.map((option) => {
                  const selected = option.id === data.appearance.selected_badge;
                  return (
                    <button
                      key={option.id}
                      type="button"
                      className={`${styles.badgeButton} ${selected ? styles.badgeSelected : ''}`}
                      disabled={!option.unlocked || savingBadge !== null}
                      aria-pressed={selected}
                      onClick={() => void selectBadge(option.id)}
                    >
                      <span className={styles.badgeSymbol} aria-hidden="true">{option.symbol}</span>
                      <span className={styles.badgeCopy}>
                        <strong>{option.title}</strong>
                        <small>{option.unlocked ? (selected ? 'Activo' : 'Desbloqueado') : 'Bloqueado'}</small>
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          ) : null}
        </section>
      ) : null}
    </>
  );
}
