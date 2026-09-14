'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { loadExplorerProfile, type ExplorerProfile } from '../lib/public-routes-source';

function formatKm(metres: number) {
  return `${(Math.max(0, metres) / 1000).toFixed(metres >= 100000 ? 0 : 1)} km`;
}

function formatElevation(metres: number) {
  return `${Math.round(Math.max(0, metres)).toLocaleString('es-ES')} m+`;
}

function formatDuration(minutes: number) {
  const safe = Math.max(0, Math.round(minutes));
  const hours = Math.floor(safe / 60);
  const remainder = safe % 60;
  if (!hours) return `${remainder} min`;
  return remainder ? `${hours} h ${remainder} min` : `${hours} h`;
}

function explorerLevel(xp: number) {
  const safeXp = Math.max(0, xp);
  const level = Math.floor(safeXp / 500) + 1;
  const current = safeXp % 500;
  const rank = level >= 10
    ? 'Guardián de Sierra Mágina'
    : level >= 7
      ? 'Aventurero de Mágina'
      : level >= 4
        ? 'Explorador de Mágina'
        : 'Caminante';
  return { level, current, rank };
}

function badgeLabel(code: string) {
  const labels: Record<string, string> = {
    primer_descubrimiento: 'Primer descubrimiento',
    aventurero_magina: 'Aventurero de Mágina',
    caminante_de_la_sierra: 'Caminante de la Sierra',
    mil_puntos: '1.000 XP',
    veinticinco_km: '25 km conquistados',
    cien_km_magina: '100 km por Mágina',
    coleccionista_de_magina: 'Coleccionista de Mágina',
    hallazgo_legendario: 'Hallazgo legendario',
  };
  return labels[code] ?? code.replaceAll('_', ' ');
}

function categoryLabel(category: ExplorerProfile['album'][number]['category']) {
  return ({
    flora: 'Flora',
    fauna: 'Fauna',
    heritage: 'Patrimonio',
    olive_culture: 'Olivar',
    tradition: 'Tradiciones',
    landscape: 'Paisaje',
  } as const)[category];
}

export function AdventureExplorerProfile() {
  const [profile, setProfile] = useState<ExplorerProfile | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadExplorerProfile()
      .then((value) => {
        if (cancelled) return;
        setProfile(value);
        setFailed(false);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => { cancelled = true; };
  }, []);

  const album = useMemo(() => {
    if (!profile) return [];
    const grouped = new Map<string, { available: number; unlocked: number }>();
    for (const entry of profile.album) {
      const current = grouped.get(entry.category) ?? { available: 0, unlocked: 0 };
      current.available += Number(entry.available);
      current.unlocked += Number(entry.unlocked);
      grouped.set(entry.category, current);
    }
    return [...grouped.entries()].map(([category, value]) => ({
      category: category as ExplorerProfile['album'][number]['category'],
      ...value,
    }));
  }, [profile]);

  const municipalitySeals = useMemo(() => profile?.territory.municipalities.filter((municipality) => municipality.available > 0 && municipality.unlocked >= municipality.available) ?? [], [profile]);

  if (failed) return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Mágina Aventura</h3><span>Sin conexión</span></div>
    <p className="subtle">No se ha podido cargar ahora tu progreso de exploración. Tus datos guardados no se han perdido.</p>
    <Link className="profile-line" href="/aventura"><span>Abrir Mágina Aventura</span><span>›</span></Link>
  </section>;

  if (!profile) return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Mágina Aventura</h3><span>Cargando…</span></div>
    <p className="subtle">Preparando tu pasaporte de explorador.</p>
  </section>;

  const xp = Number(profile.summary.total_score);
  const level = explorerLevel(xp);
  const albumUnlocked = album.reduce((sum, item) => sum + item.unlocked, 0);
  const albumAvailable = album.reduce((sum, item) => sum + item.available, 0);

  return <section className="card profile-card premium-profile-card olive-card">
    <div className="profile-card-head">
      <h3>Mi perfil de explorador</h3>
      <span>Nivel {level.level}</span>
    </div>

    <div className="olivo-level">
      <span className="olivo-mark">✦</span>
      <div>
        <strong>{level.rank}</strong>
        <small>{xp.toLocaleString('es-ES')} XP · {level.current}/500 XP para el siguiente nivel</small>
        <div className="level-bar"><i style={{ width: `${Math.max(2, (level.current / 500) * 100)}%` }}/></div>
      </div>
    </div>

    <div className="profile-line"><span>Km conquistados</span><strong>{formatKm(profile.journey.completed_distance_m)}</strong></div>
    <div className="profile-line"><span>Desnivel acumulado</span><strong>{formatElevation(profile.journey.completed_elevation_gain_m)}</strong></div>
    <div className="profile-line"><span>Tiempo de rutas completadas</span><strong>{formatDuration(profile.journey.completed_duration_minutes)}</strong></div>
    <div className="profile-line"><span>Ruta más larga</span><strong>{formatKm(profile.journey.longest_route_m)}</strong></div>
    <div className="profile-line"><span>Aventuras completadas</span><strong>{profile.summary.adventures_completed}</strong></div>
    <div className="profile-line"><span>Descubrimientos</span><strong>{profile.summary.discoveries}</strong></div>
    <div className="profile-line"><span>Municipios descubiertos</span><strong>{profile.territory.municipalities_discovered}/{profile.territory.municipalities_available}</strong></div>
    <div className="profile-line"><span>Sellos municipales</span><strong>{municipalitySeals.length}</strong></div>
    <div className="profile-line"><span>Sierra Mágina explorada</span><strong>{profile.territory.explored_percent}%</strong></div>
    <div className="profile-line"><span>Álbum territorial</span><strong>{albumUnlocked}/{albumAvailable}</strong></div>

    {municipalitySeals.length > 0 ? <div className="role-row" aria-label="Sellos municipales de Mágina Aventura">
      {municipalitySeals.map((municipality) => <span key={municipality.municipality_id}>✦ Sello de {municipality.municipality_name}</span>)}
    </div> : <p className="subtle">Completa el 100 % de los descubrimientos de un municipio para conseguir su sello.</p>}

    {album.length > 0 ? <div className="role-row" aria-label="Álbum de Sierra Mágina">
      {album.map((entry) => <span key={entry.category}>{categoryLabel(entry.category)} {entry.unlocked}/{entry.available}</span>)}
    </div> : null}

    {profile.badges.length > 0 ? <div className="role-row" aria-label="Insignias de Mágina Aventura">
      {profile.badges.map((badge) => <span key={badge}>🏅 {badgeLabel(badge)}</span>)}
    </div> : <p className="subtle">Tu primera insignia llegará con el primer descubrimiento.</p>}

    <Link className="profile-line" href="/aventura"><span>Abrir Pasaporte y Álbum completo</span><span>›</span></Link>
    <small className="subtle">Los kilómetros se calculan con la distancia oficial de rutas distintas completadas. Repetir una ruta no aumenta artificialmente el total.</small>
  </section>;
}
