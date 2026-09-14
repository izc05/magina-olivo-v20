'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
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

type LevelReward = Achievement & {
  required_level: number;
};

type Level = {
  level: number;
  slug: string;
  name: string;
  min_xp: number;
  tree_stage: number;
  badge_title: string;
  description: string;
  unlocked: boolean;
};

type LedgerEntry = {
  id: string;
  event_type: string;
  points: number;
  reason: string;
  created_at: string;
};

type Payload = {
  enabled: boolean;
  balance: number;
  xp: number;
  level: number;
  level_label: string;
  tree_stage: number;
  next_level: { level: number; name: string; min_xp: number } | null;
  progress: { current: number; target: number; percent: number };
  rhythm: { active_weeks: number; grace_active: boolean; label: string; message: string };
  today: { earned: number; cap: number; remaining: number };
  weekly: { earned: number; goal: number; percent: number };
  earning_actions: Array<{ id: string; title: string; detail: string; reward_label: string }>;
  missions: Mission[];
  achievements: Achievement[];
  rewards: LevelReward[];
  levels: Level[];
  recent: LedgerEntry[];
};

const OLIVE_POSITIONS = [
  [92, 128], [112, 102], [129, 143], [148, 91], [164, 121], [181, 98], [199, 132], [218, 105],
  [77, 151], [104, 165], [137, 167], [170, 154], [205, 161], [235, 143], [125, 78], [191, 74],
  [151, 137], [185, 139], [228, 124], [86, 111], [114, 126], [208, 87], [151, 72], [173, 82],
  [62, 142], [250, 142], [95, 84], [218, 75], [137, 61], [180, 58], [71, 102], [245, 96],
] as const;

