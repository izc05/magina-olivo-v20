'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import {
  loadExplorerProfile,
  loadPublicAdventures,
  publicRouteMediaUrl,
  type ExplorerProfile,
  type PublicAdventureSummary,
  type RouteAdventureCheckpoint,
} from '../../lib/public-routes-source';
import styles from './adventure.module.css';

function difficultyLabel(value: PublicAdventureSummary['difficulty']) {
  if (value === 'easy') return 'Fácil';
  if (value === 'moderate') return 'Moderada';
  if (value === 'hard') return 'Difícil';
  if (value === 'very_hard') return 'Muy difícil';
  return 'Sin clasificar';
}

function km(value: number | null) {
  return value == null ? '—' : `${(Number(value) / 1000).toFixed(1)} km`;
}

function duration(value: number | null) {
  if (value == null) return '—';
  const hours = Math.floor(value / 60);
  const minutes = value % 60;
  return hours > 0 ? `${hours} h${minutes ? ` ${minutes} min` : ''}` : `${minutes} min`;
}

function collectionLabel(kind: RouteAdventureCheckpoint['kind']) {
  return ({
    landmark: 'Lugares', trivia: 'Retos', observation: 'Observaciones', photo: 'Recuerdos',
    collection: 'Coleccionables', rest: 'Descansos',
  } satisfies Record<RouteAdventureCheckpoint['kind'], string>)[kind];
}

function badgeLabel(code: string) {
  if (code === 'primer_descubrimiento') return 'Primer descubrimiento';
  if (code === 'aventurero_magina') return 'Aventurero de Mágina';
  if (code === 'caminante_de_la_sierra') return 'Caminante de la Sierra';
  if (code === 'mil_puntos') return '1.000 puntos';
  return code.replaceAll('_', ' ');
}

function runStatus(value: ExplorerProfile['recent_runs'][number]['status']) {
  if (value === 'completed') return 'Completada';
  if (value === 'active') return 'En curso';
  return 'Abandonada';
}

