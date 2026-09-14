'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '../../lib/api-client';
import styles from './adventure.module.css';

type TerritoryProfile = {
  summary: {
    total_score: number;
  };
  journey: {
    completed_routes: number;
    completed_distance_m: number;
  };
  recorded: {
    activity_count: number;
    recorded_distance_m: number;
  };
  territory: {
    available_checkpoints: number;
    unlocked_checkpoints: number;
    explored_percent: number;
    municipalities_available: number;
    municipalities_discovered: number;
    municipalities: Array<{
      municipality_id: string;
      municipality_name: string;
      municipality_slug: string;
      adventure_count: number;
      available: number;
      unlocked: number;
      percent: number;
    }>;
  };
};

const EXPLORER_LEVELS = [
  { level: 1, minXp: 0, name: 'Caminante de Mágina' },
  { level: 2, minXp: 250, name: 'Rastreador del Olivar' },
  { level: 3, minXp: 500, name: 'Descubridor de Senderos' },
  { level: 4, minXp: 1_000, name: 'Explorador de Mágina' },
  { level: 5, minXp: 1_500, name: 'Cartógrafo de la Sierra' },
  { level: 6, minXp: 2_250, name: 'Aventurero de Mágina' },
  { level: 7, minXp: 3_000, name: 'Conquistador de Cumbres' },
  { level: 8, minXp: 4_000, name: 'Maestro del Sendero' },
  { level: 9, minXp: 5_000, name: 'Guardián de Mágina' },
  { level: 10, minXp: 6_500, name: 'Leyenda de Sierra Mágina' },
] as const;

type ExplorerLevel = (typeof EXPLORER_LEVELS)[number];

function explorerLevel(totalXp: number) {
  let current: ExplorerLevel = EXPLORER_LEVELS[0];
  for (const candidate of EXPLORER_LEVELS) {
    if (totalXp >= candidate.minXp) current = candidate;
  }
  const next = EXPLORER_LEVELS.find((candidate) => candidate.level === current.level + 1) ?? null;
  const progressXp = Math.max(0, totalXp - current.minXp);
  const targetXp = next ? next.minXp - current.minXp : 0;
  const progressPercent = next && targetXp > 0 ? Math.min(100, Math.round((progressXp / targetXp) * 100)) : 100;
  return { current, next, progressXp, targetXp, progressPercent };
}

function km(value: number) {
  return `${(Number(value || 0) / 1000).toFixed(1)} km`;
}

export function AdventureTerritoryBoard() {
  const [profile, setProfile] = useState<TerritoryProfile | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiFetch<TerritoryProfile>('/api/v1/adventures/me')
      .then((value) => { if (!cancelled) setProfile(value); })
      .catch(() => { if (!cancelled) setProfile(null); });
    return () => { cancelled = true; };
  }, []);

  if (!profile || profile.territory.municipalities_available === 0) return null;
  const territory = profile.territory;
  const totalXp = Number(profile.summary.total_score || 0);
  const progression = explorerLevel(totalXp);

  return <section className={styles.passportPanel} aria-labelledby="magina-passport-title">
    <div className={styles.passportBackdrop} aria-hidden="true"><span>SIERRA</span><strong>MÁGINA</strong></div>

    <div className={styles.passportHeader}>
      <div>
        <span className={styles.eyebrow}>PASAPORTE DE MÁGINA</span>
        <h2 id="magina-passport-title">Tu territorio conquistado</h2>
        <p>Los descubrimientos, rutas completadas y kilómetros registrados proceden de actividad real. Mágina Aventura no convierte tus recorridos en una clasificación de velocidad.</p>
      </div>
      <div className={styles.exploredRing} style={{ '--explored': `${territory.explored_percent * 3.6}deg` } as React.CSSProperties}>
        <div><strong>{territory.explored_percent}%</strong><span>explorado</span></div>
      </div>
    </div>

    <div className={styles.levelStage}>
      <div className={styles.levelMedal}><span>△</span><strong>{progression.current.level}</strong></div>
      <div className={styles.levelCopy}>
        <span>Nivel {progression.current.level}</span>
        <h3>{progression.current.name}</h3>
        <div className={styles.levelProgress}>
          <div style={{ width: `${progression.progressPercent}%` }} />
        </div>
        <small>{progression.next ? `${progression.progressXp}/${progression.targetXp} XP para ${progression.next.name}` : 'Has alcanzado el rango máximo de Mágina Aventura'}</small>
      </div>
      <div className={styles.xpBadge}><strong>{totalXp}</strong><span>XP</span></div>
    </div>

    <div className={styles.territoryStats}>
      <article><span>⌁</span><strong>{km(profile.journey.completed_distance_m)}</strong><small>Kilómetros conquistados</small></article>
      <article><span>◉</span><strong>{territory.unlocked_checkpoints}/{territory.available_checkpoints}</strong><small>Descubrimientos</small></article>
      <article><span>⌂</span><strong>{territory.municipalities_discovered}/{territory.municipalities_available}</strong><small>Municipios descubiertos</small></article>
      <article><span>↗</span><strong>{km(profile.recorded.recorded_distance_m)}</strong><small>GPS · {profile.recorded.activity_count} actividades</small></article>
    </div>

    <div className={styles.passportBody}>
      <div className={styles.municipalityProgress}>
        <div className={styles.subheading}><div><span className={styles.eyebrow}>MAPA DE DESCUBRIMIENTO</span><h3>Tu huella por Sierra Mágina</h3></div></div>
        <div className={styles.municipalityGrid}>
          {territory.municipalities.map((municipality) => <article key={municipality.municipality_id}>
            <div className={styles.municipalityHead}>
              <div><strong>{municipality.municipality_name}</strong><small>{municipality.adventure_count} {municipality.adventure_count === 1 ? 'aventura' : 'aventuras'}</small></div>
              <span>{municipality.percent}%</span>
            </div>
            <div className={styles.municipalityBar}><div style={{ width: `${municipality.percent}%` }} /></div>
            <small>{municipality.unlocked}/{municipality.available} descubrimientos</small>
          </article>)}
        </div>
      </div>

      <aside className={styles.levelRoadmap}>
        <span className={styles.eyebrow}>RANGOS DE EXPLORADOR</span>
        <h3>El camino continúa</h3>
        <div>
          {EXPLORER_LEVELS.map((level) => {
            const achieved = totalXp >= level.minXp;
            const current = progression.current.level === level.level;
            return <article key={level.level} className={current ? styles.currentLevel : achieved ? styles.achievedLevel : ''}>
              <span>{achieved ? '✓' : level.level}</span>
              <div><strong>{level.name}</strong><small>{level.minXp.toLocaleString('es-ES')} XP</small></div>
            </article>;
          })}
        </div>
      </aside>
    </div>
  </section>;
}
