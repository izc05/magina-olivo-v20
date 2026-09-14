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

function OliveTree({ stage, xp }: { stage: number; xp: number }) {
  const visibleOlives = Math.min(OLIVE_POSITIONS.length, xp > 0 ? Math.max(stage * 2, Math.ceil(xp / 110)) : 0);
  return (
    <svg className={styles.tree} viewBox="0 0 320 320" role="img" aria-label={`Olivo digital en fase ${stage} de 10`}>
      <defs>
        <linearGradient id="progressTrunk" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#9a714c" />
          <stop offset="0.55" stopColor="#725238" />
          <stop offset="1" stopColor="#4e392c" />
        </linearGradient>
        <radialGradient id="progressCrown" cx="45%" cy="35%" r="70%">
          <stop offset="0" stopColor="#c2d08e" />
          <stop offset="0.55" stopColor="#78925b" />
          <stop offset="1" stopColor="#405b38" />
        </radialGradient>
        <radialGradient id="progressFruit" cx="32%" cy="28%" r="75%">
          <stop offset="0" stopColor="#aeba62" />
          <stop offset="0.5" stopColor="#576735" />
          <stop offset="1" stopColor="#263221" />
        </radialGradient>
      </defs>
      <ellipse cx="160" cy="284" rx="90" ry="16" className={styles.shadow} />
      <path d="M142 278c14-42 13-73 10-104 17 15 28 39 31 70 11-30 25-50 43-65-16 29-25 61-27 99z" fill="url(#progressTrunk)" />
      <path d="M155 203c-25-29-43-47-73-61M175 193c19-31 38-48 67-64M164 168c1-31-4-53-16-78M148 225c-16-14-31-22-48-28M185 221c17-13 34-22 52-28" className={styles.branch} />
      <g className={stage >= 1 ? styles.grown : styles.future}><circle cx="151" cy="125" r="37" fill="url(#progressCrown)" /></g>
      <g className={stage >= 2 ? styles.grown : styles.future}><circle cx="112" cy="145" r="39" fill="url(#progressCrown)" /></g>
      <g className={stage >= 3 ? styles.grown : styles.future}><circle cx="204" cy="142" r="42" fill="url(#progressCrown)" /></g>
      <g className={stage >= 4 ? styles.grown : styles.future}><circle cx="125" cy="100" r="40" fill="url(#progressCrown)" /></g>
      <g className={stage >= 5 ? styles.grown : styles.future}><circle cx="194" cy="96" r="42" fill="url(#progressCrown)" /></g>
      <g className={stage >= 6 ? styles.grown : styles.future}><circle cx="76" cy="137" r="32" fill="url(#progressCrown)" /></g>
      <g className={stage >= 7 ? styles.grown : styles.future}><circle cx="244" cy="130" r="34" fill="url(#progressCrown)" /></g>
      <g className={stage >= 8 ? styles.grown : styles.future}><circle cx="93" cy="98" r="31" fill="url(#progressCrown)" /></g>
      <g className={stage >= 9 ? styles.grown : styles.future}><circle cx="226" cy="88" r="32" fill="url(#progressCrown)" /></g>
      <g className={stage >= 10 ? styles.grown : styles.future}>
        <circle cx="159" cy="72" r="35" fill="url(#progressCrown)" />
        <circle cx="160" cy="119" r="106" fill="none" stroke="rgba(214,197,132,.55)" strokeWidth="3" />
      </g>
      <g className={styles.fruitLayer}>
        {OLIVE_POSITIONS.map(([cx, cy], index) => (
          <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r={index < visibleOlives ? 5 : 3.5}
            className={index < visibleOlives ? styles.olive : styles.futureOlive}
            fill={index < visibleOlives ? 'url(#progressFruit)' : undefined} />
        ))}
      </g>
    </svg>
  );
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
  const xpMissing = data.next_level ? Math.max(0, data.next_level.min_xp - data.xp) : 0;

  return <main className={styles.shell}>
    <header className={styles.header}>
      <div><span className={styles.eyebrow}>MI OLIVO · PROGRESIÓN</span><h1>Tu olivo digital</h1><p>Crece con XP permanente y usa tus aceitunas para conseguir recompensas reales de Sierra Mágina.</p></div>
      <Link href="/" className={styles.backLink}>Inicio</Link>
    </header>

    <section className={styles.hero} aria-labelledby="tree-title">
      <div className={styles.scene}>
        <div className={styles.skyGlow} aria-hidden="true" /><div className={styles.sun} aria-hidden="true" />
        <div className={styles.distantSierra} aria-hidden="true" /><div className={styles.nearSierra} aria-hidden="true" /><div className={styles.ground} aria-hidden="true" />
        <span className={styles.sceneLabel}>SIERRA MÁGINA · XP PERMANENTE</span>
        <div className={styles.treePanel}><OliveTree stage={data.tree_stage} xp={data.xp} /></div>
        <div className={styles.sceneStats}>
          <div><span>XP histórico</span><strong>{data.xp} XP</strong></div>
          <div><span>Esta semana</span><strong>{data.weekly.earned} 🫒</strong></div>
        </div>
      </div>
      <div className={styles.heroCopy}>
        <div className={styles.levelRow}><span className={styles.levelPill}>Nivel {data.level}</span><span className={styles.stagePill}>Fase {data.tree_stage}/10</span></div>
        <h2 id="tree-title">{data.level_label}</h2>
        <div className={styles.balance}><strong>{data.balance}</strong><span>aceitunas disponibles</span></div>
        <p className={styles.heroLead}>Las aceitunas son saldo interno de fidelización y pueden gastarse. Tu XP es histórico: al canjear un premio el olivo nunca pierde nivel ni retrocede.</p>
        <div className={styles.progressLabel}><span>{data.next_level ? `Hacia ${data.next_level.name}` : 'Nivel máximo'}</span><span>{data.progress.current}/{data.progress.target} XP</span></div>
        <div className={styles.progressTrack} aria-label={`${data.progress.percent}% hacia el siguiente nivel`}><span style={{ width: `${data.progress.percent}%` }} /></div>
        <div className={styles.nextMilestone}>{data.next_level ? <><span>Te faltan</span><strong>{xpMissing} XP</strong><span>para el nivel {data.next_level.level}.</span></> : <><strong>Nivel máximo alcanzado</strong><span>Tu XP puede seguir creciendo.</span></>}</div>
      </div>
    </section>

    <section className={styles.campaignCard} aria-label="Recompensas reales de Mi Olivo">
      <div className={styles.campaignIntro}><span className={styles.eyebrow}>RECOMPENSAS AOVE</span><h2>Del progreso al territorio</h2><p>Usa tus aceitunas para reservar botellas y otros premios publicados por almazaras reales. Cada reserva genera un QR firmado y de un solo uso.</p></div>
      <div className={styles.campaignStats}>
        <div><span>Saldo disponible</span><strong>{data.balance} 🫒</strong></div>
        <div><span>Nivel</span><strong>{data.level}/10</strong></div>
        <div><span>XP histórico</span><strong>{data.xp}</strong></div>
        <div><span>Misiones</span><strong>{completedMissions}/{data.missions.length}</strong></div>
      </div>
      <div><Link href="/almazaras" className={styles.campaignLink}>Ver premios →</Link><br /><Link href="/mi-olivo/canjes" className={styles.campaignLink}>Mis canjes →</Link></div>
    </section>

    <section className={styles.weeklyPanel} aria-label="Progreso de Mi Olivo esta semana">
      <div className={styles.weeklyMain}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>ESTA SEMANA</span><h2>{data.weekly.earned} aceitunas conseguidas</h2></div><strong>{data.weekly.earned}/{data.weekly.goal}</strong></div><div className={styles.progressTrack} aria-label={`${data.weekly.percent}% del objetivo semanal`}><span style={{ width: `${data.weekly.percent}%` }} /></div><p>El objetivo semanal sirve de guía. Tu saldo no caduca por descansar y el XP ya ganado no disminuye.</p></div>
      <div className={styles.todayCard}><span className={styles.eyebrow}>EXPLORACIÓN ÚTIL HOY</span><strong>{data.today.earned}/{data.today.cap}</strong><span>{data.today.remaining > 0 ? `Aún puedes sumar hasta ${data.today.remaining} aceitunas.` : 'Límite diario alcanzado.'}</span></div>
    </section>

    <section className={styles.notice}><strong>Ritmo del cuaderno · {data.rhythm.label}</strong><span>{data.rhythm.message}</span></section>
    {!data.enabled ? <section className={styles.notice}><strong>Mi Olivo está pausado.</strong><span>Tu historial se conserva, pero no se reconocen nuevos hitos mientras esté pausado.</span></section> : null}

    <section className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>CÓMO CRECE</span><h2>Gana aceitunas y XP</h2></div><span>Acciones útiles</span></div><div className={styles.cardGrid}>{data.earning_actions.map((action) => <article key={action.id} className={styles.earningCard}><span className={styles.earningIcon}>🫒</span><div><h3>{action.title}</h3><p>{action.detail}</p></div><span className={styles.reward}>{action.reward_label}</span></article>)}</div></section>

    <section className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>MISIONES</span><h2>Pequeños pasos útiles</h2></div><span>{completedMissions}/{data.missions.length}</span></div><div className={styles.cardGrid}>{data.missions.map((mission) => <article key={mission.id} className={`${styles.missionCard} ${mission.completed ? styles.done : ''}`}><div className={styles.cardIcon}>{mission.completed ? '✓' : '○'}</div><div><h3>{mission.title}</h3><p>{mission.detail}</p>{mission.progress_target > 1 ? <><div className={styles.progressLabel}><span>Progreso</span><span>{mission.progress_current}/{mission.progress_target}</span></div><div className={styles.progressTrack}><span style={{ width: `${Math.min(100, (mission.progress_current / mission.progress_target) * 100)}%` }} /></div></> : null}</div><span className={styles.reward}>+{mission.reward}</span></article>)}</div></section>

    <section className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>LOGROS</span><h2>Tu colección</h2></div></div><div className={styles.achievementGrid}>{data.achievements.map((achievement) => <article key={achievement.id} className={`${styles.achievement} ${achievement.unlocked ? styles.unlocked : ''}`}><span className={styles.medal}>{achievement.unlocked ? '✦' : '·'}</span><h3>{achievement.title}</h3><p>{achievement.detail}</p></article>)}</div></section>

    <section className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>LOS 10 NIVELES</span><h2>Evolución del olivo</h2></div><span>{data.level}/10</span></div><div className={styles.achievementGrid}>{data.levels.map((level) => <article key={level.level} className={`${styles.achievement} ${level.unlocked ? styles.unlocked : ''}`}><span className={styles.medal}>{level.unlocked ? '✦' : level.level}</span><h3>{level.name}</h3><p>{level.unlocked ? level.description : `${level.min_xp} XP · ${level.badge_title}`}</p></article>)}</div></section>

    <section className={styles.section}><div className={styles.sectionHeading}><div><span className={styles.eyebrow}>HISTORIAL</span><h2>Movimientos de aceitunas</h2></div></div>{data.recent.length ? <div className={styles.ledger}>{data.recent.map((entry) => <div key={entry.id} className={styles.ledgerRow}><div><strong>{entry.reason}</strong><time dateTime={entry.created_at}>{new Date(entry.created_at).toLocaleDateString('es-ES')}</time></div><span className={entry.points > 0 ? styles.positive : styles.negative}>{entry.points > 0 ? '+' : ''}{entry.points}</span></div>)}</div> : <p className={styles.empty}>Aún no hay movimientos. Empieza por una misión útil.</p>}</section>

    <section className={styles.settingsCard}><div><strong>Participación voluntaria</strong><p>Mi Olivo no es dinero, no es transferible y no usa blockchain. Puedes pausarlo sin perder tu historial.</p></div><button type="button" onClick={() => void toggleEnabled()} disabled={savingPreference}>{savingPreference ? 'Guardando…' : data.enabled ? 'Pausar Mi Olivo' : 'Activar Mi Olivo'}</button></section>
  </main>;
}
