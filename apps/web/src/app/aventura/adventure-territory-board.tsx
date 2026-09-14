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

function explorerLevel(totalXp: number) {
  let current = EXPLORER_LEVELS[0];
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

  return <section className={styles.profilePanel} aria-labelledby="magina-passport-title">
    <div className={styles.profileHeading}>
      <div>
        <span className={styles.eyebrow}>PASAPORTE DE MÁGINA</span>
        <h2 id="magina-passport-title">Sierra Mágina explorada: {territory.explored_percent}%</h2>
      </div>
      <p>Tu mapa se completa con descubrimientos reales desbloqueados en las aventuras. Los kilómetros conquistados proceden de rutas completadas; los kilómetros grabados solo existen cuando activas el GPS de forma explícita.</p>
    </div>

    <div className={styles.profileStats}>
      <article><strong>Nivel {progression.current.level}</strong><span>{progression.current.name}</span></article>
      <article><strong>{totalXp} XP</strong><span>{progression.next ? `${progression.progressXp}/${progression.targetXp} hacia ${progression.next.name}` : 'Nivel máximo alcanzado'}</span></article>
      <article><strong>{km(profile.journey.completed_distance_m)}</strong><span>Kilómetros conquistados</span></article>
      <article><strong>{km(profile.recorded.recorded_distance_m)}</strong><span>Kilómetros grabados · {profile.recorded.activity_count} actividades</span></article>
      <article><strong>{territory.unlocked_checkpoints}/{territory.available_checkpoints}</strong><span>Descubrimientos</span></article>
      <article><strong>{territory.municipalities_discovered}/{territory.municipalities_available}</strong><span>Municipios descubiertos</span></article>
      <article><strong>{territory.explored_percent}%</strong><span>Territorio de aventura</span></article>
    </div>

    <div className={styles.recent}>
      <h3>Progresión de explorador</h3>
      <p>Nivel {progression.current.level} · {progression.current.name}{progression.next ? ` · siguiente: ${progression.next.name}` : ' · rango máximo'}</p>
      <progress max={100} value={progression.progressPercent} aria-label={`Progreso del nivel ${progression.current.level}: ${progression.progressPercent}%`} />
      <div className={styles.badges} aria-label="Escalera de niveles de Mágina Aventura">
        {EXPLORER_LEVELS.map((level) => <span key={level.level}>{totalXp >= level.minXp ? '✓' : '○'} {level.level}. {level.name}</span>)}
      </div>
    </div>

    <div className={styles.collectionGrid} aria-label="Progreso por municipio">
      {territory.municipalities.map((municipality) => <article key={municipality.municipality_id}>
        <div>
          <strong>{municipality.municipality_name}</strong>
          <span>{municipality.unlocked}/{municipality.available}</span>
        </div>
        <progress max={Math.max(municipality.available, 1)} value={municipality.unlocked} aria-label={`${municipality.municipality_name} ${municipality.percent}%`} />
        <small>{municipality.percent}% · {municipality.adventure_count} {municipality.adventure_count === 1 ? 'aventura' : 'aventuras'}</small>
      </article>)}
    </div>
  </section>;
}
