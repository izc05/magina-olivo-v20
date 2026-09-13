'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { loadPublicRoute, type PublicRouteDetail } from '../../../lib/public-routes-source';
import styles from '../routes-public.module.css';

function km(value: number | null) { return value === null ? '—' : `${(value / 1000).toFixed(1)} km`; }
function duration(value: number | null) {
  if (value === null) return '—';
  const h = Math.floor(value / 60); const m = value % 60;
  return h ? `${h} h${m ? ` ${m} min` : ''}` : `${m} min`;
}

function lineCoordinates(detail: PublicRouteDetail | null): [number, number][] {
  const geometry = detail?.track?.geometry;
  if (!geometry || geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates)) return [];
  return geometry.coordinates.filter((coordinate): coordinate is [number, number] =>
    Array.isArray(coordinate) && coordinate.length >= 2 && Number.isFinite(coordinate[0]) && Number.isFinite(coordinate[1]));
}

function normalizedPolyline(coordinates: [number, number][], width: number, height: number, padding = 18) {
  if (coordinates.length < 2) return '';
  const xs = coordinates.map(([x]) => x); const ys = coordinates.map(([, y]) => y);
  const minX = Math.min(...xs); const maxX = Math.max(...xs); const minY = Math.min(...ys); const maxY = Math.max(...ys);
  const dx = Math.max(maxX - minX, 1e-9); const dy = Math.max(maxY - minY, 1e-9);
  return coordinates.map(([x, y], index) => {
    const px = padding + ((x - minX) / dx) * (width - padding * 2);
    const py = height - padding - ((y - minY) / dy) * (height - padding * 2);
    return `${index ? 'L' : 'M'}${px.toFixed(1)},${py.toFixed(1)}`;
  }).join(' ');
}

function elevationPath(samples: PublicRouteDetail['elevation'], width: number, height: number, padding = 18) {
  if (samples.length < 2) return '';
  const maxDistance = Math.max(...samples.map((sample) => Number(sample.distance_m)), 1);
  const elevations = samples.map((sample) => Number(sample.elevation_m));
  const minElevation = Math.min(...elevations); const maxElevation = Math.max(...elevations);
  const range = Math.max(maxElevation - minElevation, 1);
  return samples.map((sample, index) => {
    const x = padding + (Number(sample.distance_m) / maxDistance) * (width - padding * 2);
    const y = height - padding - ((Number(sample.elevation_m) - minElevation) / range) * (height - padding * 2);
    return `${index ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
}

export function RouteDetailClient() {
  const [slug, setSlug] = useState<string | null>(null);
  const [detail, setDetail] = useState<PublicRouteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    const nextSlug = new URLSearchParams(window.location.search).get('slug');
    setSlug(nextSlug);
    if (!nextSlug) {
      setError(true);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    loadPublicRoute(nextSlug)
      .then((value) => { if (!cancelled) { setDetail(value); setError(false); } })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const coordinates = useMemo(() => lineCoordinates(detail), [detail]);
  const trackPath = useMemo(() => normalizedPolyline(coordinates, 600, 340), [coordinates]);
  const profilePath = useMemo(() => elevationPath(detail?.elevation ?? [], 1100, 210), [detail?.elevation]);

  if (loading) return <main className={styles.detailShell}><section className={styles.state}><h1>Cargando ruta…</h1><p>Comprobando track y datos publicados.</p></section></main>;
  if (error || !detail || !slug) return <main className={styles.detailShell}><section className={styles.state}><h1>Ruta no disponible</h1><p>Puede estar en revisión, no disponer de track validado o faltar el identificador de ruta.</p><Link href="/rutas">Volver a rutas</Link></section></main>;

  const route = detail.route;
  return <main className={styles.detailShell}>
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
      <article className={styles.mapCard} aria-label="Vista del trazado de la ruta">
        {trackPath ? <svg className={styles.mapSvg} viewBox="0 0 600 340" role="img" aria-label="Trazado proporcional del track GPX"><path d={trackPath} fill="none" stroke="currentColor" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" opacity=".85" /></svg> : <div className={styles.emptyMap}>No hay geometría pública disponible.</div>}
      </article>
    </section>

    <section className={styles.profileCard}>
      <h2>Perfil de elevación</h2>
      {profilePath ? <svg className={styles.profileSvg} viewBox="0 0 1100 210" preserveAspectRatio="none" role="img" aria-label="Perfil de elevación calculado a partir de las cotas GPX"><path d={`${profilePath} L1082,192 L18,192 Z`} fill="rgba(56,98,65,.14)" /><path d={profilePath} fill="none" stroke="currentColor" strokeWidth="4" vectorEffect="non-scaling-stroke" /></svg> : <p>No hay cotas de elevación en el GPX validado. No se han inventado altitudes.</p>}
    </section>

    <section className={styles.infoGrid}>
      <article className={styles.infoCard}><h2>Acceso y seguridad</h2><p>{route.access_notes ?? 'Sin notas específicas de acceso.'}</p><p>{route.safety_notes ?? 'Sin avisos adicionales publicados.'}</p></article>
      <article className={styles.infoCard}><h2>Agua y recorrido</h2><p>{route.water_notes ?? 'No hay información verificada sobre puntos de agua.'}</p><p>{route.circular ? 'Ruta circular.' : 'Ruta lineal o de ida y vuelta según el track.'} {route.family_friendly ? 'Marcada como apta para familias.' : ''}</p></article>
      <article className={styles.infoCard}><h2>Fuentes</h2>{detail.sources.length ? <ul className={styles.sourceList}>{detail.sources.map((source, index) => <li key={String(source.id ?? index)}>{typeof source.source_url === 'string' ? <a href={source.source_url} target="_blank" rel="noreferrer">{String(source.source_name ?? 'Fuente')}</a> : String(source.source_name ?? 'Fuente')}</li>)}</ul> : <p>La ficha no publica fuentes adicionales.</p>}</article>
    </section>
  </main>;
}
