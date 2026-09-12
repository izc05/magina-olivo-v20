'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../lib/api-client';
import { useAuth } from './auth-provider';
import styles from './mi-olivo-dashboard.module.css';

type Mission = {
  id: string;
  title: string;
  detail: string;
  reward: number;
  completed: boolean;
  progress_current: number;
  progress_target: number;
};

type Achievement = {
  id: string;
  title: string;
  detail: string;
  unlocked: boolean;
};

type Reward = Achievement & {
  required_level: number;
};

type Rhythm = {
  active_weeks: number;
  grace_active: boolean;
  label: string;
  message: string;
};

type EarningAction = {
  id: string;
  title: string;
  detail: string;
  reward_label: string;
};

type LedgerEntry = {
  id: string;
  event_type: string;
  points: number;
  reason: string;
  created_at: string;
};

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
  pending_result_kg: number;
  total_cost_eur: number;
  accrued_income_eur: number;
  collected_income_eur: number;
  delivery_count: number;
  field_count: number;
};

type MiOlivoPayload = {
  enabled: boolean;
  rule_version: string;
  engagement_rule_version: string;
  balance: number;
  level: number;
  level_label: string;
  tree_stage: number;
  progress: { current: number; target: number; percent: number };
  rhythm: Rhythm;
  today: { earned: number; cap: number; remaining: number };
  weekly: { earned: number; goal: number; percent: number };
  earning_actions: EarningAction[];
  missions: Mission[];
  achievements: Achievement[];
  rewards: Reward[];
  recent: LedgerEntry[];
};

const OLIVE_POSITIONS = [
  [92, 128], [112, 102], [129, 143], [148, 91], [164, 121], [181, 98], [199, 132], [218, 105],
  [77, 151], [104, 165], [137, 167], [170, 154], [205, 161], [235, 143], [125, 78], [191, 74],
  [151, 137], [185, 139], [228, 124], [86, 111], [114, 126], [208, 87], [151, 72], [173, 82],
] as const;

function OliveTree({ stage, balance }: { stage: number; balance: number }) {
  const visibleOlives = Math.min(
    OLIVE_POSITIONS.length,
    balance > 0 ? Math.max(stage, Math.ceil(balance / 25)) : 0,
  );

  return (
    <svg className={styles.tree} viewBox="0 0 320 320" role="img" aria-label={`Olivo digital en fase ${stage} de 5`}>
      <defs>
        <linearGradient id="trunk" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#9a714c" />
          <stop offset="0.55" stopColor="#725238" />
          <stop offset="1" stopColor="#4e392c" />
        </linearGradient>
        <radialGradient id="crown" cx="45%" cy="35%" r="70%">
          <stop offset="0" stopColor="#b7c783" />
          <stop offset="0.55" stopColor="#78925b" />
          <stop offset="1" stopColor="#4b643c" />
        </radialGradient>
        <radialGradient id="oliveFruit" cx="32%" cy="28%" r="75%">
          <stop offset="0" stopColor="#9aaa56" />
          <stop offset="0.5" stopColor="#576735" />
          <stop offset="1" stopColor="#263221" />
        </radialGradient>
      </defs>
      <ellipse cx="160" cy="284" rx="90" ry="16" className={styles.shadow} />
      <path d="M142 278c14-42 13-73 10-104 17 15 28 39 31 70 11-30 25-50 43-65-16 29-25 61-27 99z" fill="url(#trunk)" />
      <path d="M155 203c-25-29-43-47-73-61M175 193c19-31 38-48 67-64M164 168c1-31-4-53-16-78M148 225c-16-14-31-22-48-28M185 221c17-13 34-22 52-28" className={styles.branch} />
      <g className={stage >= 1 ? styles.grown : styles.future}>
        <circle cx="151" cy="116" r="45" fill="url(#crown)" />
      </g>
      <g className={stage >= 2 ? styles.grown : styles.future}>
        <circle cx="104" cy="145" r="48" fill="url(#crown)" />
        <circle cx="213" cy="139" r="51" fill="url(#crown)" />
      </g>
      <g className={stage >= 3 ? styles.grown : styles.future}>
        <circle cx="118" cy="93" r="42" fill="url(#crown)" />
        <circle cx="202" cy="88" r="44" fill="url(#crown)" />
      </g>
      <g className={stage >= 4 ? styles.grown : styles.future}>
        <circle cx="72" cy="121" r="31" fill="url(#crown)" />
        <circle cx="248" cy="113" r="33" fill="url(#crown)" />
      </g>
      <g className={styles.fruitLayer}>
        {OLIVE_POSITIONS.map(([cx, cy], index) => (
          <circle
            key={`${cx}-${cy}`}
            cx={cx}
            cy={cy}
            r={index < visibleOlives ? 5 : 3.5}
            className={index < visibleOlives ? styles.olive : styles.futureOlive}
            fill={index < visibleOlives ? 'url(#oliveFruit)' : undefined}
          />
        ))}
      </g>
    </svg>
  );
}