export function AdventureHubClient() {
  const [adventures, setAdventures] = useState<PublicAdventureSummary[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [profile, setProfile] = useState<ExplorerProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadPublicAdventures()
      .then((value) => {
        if (cancelled) return;
        setAdventures(value.adventures);
        setNotice(value.notice);
        setError(false);
      })
      .catch((cause) => {
        console.warn('Unable to load Mágina Aventura hub', cause);
        if (!cancelled) setError(true);
      })
      .finally(() => { if (!cancelled) setLoading(false); });

    loadExplorerProfile()
      .then((value) => { if (!cancelled) setProfile(value); })
      .catch(() => { if (!cancelled) setProfile(null); });

    return () => { cancelled = true; };
  }, []);

  const totals = useMemo(() => adventures.reduce((current, adventure) => ({
    checkpoints: current.checkpoints + Number(adventure.checkpoint_count || 0),
    points: current.points + Number(adventure.total_points || 0),
  }), { checkpoints: 0, points: 0 }), [adventures]);

  return <main className={styles.shell}>
    <section className={styles.hero}>
      <div>
        <span className={styles.eyebrow}>MÁGINA OLIVO · EXPLORACIÓN</span>
        <h1>Mágina<br/>Aventura</h1>
        <p>Camina por rutas reales de Sierra Mágina, acércate a los puntos de descubrimiento y completa retos para llenar tu Cuaderno del Explorador.</p>
        <div className={styles.heroActions}>
          <a href="#aventuras" className={styles.primaryAction}>Ver aventuras</a>
          <Link href="/rutas" className={styles.secondaryAction}>Ver rutas</Link>
        </div>
      </div>
      <div className={styles.heroStats} aria-label="Resumen público de Mágina Aventura">
        <article><strong>{adventures.length}</strong><span>Aventuras disponibles</span></article>
        <article><strong>{totals.checkpoints}</strong><span>Descubrimientos</span></article>
        <article><strong>{totals.points}</strong><span>Puntos posibles</span></article>
      </div>
    </section>

    {profile ? <section className={styles.profilePanel} aria-labelledby="explorer-profile-title">
      <div className={styles.profileHeading}>
        <div><span className={styles.eyebrow}>MI EXPEDICIÓN</span><h2 id="explorer-profile-title">Cuaderno global del Explorador</h2></div>
        <p>Tu progreso une todas las aventuras de Mágina sin guardar una traza continua de tu posición.</p>
      </div>
      <div className={styles.profileStats}>
        <article><strong>{profile.summary.adventures_started}</strong><span>Iniciadas</span></article>
        <article><strong>{profile.summary.adventures_completed}</strong><span>Completadas</span></article>
        <article><strong>{profile.summary.discoveries}</strong><span>Descubrimientos</span></article>
        <article><strong>{profile.summary.total_score}</strong><span>Puntos</span></article>
      </div>
      {profile.badges.length > 0 ? <div className={styles.badges} aria-label="Insignias globales">{profile.badges.map((badge) => <span key={badge}>✦ {badgeLabel(badge)}</span>)}</div> : null}
      {profile.collections.length > 0 ? <div className={styles.collectionGrid}>{profile.collections.map((collection) => {
        const percent = collection.available > 0 ? Math.round((collection.unlocked / collection.available) * 100) : 0;
        return <article key={collection.kind}>
          <div><strong>{collectionLabel(collection.kind)}</strong><span>{collection.unlocked}/{collection.available}</span></div>
          <progress max={Math.max(collection.available, 1)} value={collection.unlocked} aria-label={`${collectionLabel(collection.kind)} ${percent}%`} />
          <small>{percent}% descubierto</small>
        </article>;
      })}</div> : null}
      {profile.recent_runs.length > 0 ? <div className={styles.recent}>
        <h3>Últimas expediciones</h3>
        {profile.recent_runs.map((run) => <Link href={`/rutas/detalle?slug=${encodeURIComponent(run.slug)}`} key={run.id}>
          <span><strong>{run.adventure_title}</strong><small>{run.route_name}</small></span>
          <span><strong>{run.unlocked_checkpoints}/{run.total_checkpoints}</strong><small>{runStatus(run.status)}</small></span>
        </Link>)}
      </div> : null}
      <small className={styles.privacy}>{profile.privacy}</small>
    </section> : null}

    <section className={styles.how} aria-labelledby="como-funciona">
      <div className={styles.sectionHeading}><span className={styles.eyebrow}>CÓMO FUNCIONA</span><h2 id="como-funciona">El sendero se convierte en aventura</h2></div>
      <div className={styles.howGrid}>
        <article><strong>01</strong><h3>Elige una ruta</h3><p>Solo usamos rutas públicas con track validado. Antes de salir, revisa siempre sus datos técnicos y avisos.</p></article>
        <article><strong>02</strong><h3>Descubre puntos</h3><p>En cada etapa puedes encontrar lugares, preguntas, observaciones y coleccionables configurados desde Mágina Olivo.</p></article>
        <article><strong>03</strong><h3>Completa tu cuaderno</h3><p>Tu progreso suma puntos, insignias y descubrimientos. La posición exacta no se guarda como un recorrido continuo.</p></article>
      </div>
    </section>

    <section id="aventuras" className={styles.adventures} aria-labelledby="aventuras-title">
      <div className={styles.sectionHeading}><span className={styles.eyebrow}>AVENTURAS ACTIVAS</span><h2 id="aventuras-title">Elige dónde empezar</h2><p>El catálogo se genera desde la base real: una aventura no aparece hasta tener una ruta publicada, track validado y al menos un checkpoint activo.</p></div>

      {loading ? <div className={styles.state}><h3>Buscando aventuras…</h3><p>Consultando los recorridos disponibles.</p></div> : null}
      {!loading && error ? <div className={styles.state}><h3>Mágina Aventura no está disponible ahora</h3><p>No mostramos recorridos ficticios como sustitución. Puedes seguir consultando las rutas públicas.</p><Link href="/rutas">Abrir rutas</Link></div> : null}
      {!loading && !error && adventures.length === 0 ? <div className={styles.state}><h3>Las primeras aventuras están en preparación</h3><p>El sistema ya está listo, pero no publicaremos una aventura hasta que sus puntos reales estén revisados desde Administración.</p><Link href="/rutas">Explorar rutas disponibles</Link></div> : null}

      {!loading && !error && adventures.length > 0 ? <div className={styles.grid}>{adventures.map((adventure) => <article className={styles.card} key={adventure.route_id}>
        <div className={styles.image}>
          {adventure.hero_url ? <img src={publicRouteMediaUrl(adventure.hero_url)} alt="" /> : <span>✦ Mágina Aventura</span>}
          <div className={styles.imageBadge}>{adventure.checkpoint_count} descubrimientos</div>
        </div>
        <div className={styles.cardBody}>
          <div className={styles.meta}><span>{adventure.municipality_name ?? 'Sierra Mágina'}</span><span>{difficultyLabel(adventure.difficulty)}</span></div>
          <h3>{adventure.title}</h3>
          <p>{adventure.intro ?? adventure.short_description ?? `Aventura sobre la ruta ${adventure.route_name}.`}</p>
          <div className={styles.cardStats}>
            <span><strong>{km(adventure.distance_m)}</strong>Recorrido</span>
            <span><strong>{duration(adventure.duration_minutes)}</strong>Duración</span>
            <span><strong>{adventure.total_points}</strong>Puntos</span>
          </div>
          <Link className={styles.primaryAction} href={`/rutas/detalle?slug=${encodeURIComponent(adventure.slug)}`}>Entrar en la aventura</Link>
          <small>Ruta base: {adventure.route_name}{adventure.place_name ? ` · ${adventure.place_name}` : ''}</small>
        </div>
      </article>)}</div> : null}
    </section>

    {notice ? <p className={styles.notice}>{notice}</p> : null}
  </main>;
}
