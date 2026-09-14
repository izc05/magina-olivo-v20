'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { loadPublicRoute, type PublicRouteDetail } from '../../../lib/public-routes-source';
import { RouteMap } from './route-map';
import { RouteCommunityPanel } from './route-community-panel';
import { RouteElevationProfile } from './route-elevation-profile';
import { RouteExperience } from './route-experience';
import { RouteAdventurePanel } from './route-adventure-panel';
import { RouteActivityRecorder } from './route-activity-recorder';
import styles from '../routes-public.module.css';

function km(value: number | null) { return value === null ? '—' : `${(value / 1000).toFixed(1)} km`; }
function duration(value: number | null) {
  if (value === null) return '—';
  const h = Math.floor(value / 60); const m = value % 60;
  return h ? `${h} h${m ? ` ${m} min` : ''}` : `${m} min`;
}

export function RouteDetailClient() {
  const [slug, setSlug] = useState<string | null>(null);
  const [detail, setDetail] = useState<PublicRouteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    const nextSlug = new URLSearchParams(window.location.search).get('slug');
    setSlug(nextSlug);
    if (!nextSlug) { setError(true); setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    loadPublicRoute(nextSlug)
      .then((value) => { if (!cancelled) { setDetail(value); setError(false); } })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  if (loading) return <div className={styles.detailShell}><section className={styles.state}><h1>Cargando ruta…</h1><p>Comprobando track y datos publicados.</p></section></div>;
  if (error || !detail || !slug) return <div className={styles.detailShell}><section className={styles.state}><h1>Ruta no disponible</h1><p>Puede estar en revisión, no disponer de track validado o faltar el identificador de ruta.</p><Link href="/rutas">Volver a rutas</Link></section></div>;

  const route = detail.route;
  return <div className={styles.detailShell}>
    <div className={styles.notice}>Track validado · los datos técnicos proceden del recorrido almacenado, no de una ruta generada por IA.</div>
    <section className={styles.detailHero}>
      <article className={styles.detailIntro}>
        <Link href="/rutas">← Todas las rutas</Link>
        <h1>{route.name}</h1>
        <p>{route.short_description ?? route.description ?? 'Ruta territorial de Sierra Mágina.'}</p>
        <div className={styles.detailStats}>
          <article><span>Distancia</span><strong>{km(route.distance_m)}</strong></article>
          <article><span>Duración</span><strong>{duration(route.duration_minutes)}</strong></article>
          <article><span>Desnivel +</span><strong>{route.elevation_gain_m ?? '—'} m</strong></article>
          <article><span>Altitud</span><strong>{route.min_altitude_m ?? '—'}–{route.max_altitude_m ?? '—'} m</strong></article>
        </div>
      </article>
      <article className={styles.mapCard} aria-label="Mapa inteligente del trazado de la ruta"><RouteMap detail={detail} /></article>
    </section>

    <RouteElevationProfile samples={detail.elevation} />

    <section className={styles.infoGrid}>
      <article className={styles.infoCard}><h2>Acceso y seguridad</h2><p>{route.access_notes ?? 'Sin notas específicas de acceso.'}</p><p>{route.safety_notes ?? 'Sin avisos adicionales publicados.'}</p></article>
      <article className={styles.infoCard}><h2>Agua y recorrido</h2><p>{route.water_notes ?? 'No hay información verificada sobre puntos de agua.'}</p><p>{route.circular ? 'Ruta circular.' : 'Ruta lineal o de ida y vuelta según el track.'} {route.family_friendly ? 'Marcada como apta para familias.' : ''}</p></article>
      <article className={styles.infoCard}><h2>Fuentes oficiales y editoriales</h2>{detail.sources.length ? <ul className={styles.sourceList}>{detail.sources.map((source, index) => <li key={String(source.id ?? index)}>{typeof source.source_url === 'string' ? <a href={source.source_url} target="_blank" rel="noreferrer">{String(source.source_name ?? 'Fuente')}</a> : String(source.source_name ?? 'Fuente')}</li>)}</ul> : <p>La ficha no publica fuentes adicionales.</p>}</article>
    </section>

    <RouteActivityRecorder routeId={route.id} slug={slug} />
    <RouteAdventurePanel routeId={route.id} slug={slug} />
    <RouteExperience detail={detail} />
    <RouteCommunityPanel routeId={route.id} slug={slug} />
  </div>;
}
