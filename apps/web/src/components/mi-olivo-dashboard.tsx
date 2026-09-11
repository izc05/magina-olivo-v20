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
};

type Achievement = {
  id: string;
  title: string;
  detail: string;
  unlocked: boolean;
};

type LedgerEntry = {
  id: string;
  event_type: string;
  points: number;
  reason: string;
  created_at: string;
};

type MiOlivoPayload = {
  enabled: boolean;
  rule_version: string;
  balance: number;
  level: number;
  level_label: string;
  tree_stage: number;
  progress: { current: number; target: number; percent: number };
  missions: Mission[];
  achievements: Achievement[];
  recent: LedgerEntry[];
};

function OliveTree({ stage }: { stage: number }) {
  return (
    <svg className={styles.tree} viewBox="0 0 320 320" role="img" aria-label={`Olivo digital en fase ${stage} de 5`}>
      <defs>
        <linearGradient id="trunk" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#8c6746" />
          <stop offset="1" stopColor="#5d4635" />
        </linearGradient>
        <radialGradient id="crown" cx="45%" cy="35%" r="70%">
          <stop offset="0" stopColor="#a4b872" />
          <stop offset="1" stopColor="#5f7546" />
        </radialGradient>
      </defs>
      <ellipse cx="160" cy="282" rx="82" ry="15" className={styles.shadow} />
      <path d="M144 276c15-42 11-77 8-107 18 20 27 42 29 74 10-29 23-49 42-64-16 28-25 58-27 97z" fill="url(#trunk)" />
      <path d="M154 198c-23-27-39-43-66-58M174 190c17-29 35-46 65-62M164 164c2-29-3-49-14-72" className={styles.branch} />
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
      <g className={stage >= 5 ? styles.grown : styles.future}>
        {[91, 126, 164, 198, 229].map((x, index) => <circle key={x} cx={x} cy={index % 2 ? 105 : 135} r="5" className={styles.olive} />)}
      </g>
    </svg>
  );
}

export function MiOlivoDashboard() {
  const { status, selectedWorkspaceId, apiConfigured } = useAuth();
  const [data, setData] = useState<MiOlivoPayload | null>(null);
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
    } catch (loadError) {
      console.error('Unable to load Mi Olivo', loadError);
      setError('No hemos podido cargar tu olivo. Tus datos de campo no se han modificado.');
    } finally {
      setLoading(false);
    }
  }, [apiConfigured, selectedWorkspaceId, status]);

  useEffect(() => { void load(); }, [load]);

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
          <p>Crece cuando organizas de verdad tu actividad en Mágina.</p>
        </div>
        <Link href="/" className={styles.backLink}>Inicio</Link>
      </header>

      <section className={styles.hero} aria-labelledby="tree-title">
        <div className={styles.treePanel}>
          <OliveTree stage={data.tree_stage} />
        </div>
        <div className={styles.heroCopy}>
          <span className={styles.levelPill}>Nivel {data.level}</span>
          <h2 id="tree-title">{data.level_label}</h2>
          <div className={styles.balance}><strong>{data.balance}</strong><span>aceitunas</span></div>
          <div className={styles.progressLabel}><span>Próximo nivel</span><span>{data.progress.current}/{data.progress.target}</span></div>
          <div className={styles.progressTrack} aria-label={`${data.progress.percent}% hacia el siguiente nivel`}>
            <span style={{ width: `${data.progress.percent}%` }} />
          </div>
          <p className={styles.helper}>Los puntos proceden de hitos verificables. Repetir una acción no genera puntos ilimitados.</p>
        </div>
      </section>

      {!data.enabled && (
        <section className={styles.notice}>
          <strong>Mi Olivo está pausado.</strong>
          <span>Tu historial se conserva, pero no se reconocen nuevos hitos mientras esté pausado.</span>
        </section>
      )}

      <section className={styles.section}>
        <div className={styles.sectionHeading}>
          <div><span className={styles.eyebrow}>MISIONES</span><h2>Pequeños pasos útiles</h2></div>
          <span>{completedMissions}/{data.missions.length}</span>
        </div>
        <div className={styles.cardGrid}>
          {data.missions.map((mission) => (
            <article key={mission.id} className={`${styles.missionCard} ${mission.completed ? styles.done : ''}`}>
              <div className={styles.cardIcon}>{mission.completed ? '✓' : '○'}</div>
              <div><h3>{mission.title}</h3><p>{mission.detail}</p></div>
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
