'use client';

import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../../lib/api-client';
import styles from './adventure.module.css';

type MunicipalityProgress = {
  municipality_id: string;
  municipality_name: string;
  municipality_slug: string;
  adventure_count: number;
  available: number;
  unlocked: number;
  percent: number;
};

type TerritoryProfile = {
  summary: {
    total_score: number;
  };
  territory: {
    available_checkpoints: number;
    unlocked_checkpoints: number;
    explored_percent: number;
    municipalities_available: number;
    municipalities_discovered: number;
    municipalities: MunicipalityProgress[];
  };
};

const XP_PER_LEVEL = 500;

function explorerRank(level: number) {
  if (level >= 10) return 'Guardián de Sierra Mágina';
  if (level >= 6) return 'Aventurero de Mágina';
  if (level >= 3) return 'Explorador de Mágina';
  return 'Caminante de Mágina';
}

function municipalitySeal(municipality: MunicipalityProgress) {
  if (municipality.available > 0 && municipality.unlocked >= municipality.available) {
    return { icon: '✦', label: 'Sello conseguido', unlocked: true };
  }
  if (municipality.unlocked > 0) {
    return { icon: '◐', label: 'En progreso', unlocked: false };
  }
  return { icon: '○', label: 'Por descubrir', unlocked: false };
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

  const seals = useMemo(() => profile?.territory.municipalities.filter((municipality) => municipality.available > 0 && municipality.unlocked >= municipality.available) ?? [], [profile]);

  if (!profile || profile.territory.municipalities_available === 0) return null;
  const territory = profile.territory;
  const totalXp = Number(profile.summary.total_score || 0);
  const level = Math.floor(totalXp / XP_PER_LEVEL) + 1;
  const levelXp = totalXp % XP_PER_LEVEL;
  const rank = explorerRank(level);

  return <section className={styles.profilePanel} aria-labelledby="magina-passport-title">
    <div className={styles.profileHeading}>
      <div>
        <span className={styles.eyebrow}>PASAPORTE DE MÁGINA</span>
        <h2 id="magina-passport-title">Sierra Mágina explorada: {territory.explored_percent}%</h2>
      </div>
      <p>Tu mapa se completa con descubrimientos reales desbloqueados en las aventuras. No usamos kilómetros ficticios ni guardamos tu recorrido GPS.</p>
    </div>

    <div className={styles.profileStats}>
      <article><strong>Nivel {level}</strong><span>{rank}</span></article>
      <article><strong>{totalXp} XP</strong><span>{levelXp}/{XP_PER_LEVEL} hacia el siguiente nivel</span></article>
      <article><strong>{territory.unlocked_checkpoints}/{territory.available_checkpoints}</strong><span>Descubrimientos</span></article>
      <article><strong>{territory.municipalities_discovered}/{territory.municipalities_available}</strong><span>Municipios descubiertos</span></article>
      <article><strong>{seals.length}</strong><span>Sellos municipales</span></article>
      <article><strong>{territory.explored_percent}%</strong><span>Territorio de aventura</span></article>
    </div>

    {seals.length ? <div className={styles.profileHeading} style={{ marginTop: 18 }}>
      <div><span className={styles.eyebrow}>SELLOS CONSEGUIDOS</span><h3>{seals.length} {seals.length === 1 ? 'municipio completado' : 'municipios completados'}</h3></div>
      <p>Un sello se consigue al desbloquear el 100 % de los descubrimientos disponibles actualmente en ese municipio.</p>
    </div> : null}

    {seals.length ? <div className={styles.collectionGrid} aria-label="Sellos municipales conseguidos">
      {seals.map((municipality) => <article key={`seal-${municipality.municipality_id}`}>
        <div><strong>✦ {municipality.municipality_name}</strong><span>100%</span></div>
        <small>Sello de {municipality.municipality_name} · {municipality.available} descubrimientos</small>
      </article>)}
    </div> : null}

    <div className={styles.collectionGrid} aria-label="Progreso por municipio">
      {territory.municipalities.map((municipality) => {
        const seal = municipalitySeal(municipality);
        return <article key={municipality.municipality_id}>
          <div>
            <strong>{seal.icon} {municipality.municipality_name}</strong>
            <span>{municipality.unlocked}/{municipality.available}</span>
          </div>
          <progress max={Math.max(municipality.available, 1)} value={municipality.unlocked} aria-label={`${municipality.municipality_name} ${municipality.percent}%`} />
          <small>{seal.label} · {municipality.percent}% · {municipality.adventure_count} {municipality.adventure_count === 1 ? 'aventura' : 'aventuras'}</small>
        </article>;
      })}
    </div>
  </section>;
}