function OliveTree({ stage, xp, compact = false }: { stage: number; xp: number; compact?: boolean }) {
  const visibleOlives = Math.min(OLIVE_POSITIONS.length, xp > 0 ? Math.max(stage * 2, Math.ceil(xp / 110)) : 0);
  return (
    <svg className={`${styles.tree} ${compact ? styles.treeCompact : ''}`} viewBox="0 0 320 320" role="img" aria-label={`Olivo digital en fase ${stage} de 10`}>
      <defs>
        <linearGradient id={`progressTrunk-${stage}-${compact ? 'mini' : 'hero'}`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#b99469" />
          <stop offset="0.46" stopColor="#79583c" />
          <stop offset="1" stopColor="#433126" />
        </linearGradient>
        <radialGradient id={`progressCrown-${stage}-${compact ? 'mini' : 'hero'}`} cx="42%" cy="32%" r="72%">
          <stop offset="0" stopColor="#dce3aa" />
          <stop offset="0.42" stopColor="#8ca467" />
          <stop offset="0.78" stopColor="#587249" />
          <stop offset="1" stopColor="#344a32" />
        </radialGradient>
        <radialGradient id={`progressFruit-${stage}-${compact ? 'mini' : 'hero'}`} cx="30%" cy="24%" r="78%">
          <stop offset="0" stopColor="#d3d98b" />
          <stop offset="0.42" stopColor="#77833f" />
          <stop offset="1" stopColor="#263121" />
        </radialGradient>
      </defs>
      <ellipse cx="160" cy="284" rx="90" ry="16" className={styles.shadow} />
      <path d="M142 278c14-42 13-73 10-104 17 15 28 39 31 70 11-30 25-50 43-65-16 29-25 61-27 99z" fill={`url(#progressTrunk-${stage}-${compact ? 'mini' : 'hero'})`} />
      <path d="M155 203c-25-29-43-47-73-61M175 193c19-31 38-48 67-64M164 168c1-31-4-53-16-78M148 225c-16-14-31-22-48-28M185 221c17-13 34-22 52-28" className={styles.branch} />
      {[
        [1, 151, 125, 37], [2, 112, 145, 39], [3, 204, 142, 42], [4, 125, 100, 40], [5, 194, 96, 42],
        [6, 76, 137, 32], [7, 244, 130, 34], [8, 93, 98, 31], [9, 226, 88, 32], [10, 159, 72, 35],
      ].map(([required, cx, cy, radius]) => (
        <g key={required} className={stage >= required ? styles.grown : styles.future}>
          <circle cx={cx} cy={cy} r={radius} fill={`url(#progressCrown-${stage}-${compact ? 'mini' : 'hero'})`} />
        </g>
      ))}
      <g className={styles.fruitLayer}>
        {OLIVE_POSITIONS.map(([cx, cy], index) => (
          <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r={index < visibleOlives ? 5 : 3.5}
            className={index < visibleOlives ? styles.olive : styles.futureOlive}
            fill={index < visibleOlives ? `url(#progressFruit-${stage}-${compact ? 'mini' : 'hero'})` : undefined} />
        ))}
      </g>
    </svg>
  );
}

function progressWidth(value: number) {
  return `${Math.max(0, Math.min(100, value))}%`;
}

export function MiOlivoProgressionDashboard() {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [data, setData] = useState<Payload | null>(null);
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
      setData(await apiFetch<Payload>('/api/v1/mi-olivo', { workspaceId: selectedWorkspaceId }));
    } catch (cause) {
      console.error('Unable to load Mi Olivo progression', cause);
      setError('No hemos podido cargar tu olivo. Tus datos no se han modificado.');
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
  if (!apiConfigured || status !== 'authenticated') return <main className={styles.shell}><section className={styles.stateCard}>
    <span className={styles.eyebrow}>MI OLIVO</span><h1>Tu árbol crece con acciones reales</h1>
    <p>Inicia sesión para ver tu progreso, tus aceitunas y tus recompensas.</p><Link href="/perfil" className={styles.primaryLink}>Ir a mi perfil</Link>
  </section></main>;
  if (error || !data) return <main className={styles.shell}><div className={styles.stateCard}><h1>Mi Olivo</h1><p>{error ?? 'No hay datos disponibles.'}</p><button onClick={() => void load()}>Reintentar</button></div></main>;

  const completedMissions = data.missions.filter((mission) => mission.completed).length;
  const currentLevel = data.levels.find((level) => level.level === data.level);
  const nextLevel = data.next_level ? data.levels.find((level) => level.level === data.next_level?.level) : null;
  const xpMissing = data.next_level ? Math.max(0, data.next_level.min_xp - data.xp) : 0;
  const visibleLevels = useMemo(() => {
    const start = Math.max(0, Math.min(data.levels.length - 5, data.level - 3));
    return data.levels.slice(start, start + 5);
  }, [data.level, data.levels]);

  return <main className={styles.shell}>
    <header className={styles.mobileHeader}>
      <Link href="/" className={styles.mobileBack} aria-label="Volver al inicio">‹</Link>
      <div><span className={styles.leafMark}>❧</span><strong>Mi Olivo</strong></div>
      <span className={styles.mobileMenu} aria-hidden="true">⋮</span>
    </header>

    <header className={styles.header}>
      <div><span className={styles.eyebrow}>MI OLIVO · SIERRA MÁGINA</span><h1>Mi Olivo</h1><p>Tu historia, nuestras raíces. Un olivo digital que conserva tu progreso y conecta tus aceitunas con recompensas reales del territorio.</p></div>
      <Link href="/mi-campo" className={styles.fieldLink}>🌱 Ver mi campo <span>→</span></Link>
    </header>

    <nav className={styles.tabs} aria-label="Secciones de Mi Olivo">
      <a href="#resumen" className={styles.activeTab}>Resumen</a>
      <a href="#misiones">Cuidados</a>
      <a href="#historial">Historia</a>
      <a href="#recompensas">Recompensas</a>
    </nav>

    <section id="resumen" className={styles.hero} aria-labelledby="tree-title">
      <div className={styles.scene}>
        <div className={styles.sceneShade} aria-hidden="true" />
        <span className={styles.sceneLabel}>SIERRA MÁGINA · TU PROGRESO</span>
        <div className={styles.treeAura} aria-hidden="true" />
        <div className={styles.treePanel}><OliveTree stage={data.tree_stage} xp={data.xp} /></div>
        <p className={`${styles.editorialLine} ${styles.editorialLeft}`}>Raíces que conectan personas</p>
        <p className={`${styles.editorialLine} ${styles.editorialRight}`}>Un futuro más verde</p>
        <div className={styles.heroLevelCard}>
          <span>Nivel {data.level}</span>
          <strong id="tree-title">{data.level_label}</strong>
          <div className={styles.progressTrack}><span style={{ width: progressWidth(data.progress.percent) }} /></div>
          <small>{data.progress.current} / {data.progress.target} XP</small>
        </div>
        <div className={styles.sceneStats}>
          <div><span>🫒</span><strong>{data.balance}</strong><small>Aceitunas</small><em>+{data.weekly.earned} esta semana</em></div>
          <div><span>▥</span><strong>{data.today.earned}</strong><small>Hoy</small><em>de {data.today.cap}</em></div>
          <div><span>🌱</span><strong>{data.rhythm.active_weeks || 0}</strong><small>Semanas</small><em>{data.rhythm.label}</em></div>
          <div><span>🏆</span><strong>{data.achievements.filter((item) => item.unlocked).length}</strong><small>Logros</small><em>Conseguidos</em></div>
        </div>
      </div>

      <div className={styles.summaryPane}>
        <article className={styles.currentLevelCard}>
          <div className={styles.currentTree}><OliveTree stage={data.tree_stage} xp={data.xp} compact /></div>
          <div className={styles.currentCopy}>
            <span className={styles.eyebrow}>TU OLIVO AHORA</span>
            <h2>Nivel {data.level} · {data.level_label}</h2>
            <p className={styles.levelDescription}>{currentLevel?.description ?? 'Tu olivo conserva la memoria de tu progreso en Mágina.'}</p>
            <div className={styles.progressTrack}><span style={{ width: progressWidth(data.progress.percent) }} /></div>
            <div className={styles.progressFoot}><strong>{data.progress.current} / {data.progress.target} XP</strong><span>{data.next_level ? `Faltan ${xpMissing}` : 'Nivel máximo'}</span></div>
          </div>
          {nextLevel ? <div className={styles.nextMini}><OliveTree stage={nextLevel.tree_stage} xp={nextLevel.min_xp} compact /><span>Nivel {nextLevel.level}</span><strong>{nextLevel.name}</strong></div> : null}
        </article>

        {nextLevel ? <article id="proximo-nivel" className={styles.nextLevelCard}>
          <div className={styles.sectionTitle}><div><span className={styles.eyebrow}>PRÓXIMO NIVEL</span><h2>{nextLevel.name}</h2></div><span>{nextLevel.min_xp} XP</span></div>
          <div className={styles.nextLevelBody}>
            <div className={styles.nextTree}><OliveTree stage={nextLevel.tree_stage} xp={nextLevel.min_xp} compact /></div>
            <div><strong>Nivel {nextLevel.level} · {nextLevel.name}</strong><p>{nextLevel.description}</p><ul><li>Más posibilidades de recompensa</li><li>{nextLevel.badge_title}</li><li>Tu olivo evoluciona sin perder XP</li></ul></div>
          </div>
        </article> : null}

        <article id="evolucion" className={styles.evolutionCard}>
          <div className={styles.sectionTitle}><div><span className={styles.eyebrow}>EVOLUCIÓN</span><h2>La vida de tu olivo</h2></div><span>{data.level}/10</span></div>
          <div className={styles.levelTimeline}>
            {visibleLevels.map((level) => <div key={level.level} className={`${styles.levelStage} ${level.level === data.level ? styles.levelStageCurrent : ''} ${level.unlocked ? styles.levelStageUnlocked : ''}`}>
              <div className={styles.levelTree}><OliveTree stage={level.tree_stage} xp={Math.max(level.min_xp, data.xp)} compact /></div>
              <strong>{level.name}</strong><span>N.{level.level}</span>
            </div>)}
          </div>
          <a href="#niveles" className={styles.inlineLink}>Ver todos los niveles →</a>
        </article>
      </div>
    </section>

    <section id="recompensas" className={styles.rewardShowcase}>
      <div className={styles.sectionTitle}><div><span className={styles.eyebrow}>TUS RECOMPENSAS</span><h2>Del olivo al territorio</h2></div><Link href="/almazaras">Ver catálogo →</Link></div>
      <div className={styles.rewardScroller}>
        {data.rewards.slice(0, 3).map((reward, index) => <article key={reward.id} className={styles.rewardCard}>
          <div className={`${styles.rewardThumb} ${styles[`rewardThumb${index + 1}`] ?? ''}`}><span>{reward.unlocked ? 'Disponible' : `Nivel ${reward.required_level}`}</span></div>
          <strong>{reward.title}</strong><p>{reward.detail}</p><small>{reward.unlocked ? 'Listo para explorar' : `Se desbloquea en nivel ${reward.required_level}`}</small>
        </article>)}
        {!data.rewards.length ? <article className={styles.rewardEmpty}><strong>Catálogo AOVE</strong><p>Las recompensas aparecerán aquí cuando estén publicadas para tu cuenta.</p><Link href="/almazaras">Explorar almazaras →</Link></article> : null}
      </div>
      <div className={styles.rewardActions}><Link href="/almazaras" className={styles.primaryAction}>Explorar premios</Link><Link href="/mi-olivo/canjes" className={styles.secondaryAction}>Mis canjes</Link></div>
    </section>

    <section className={styles.weeklyPanel} aria-label="Progreso de Mi Olivo esta semana">
      <div className={styles.weeklyMain}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>ESTA SEMANA</span><h2>{data.weekly.earned} aceitunas conseguidas</h2></div><strong>{data.weekly.earned}/{data.weekly.goal}</strong></div><div className={styles.progressTrack}><span style={{ width: progressWidth(data.weekly.percent) }} /></div><p>Tu objetivo semanal orienta el progreso; descansar no hace caducar el saldo ni reduce el XP acumulado.</p></div>
      <div className={styles.todayCard}><span className={styles.eyebrow}>HOY</span><strong>{data.today.earned}/{data.today.cap}</strong><span>{data.today.remaining > 0 ? `Aún puedes sumar ${data.today.remaining} aceitunas.` : 'Límite diario alcanzado.'}</span></div>
    </section>

    <section className={styles.quoteCard}><span>❧</span><blockquote>“Cuidar un olivo es cuidar también de lo que viene.”</blockquote></section>

    <section className={styles.notice}><strong>Ritmo del cuaderno · {data.rhythm.label}</strong><span>{data.rhythm.message}</span></section>
    {!data.enabled ? <section className={styles.notice}><strong>Mi Olivo está pausado.</strong><span>Tu historial se conserva, pero no se reconocen nuevos hitos mientras esté pausado.</span></section> : null}

    <section id="misiones" className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>CÓMO CRECE</span><h2>Acciones que cuidan tu progreso</h2></div><span>Acciones útiles</span></div><div className={styles.cardGrid}>{data.earning_actions.map((action) => <article key={action.id} className={styles.earningCard}><span className={styles.earningIcon}>🫒</span><div><h3>{action.title}</h3><p>{action.detail}</p></div><span className={styles.reward}>{action.reward_label}</span></article>)}</div></section>

    <section className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>MISIONES</span><h2>Pequeños pasos útiles</h2></div><span>{completedMissions}/{data.missions.length}</span></div><div className={styles.cardGrid}>{data.missions.map((mission) => <article key={mission.id} className={`${styles.missionCard} ${mission.completed ? styles.done : ''}`}><div className={styles.cardIcon}>{mission.completed ? '✓' : '○'}</div><div><h3>{mission.title}</h3><p>{mission.detail}</p>{mission.progress_target > 1 ? <><div className={styles.progressLabel}><span>Progreso</span><span>{mission.progress_current}/{mission.progress_target}</span></div><div className={styles.progressTrack}><span style={{ width: progressWidth((mission.progress_current / mission.progress_target) * 100) }} /></div></> : null}</div><span className={styles.reward}>+{mission.reward}</span></article>)}</div></section>

    <section className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>LOGROS</span><h2>Tu colección</h2></div></div><div className={styles.achievementGrid}>{data.achievements.map((achievement) => <article key={achievement.id} className={`${styles.achievement} ${achievement.unlocked ? styles.unlocked : ''}`}><span className={styles.medal}>{achievement.unlocked ? '✦' : '·'}</span><h3>{achievement.title}</h3><p>{achievement.detail}</p></article>)}</div></section>

    <section id="niveles" className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>LOS 10 NIVELES</span><h2>Evolución completa del olivo</h2></div><span>{data.level}/10</span></div><div className={styles.achievementGrid}>{data.levels.map((level) => <article key={level.level} className={`${styles.achievement} ${level.unlocked ? styles.unlocked : ''}`}><span className={styles.medal}>{level.unlocked ? '✦' : level.level}</span><h3>{level.name}</h3><p>{level.unlocked ? level.description : `${level.min_xp} XP · ${level.badge_title}`}</p></article>)}</div></section>

    <section id="historial" className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>HISTORIA</span><h2>Movimientos de aceitunas</h2></div></div>{data.recent.length ? <div className={styles.ledger}>{data.recent.map((entry) => <div key={entry.id} className={styles.ledgerRow}><div><strong>{entry.reason}</strong><time dateTime={entry.created_at}>{new Date(entry.created_at).toLocaleDateString('es-ES')}</time></div><span className={entry.points > 0 ? styles.positive : styles.negative}>{entry.points > 0 ? '+' : ''}{entry.points}</span></div>)}</div> : <p className={styles.empty}>Aún no hay movimientos. Empieza por una misión útil.</p>}</section>

    <section className={styles.settingsCard}><div><strong>Participación voluntaria</strong><p>Mi Olivo no es dinero, no es transferible y no usa blockchain. Puedes pausarlo sin perder tu historial.</p></div><button type="button" onClick={() => void toggleEnabled()} disabled={savingPreference}>{savingPreference ? 'Guardando…' : data.enabled ? 'Pausar Mi Olivo' : 'Activar Mi Olivo'}</button></section>
  </main>;
}