function campaignStatusLabel(status: string) {
  if (status === 'active') return 'Campaña activa';
  if (status === 'closed') return 'Campaña cerrada';
  if (status === 'planned') return 'Campaña planificada';
  return 'Campaña';
}

function formatKg(value: number) {
  return new Intl.NumberFormat('es-ES', { maximumFractionDigits: 0 }).format(value);
}

function formatYield(value: number | null) {
  if (value === null) return 'Pendiente';
  return `${new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 }).format(value)} %`;
}

export function MiOlivoDashboard() {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [data, setData] = useState<MiOlivoPayload | null>(null);
  const [campaignSummary, setCampaignSummary] = useState<CampaignSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savingPreference, setSavingPreference] = useState(false);

  const load = useCallback(async () => {
    if (status !== 'authenticated' || !selectedWorkspaceId || !apiConfigured) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const payload = await apiFetch<MiOlivoPayload>('/api/v1/mi-olivo', { workspaceId: selectedWorkspaceId });
      setData(payload);

      try {
        const campaignPayload = await apiFetch<{ campaigns: Campaign[] }>('/api/v1/campaigns', { workspaceId: selectedWorkspaceId });
        const campaign = campaignPayload.campaigns.find((item) => item.status === 'active') ?? campaignPayload.campaigns[0] ?? null;
        if (campaign) {
          const summary = await apiFetch<CampaignSummary>(`/api/v1/campaigns/${campaign.id}/summary`, { workspaceId: selectedWorkspaceId });
          setCampaignSummary(summary);
        } else {
          setCampaignSummary(null);
        }
      } catch (campaignError) {
        console.warn('Unable to load campaign context for Mi Olivo', campaignError);
        setCampaignSummary(null);
      }
    } catch (loadError) {
      console.error('Unable to load Mi Olivo', loadError);
      setError('No hemos podido cargar tu olivo. Tus datos de campo no se han modificado.');
    } finally {
      setLoading(false);
    }
  }, [apiConfigured, selectedWorkspaceId, status]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    const refreshAfterAward = () => { void load(); };
    window.addEventListener('magina:mi-olivo-award', refreshAfterAward);
    return () => window.removeEventListener('magina:mi-olivo-award', refreshAfterAward);
  }, [load]);

  async function toggleEnabled() {
    if (!data || !selectedWorkspaceId) return;
    setSavingPreference(true);
    try {
      await apiFetch('/api/v1/mi-olivo/preferences', {
        method: 'PUT',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify({ enabled: !data.enabled }),
      });
      await load();
    } finally {
      setSavingPreference(false);
    }
  }

  if (loading) return <main className={styles.shell}><div className={styles.stateCard}>Preparando tu olivo…</div></main>;

  if (!apiConfigured || status !== 'authenticated') {
    return (
      <main className={styles.shell}>
        <section className={styles.stateCard}>
          <span className={styles.eyebrow}>MI OLIVO</span>
          <h1>Tu árbol crece con acciones reales</h1>
          <p>Inicia sesión para ver tu progreso. Esta pantalla no inventa puntos ni usa datos de demostración.</p>
          <Link href="/perfil" className={styles.primaryLink}>Ir a mi perfil</Link>
        </section>
      </main>
    );
  }

  if (error || !data) {
    return <main className={styles.shell}><div className={styles.stateCard}><h1>Mi Olivo</h1><p>{error ?? 'No hay datos disponibles.'}</p><button onClick={() => void load()}>Reintentar</button></div></main>;
  }

  const completedMissions = data.missions.filter((mission) => mission.completed).length;

  return (
    <main className={styles.shell}>
      <header className={styles.header}>
        <div>
          <span className={styles.eyebrow}>MI OLIVO</span>
          <h1>Tu olivo digital</h1>
          <p>Un reflejo visual de tu trabajo, tu organización y lo que vas descubriendo en Mágina.</p>
        </div>
        <Link href="/" className={styles.backLink}>Inicio</Link>
      </header>

      <section className={styles.hero} aria-labelledby="tree-title">
        <div className={styles.scene}>
          <div className={styles.skyGlow} aria-hidden="true" />
          <div className={styles.sun} aria-hidden="true" />
          <div className={styles.distantSierra} aria-hidden="true" />
          <div className={styles.nearSierra} aria-hidden="true" />
          <div className={styles.ground} aria-hidden="true" />
          <span className={styles.sceneLabel}>SIERRA MÁGINA · TU PROGRESO</span>
          <div className={styles.treePanel}>
            <OliveTree stage={data.tree_stage} balance={data.balance} />
          </div>
          <div className={styles.sceneStats}>
            <div><span>Esta semana</span><strong>{data.weekly.earned} 🫒</strong></div>
            <div><span>Ritmo</span><strong>{data.rhythm.active_weeks || '—'}{data.rhythm.active_weeks ? ' sem.' : ''}</strong></div>
          </div>
        </div>

        <div className={styles.heroCopy}>
          <div className={styles.levelRow}>
            <span className={styles.levelPill}>Nivel {data.level}</span>
            <span className={styles.stagePill}>Fase {data.tree_stage}/5</span>
          </div>
          <h2 id="tree-title">{data.level_label}</h2>
          <div className={styles.balance}><strong>{data.balance}</strong><span>aceitunas</span></div>
          <p className={styles.heroLead}>Cada fruto visible representa progreso acumulado. El saldo real sigue estando en tu ledger y nunca depende de recargar la web.</p>
          <div className={styles.progressLabel}><span>Próximo nivel</span><span>{data.progress.current}/{data.progress.target}</span></div>
          <div className={styles.progressTrack} aria-label={`${data.progress.percent}% hacia el siguiente nivel`}>
            <span style={{ width: `${data.progress.percent}%` }} />
          </div>
          <div className={styles.nextMilestone}>
            <span>Te faltan</span>
            <strong>{Math.max(0, data.progress.target - data.progress.current)} 🫒</strong>
            <span>para la siguiente etapa.</span>
          </div>
        </div>
      </section>

      {campaignSummary ? (
        <section className={styles.campaignCard} aria-label="Progreso real de la campaña">
          <div className={styles.campaignIntro}>
            <span className={styles.eyebrow}>{campaignStatusLabel(campaignSummary.campaign.status)}</span>
            <h2>{campaignSummary.campaign.name}</h2>
            <p>Contexto real de tu campaña agrícola. Estos datos vienen del registro de entregas y resultados, no de la gamificación.</p>
          </div>
          <div className={styles.campaignStats}>
            <div><span>Entregado</span><strong>{formatKg(campaignSummary.delivered_kg)} kg</strong></div>
            <div><span>Entregas</span><strong>{campaignSummary.delivery_count}</strong></div>
            <div><span>Fincas</span><strong>{campaignSummary.field_count}</strong></div>
            <div><span>Rendimiento</span><strong>{formatYield(campaignSummary.weighted_yield_percent)}</strong></div>
          </div>
          <Link href="/mi-campo/campana" className={styles.campaignLink}>Ver campaña completa →</Link>
        </section>
      ) : null}

      <section className={styles.weeklyPanel} aria-label="Progreso de Mi Olivo esta semana">
        <div className={styles.weeklyMain}>
          <div className={styles.sectionHeading}>
            <div><span className={styles.eyebrow}>ESTA SEMANA</span><h2>{data.weekly.earned} aceitunas conseguidas</h2></div>
            <strong>{data.weekly.earned}/{data.weekly.goal}</strong>
          </div>
          <div className={styles.progressTrack} aria-label={`${data.weekly.percent}% del objetivo semanal`}>
            <span style={{ width: `${data.weekly.percent}%` }} />
          </div>
          <p>El objetivo semanal sirve de guía, no caduca tu saldo ni te penaliza si una semana haces menos.</p>
        </div>
        <div className={styles.todayCard}>
          <span className={styles.eyebrow}>EXPLORACIÓN ÚTIL HOY</span>
          <strong>{data.today.earned}/{data.today.cap}</strong>
          <span>{data.today.remaining > 0 ? `Aún puedes sumar hasta ${data.today.remaining} aceitunas explorando contenido útil.` : 'Límite diario alcanzado. Mañana habrá nuevas oportunidades.'}</span>
        </div>
      </section>

      <section className={styles.notice}>
        <strong>Ritmo del cuaderno · {data.rhythm.label}</strong>
        <span>{data.rhythm.message}</span>
      </section>

      {!data.enabled && (
        <section className={styles.notice}>
          <strong>Mi Olivo está pausado.</strong>
          <span>Tu historial se conserva, pero no se reconocen nuevos hitos mientras esté pausado.</span>
        </section>
      )}

      <section className={styles.section}>
        <div className={styles.sectionHeading}>
          <div><span className={styles.eyebrow}>CÓMO CRECE</span><h2>Haz crecer tu olivo</h2></div>
          <span>Acciones útiles</span>
        </div>
        <div className={styles.cardGrid}>
          {data.earning_actions.map((action) => (
            <article key={action.id} className={styles.earningCard}>
              <span className={styles.earningIcon}>🫒</span>
              <div>
                <h3>{action.title}</h3>
                <p>{action.detail}</p>
              </div>
              <span className={styles.reward}>{action.reward_label}</span>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <div className={styles.sectionHeading}>
          <div><span className={styles.eyebrow}>MISIONES</span><h2>Pequeños pasos útiles</h2></div>
          <span>{completedMissions}/{data.missions.length}</span>
        </div>
        <div className={styles.cardGrid}>
          {data.missions.map((mission) => (
            <article key={mission.id} className={`${styles.missionCard} ${mission.completed ? styles.done : ''}`}>
              <div className={styles.cardIcon}>{mission.completed ? '✓' : '○'}</div>
              <div>
                <h3>{mission.title}</h3>
                <p>{mission.detail}</p>
                {mission.progress_target > 1 && (
                  <>
                    <div className={styles.progressLabel}><span>Progreso</span><span>{mission.progress_current}/{mission.progress_target}</span></div>
                    <div className={styles.progressTrack} aria-label={`${mission.progress_current} de ${mission.progress_target} en ${mission.title}`}>
                      <span style={{ width: `${Math.min(100, (mission.progress_current / mission.progress_target) * 100)}%` }} />
                    </div>
                  </>
                )}
              </div>
              <span className={styles.reward}>+{mission.reward}</span>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <div className={styles.sectionHeading}><div><span className={styles.eyebrow}>LOGROS</span><h2>Tu camino</h2></div></div>
        <div className={styles.achievementGrid}>
          {data.achievements.map((achievement) => (
            <article key={achievement.id} className={`${styles.achievement} ${achievement.unlocked ? styles.unlocked : ''}`}>
              <span className={styles.medal}>{achievement.unlocked ? '✦' : '·'}</span>
              <h3>{achievement.title}</h3>
              <p>{achievement.detail}</p>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <div className={styles.sectionHeading}><div><span className={styles.eyebrow}>DESBLOQUEOS</span><h2>Distintivos digitales</h2></div></div>
        <div className={styles.achievementGrid}>
          {data.rewards.map((reward) => (
            <article key={reward.id} className={`${styles.achievement} ${reward.unlocked ? styles.unlocked : ''}`}>
              <span className={styles.medal}>{reward.unlocked ? '✦' : reward.required_level}</span>
              <h3>{reward.title}</h3>
              <p>{reward.unlocked ? reward.detail : `Se desbloquea en el nivel ${reward.required_level}.`}</p>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <div className={styles.sectionHeading}><div><span className={styles.eyebrow}>HISTORIAL</span><h2>Por qué ha crecido</h2></div></div>
        {data.recent.length ? (
          <div className={styles.ledger}>
            {data.recent.map((entry) => (
              <div key={entry.id} className={styles.ledgerRow}>
                <div><strong>{entry.reason}</strong><time dateTime={entry.created_at}>{new Date(entry.created_at).toLocaleDateString('es-ES')}</time></div>
                <span className={entry.points > 0 ? styles.positive : styles.negative}>{entry.points > 0 ? '+' : ''}{entry.points}</span>
              </div>
            ))}
          </div>
        ) : <p className={styles.empty}>Aún no hay hitos reconocidos. Empieza por una misión útil.</p>}
      </section>

      <section className={styles.settingsCard}>
        <div><strong>Participación voluntaria</strong><p>Mi Olivo no bloquea ninguna función de Mágina y puedes pausarlo cuando quieras.</p></div>
        <button type="button" onClick={() => void toggleEnabled()} disabled={savingPreference}>{savingPreference ? 'Guardando…' : data.enabled ? 'Pausar Mi Olivo' : 'Activar Mi Olivo'}</button>
      </section>
    </main>
  );
}
