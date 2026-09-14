'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ApiRequestError } from '../../../lib/api-client';
import { loadPublicRoute, type PublicRouteDetail } from '../../../lib/public-routes-source';
import { loadActiveRouteActivity } from '../../../lib/route-activity-source';
import { RouteActivityRecorder } from '../../rutas/detalle/route-activity-recorder';
import { RouteAdventurePanel } from '../../rutas/detalle/route-adventure-panel';
import { RouteMap } from '../../rutas/detalle/route-map';
import styles from './live.module.css';

function distance(value: number | null) {
  return value == null ? '—' : `${(value / 1000).toFixed(1)} km`;
}

function duration(value: number | null) {
  if (value == null) return '—';
  const hours = Math.floor(value / 60);
  const minutes = value % 60;
  return hours ? `${hours} h${minutes ? ` ${minutes} min` : ''}` : `${minutes} min`;
}

function difficulty(value: PublicRouteDetail['route']['difficulty']) {
  if (value === 'easy') return 'Fácil';
  if (value === 'moderate') return 'Moderada';
  if (value === 'hard') return 'Difícil';
  if (value === 'very_hard') return 'Muy difícil';
  return 'Sin clasificar';
}

export function AdventureLiveClient() {
  const [slug, setSlug] = useState<string | null>(null);
  const [detail, setDetail] = useState<PublicRouteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [authRequired, setAuthRequired] = useState(false);
  const [noActive, setNoActive] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function resolveRoute() {
      const querySlug = new URLSearchParams(window.location.search).get('slug');
      if (querySlug) {
        if (!cancelled) setSlug(querySlug);
        return;
      }

      try {
        const { activity } = await loadActiveRouteActivity();
        if (cancelled) return;
        if (activity?.route_slug) {
          setSlug(activity.route_slug);
          const next = `/aventura/en-curso?slug=${encodeURIComponent(activity.route_slug)}`;
          window.history.replaceState(window.history.state, '', next);
          return;
        }
        setNoActive(true);
      } catch (cause) {
        if (cancelled) return;
        if (cause instanceof ApiRequestError && cause.status === 401) setAuthRequired(true);
        else setError(true);
      }
    }

    void resolveRoute();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!slug) return;
    let cancelled = false;
    setLoading(true);
    setError(false);
    loadPublicRoute(slug)
      .then((value) => {
        if (cancelled) return;
        setDetail(value);
      })
      .catch(() => {
        if (cancelled) return;
        setDetail(null);
        setError(true);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [slug]);

  useEffect(() => {
    if (!slug && (noActive || authRequired || error)) setLoading(false);
  }, [slug, noActive, authRequired, error]);

  if (loading) return <main className={styles.page}><section className={styles.state}><strong>Preparando expedición…</strong><p>Cargando la ruta, checkpoints y estado GPS.</p></section></main>;

  if (authRequired && !slug) return <main className={styles.page}><section className={styles.state}><span className={styles.kicker}>MÁGINA AVENTURA</span><h1>Tu aventura en curso</h1><p>Inicia sesión para recuperar la actividad o expedición que tengas abierta.</p><Link className={styles.primary} href="/login?next=%2Faventura%2Fen-curso">Iniciar sesión</Link><Link className={styles.secondary} href="/aventura">Volver a Mágina Aventura</Link></section></main>;

  if (noActive && !slug) return <main className={styles.page}><section className={styles.state}><span className={styles.kicker}>MÁGINA AVENTURA</span><h1>No hay una aventura en curso</h1><p>Elige una aventura o una ruta real y empieza cuando estés preparado.</p><Link className={styles.primary} href="/aventura">Elegir aventura</Link><Link className={styles.secondary} href="/rutas">Ver rutas</Link></section></main>;

  if (error || !detail || !slug) return <main className={styles.page}><section className={styles.state}><span className={styles.kicker}>MÁGINA AVENTURA</span><h1>No podemos abrir esta expedición</h1><p>La ruta puede estar en revisión o no disponer ya de un track publicado y validado.</p><Link className={styles.primary} href="/aventura">Volver a Mágina Aventura</Link></section></main>;

  const route = detail.route;

  return <main className={styles.page}>
    <header className={styles.topbar}>
      <Link href="/aventura" className={styles.back}>← Aventura</Link>
      <span className={styles.livePill}>● Modo expedición</span>
    </header>

    <section className={styles.hero}>
      <span className={styles.kicker}>{route.municipality_name ?? 'Sierra Mágina'} · AVENTURA EN CURSO</span>
      <h1>{route.name}</h1>
      <p>{route.short_description ?? 'Sigue el track real, completa los descubrimientos y mantén siempre la seguridad de la ruta por delante del juego.'}</p>
      <div className={styles.stats}>
        <article><span>Distancia</span><strong>{distance(route.distance_m)}</strong></article>
        <article><span>Duración</span><strong>{duration(route.duration_minutes)}</strong></article>
        <article><span>Dificultad</span><strong>{difficulty(route.difficulty)}</strong></article>
        <article><span>Desnivel +</span><strong>{route.elevation_gain_m == null ? '—' : `${Math.round(route.elevation_gain_m)} m`}</strong></article>
      </div>
    </section>

    <nav className={styles.quickNav} aria-label="Controles de la expedición">
      <a href="#mapa">Mapa</a>
      <a href="#gps">GPS</a>
      <a href="#retos">Retos</a>
      <Link href={`/rutas/detalle?slug=${encodeURIComponent(slug)}`}>Ficha completa</Link>
    </nav>

    <section id="mapa" className={styles.mapSection}>
      <div className={styles.sectionHead}>
        <div><span className={styles.kicker}>TRACK REAL + CHECKPOINTS</span><h2>Mapa de expedición</h2></div>
        <p>Usa los checkpoints como capa lúdica. Para orientación y seguridad manda siempre el track validado y la señalización del terreno.</p>
      </div>
      <div className={styles.mapFrame}><RouteMap detail={detail} /></div>
    </section>

    {(route.safety_notes || route.access_notes || route.restrictions) ? <section className={styles.safety}>
      <strong>Antes de seguir</strong>
      {route.safety_notes ? <p>{route.safety_notes}</p> : null}
      {route.access_notes ? <p>{route.access_notes}</p> : null}
      {route.restrictions ? <p>{route.restrictions}</p> : null}
    </section> : null}

    <section id="gps" className={styles.block}><RouteActivityRecorder routeId={route.id} slug={slug} /></section>
    <section id="retos" className={styles.block}><RouteAdventurePanel routeId={route.id} slug={slug} /></section>

    <footer className={styles.footer}>
      <Link href={`/rutas/detalle?slug=${encodeURIComponent(slug)}`}>Abrir ficha completa de senderismo</Link>
      <small>Mágina Aventura añade exploración y colección; no sustituye navegación, señalización, cierres ni avisos oficiales.</small>
    </footer>
  </main>;
}
