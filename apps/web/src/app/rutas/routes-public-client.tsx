'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { loadPublicRoutes, type PublicRouteSummary } from '../../lib/public-routes-source';
import styles from './routes-public.module.css';

function km(value: number | null) {
  return value === null ? '—' : `${(value / 1000).toFixed(1)} km`;
}

function duration(value: number | null) {
  if (value === null) return '—';
  const hours = Math.floor(value / 60);
  const minutes = value % 60;
  if (!hours) return `${minutes} min`;
  return `${hours} h${minutes ? ` ${minutes} min` : ''}`;
}

const difficultyLabel: Record<NonNullable<PublicRouteSummary['difficulty']>, string> = {
  easy: 'Fácil',
  moderate: 'Moderada',
  hard: 'Difícil',
  very_hard: 'Muy difícil',
};

export function RoutesPublicClient() {
  const [routes, setRoutes] = useState<PublicRouteSummary[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    loadPublicRoutes()
      .then((items) => { if (!cancelled) { setRoutes(items); setError(false); } })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('es');
    if (!normalized) return routes;
    return routes.filter((route) => `${route.name} ${route.place_name ?? ''} ${route.municipality_name ?? ''} ${route.short_description ?? ''}`.toLocaleLowerCase('es').includes(normalized));
  }, [query, routes]);

  return <main className={styles.shell}>
    <section className={styles.hero}>
      <div><span>Rutas verificadas · Sierra Mágina</span><h1>Camina el territorio con datos reales.</h1><p>Tracks GPX validados, desnivel calculado desde el recorrido y fuentes trazables. Si una ruta no tiene track validado, no se publica.</p></div>
      <label><span className="sr-only">Buscar ruta</span><input type="search" placeholder="Buscar ruta o pueblo…" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
    </section>

    {loading ? <section className={styles.state}><h2>Cargando rutas…</h2><p>Consultando únicamente rutas publicadas y con track validado.</p></section> : null}
    {!loading && error ? <section className={styles.state}><h2>Rutas no disponibles</h2><p>No mostramos rutas de sustitución inventadas. Vuelve a intentarlo más tarde.</p></section> : null}
    {!loading && !error && visible.length === 0 ? <section className={styles.state}><h2>No hay rutas publicadas</h2><p>Cuando una ruta termine su validación aparecerá aquí.</p></section> : null}

    {!loading && !error && visible.length > 0 ? <section className={styles.grid}>{visible.map((route) => <Link className={styles.card} href={`/rutas/${route.slug}`} key={route.id}>
      <div className={styles.image}>{route.hero_url ? <img src={route.hero_url} alt="" /> : <span>Track validado</span>}</div>
      <div className={styles.cardBody}>
        <div className={styles.meta}><span>{route.place_name ?? route.municipality_name ?? 'Sierra Mágina'}</span><span>{route.route_type}</span></div>
        <h2>{route.name}</h2>
        <p>{route.short_description ?? 'Ruta territorial verificada por Mágina Olivo.'}</p>
        <div className={styles.stats}><span><strong>{km(route.distance_m)}</strong>distancia</span><span><strong>{duration(route.duration_minutes)}</strong>duración</span><span><strong>{route.elevation_gain_m ?? '—'} m</strong>desnivel +</span><span><strong>{route.difficulty ? difficultyLabel[route.difficulty] : '—'}</strong>dificultad</span></div>
      </div>
    </Link>)}</section> : null}
  </main>;
}
