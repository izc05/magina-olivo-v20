'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ApiRequestError } from '../../../lib/api-client';
import {
  loadPublicRoute,
  loadPublicRouteAdventure,
  type PublicRouteAdventure,
  type PublicRouteDetail,
  type RouteAdventureCheckpoint,
} from '../../../lib/public-routes-source';
import { loadActiveRouteActivity } from '../../../lib/route-activity-source';
import { RouteActivityRecorder } from '../../rutas/detalle/route-activity-recorder';
import { RouteAdventurePanel } from '../../rutas/detalle/route-adventure-panel';
import { RouteMap } from '../../rutas/detalle/route-map';
import styles from './live.module.css';

type GpsTelemetry = {
  status: 'idle' | 'searching' | 'tracking' | 'error';
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null;
  timestamp: number | null;
};

type AdventureProgressDetail = { unlockedCheckpointIds: string[] };

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

function gpsStatus(value: GpsTelemetry['status']) {
  if (value === 'tracking') return 'GPS activo';
  if (value === 'searching') return 'Buscando señal…';
  if (value === 'error') return 'GPS sin señal';
  return 'GPS detenido';
}

function metres(value: number | null) {
  if (value == null) return '—';
  if (value >= 1000) return `${(value / 1000).toFixed(2)} km`;
  return `${Math.round(value)} m`;
}

function haversineMetres(a: { latitude: number; longitude: number }, b: { latitude: number; longitude: number }) {
  const rad = Math.PI / 180;
  const lat1 = a.latitude * rad;
  const lat2 = b.latitude * rad;
  const dLat = (b.latitude - a.latitude) * rad;
  const dLon = (b.longitude - a.longitude) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

function checkpointPosition(checkpoint: RouteAdventureCheckpoint | null) {
  if (!checkpoint) return null;
  const latitude = Number(checkpoint.latitude);
  const longitude = Number(checkpoint.longitude);
  return Number.isFinite(latitude) && Number.isFinite(longitude) ? { latitude, longitude } : null;
}

export function AdventureLiveClient() {
  const [slug, setSlug] = useState<string | null>(null);
  const [detail, setDetail] = useState<PublicRouteDetail | null>(null);
  const [adventure, setAdventure] = useState<PublicRouteAdventure | null>(null);
  const [unlockedCheckpointIds, setUnlockedCheckpointIds] = useState<string[]>([]);
  const [gps, setGps] = useState<GpsTelemetry>({ status: 'idle', latitude: null, longitude: null, accuracy: null, timestamp: null });
  const [online, setOnline] = useState(true);
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
    if (!slug) return;
    let cancelled = false;
    setAdventure(null);
    loadPublicRouteAdventure(slug)
      .then((value) => { if (!cancelled) setAdventure(value); })
      .catch(() => { if (!cancelled) setAdventure(null); });
    return () => { cancelled = true; };
  }, [slug]);

  useEffect(() => {
    function syncGps(event: Event) {
      const value = (event as CustomEvent<Partial<GpsTelemetry> & { status?: GpsTelemetry['status'] }>).detail;
      if (!value?.status) return;
      setGps({
        status: value.status,
        latitude: typeof value.latitude === 'number' ? value.latitude : null,
        longitude: typeof value.longitude === 'number' ? value.longitude : null,
        accuracy: typeof value.accuracy === 'number' ? value.accuracy : null,
        timestamp: typeof value.timestamp === 'number' ? value.timestamp : null,
      });
    }
    function syncProgress(event: Event) {
      const value = (event as CustomEvent<AdventureProgressDetail>).detail;
      if (value && Array.isArray(value.unlockedCheckpointIds)) setUnlockedCheckpointIds(value.unlockedCheckpointIds);
    }
    function syncConnection() { setOnline(navigator.onLine); }

    syncConnection();
    window.addEventListener('magina:route-gps-telemetry', syncGps);
    window.addEventListener('magina:route-adventure-progress', syncProgress);
    window.addEventListener('online', syncConnection);
    window.addEventListener('offline', syncConnection);
    return () => {
      window.removeEventListener('magina:route-gps-telemetry', syncGps);
      window.removeEventListener('magina:route-adventure-progress', syncProgress);
      window.removeEventListener('online', syncConnection);
      window.removeEventListener('offline', syncConnection);
    };
  }, []);

  useEffect(() => {
    if (!slug && (noActive || authRequired || error)) setLoading(false);
  }, [slug, noActive, authRequired, error]);

  if (loading) return <main className={styles.page}><section className={styles.state}><strong>Preparando expedición…</strong><p>Cargando la ruta, checkpoints y estado GPS.</p></section></main>;

  if (authRequired && !slug) return <main className={styles.page}><section className={styles.state}><span className={styles.kicker}>MÁGINA AVENTURA</span><h1>Tu aventura en curso</h1><p>Inicia sesión para recuperar la actividad o expedición que tengas abierta.</p><Link className={styles.primary} href="/login?next=%2Faventura%2Fen-curso">Iniciar sesión</Link><Link className={styles.secondary} href="/aventura">Volver a Mágina Aventura</Link></section></main>;

  if (noActive && !slug) return <main className={styles.page}><section className={styles.state}><span className={styles.kicker}>MÁGINA AVENTURA</span><h1>No hay una aventura en curso</h1><p>Elige una aventura o una ruta real y empieza cuando estés preparado.</p><Link className={styles.primary} href="/aventura">Elegir aventura</Link><Link className={styles.secondary} href="/rutas">Ver rutas</Link></section></main>;

  if (error || !detail || !slug) return <main className={styles.page}><section className={styles.state}><span className={styles.kicker}>MÁGINA AVENTURA</span><h1>No podemos abrir esta expedición</h1><p>La ruta puede estar en revisión o no disponer ya de un track publicado y validado.</p><Link className={styles.primary} href="/aventura">Volver a Mágina Aventura</Link></section></main>;

  const route = detail.route;
  const unlocked = new Set(unlockedCheckpointIds);
  const orderedCheckpoints = adventure?.enabled
    ? adventure.checkpoints.slice().sort((a, b) => a.sort_order - b.sort_order)
    : [];
  const nextCheckpoint = orderedCheckpoints.find((checkpoint) => !unlocked.has(checkpoint.id)) ?? null;
  const nextPosition = checkpointPosition(nextCheckpoint);
  const distanceToNext = gps.status === 'tracking' && gps.latitude != null && gps.longitude != null && nextPosition
    ? haversineMetres({ latitude: gps.latitude, longitude: gps.longitude }, nextPosition)
    : null;
  const insideUnlockRadius = nextCheckpoint && distanceToNext != null
    ? distanceToNext <= nextCheckpoint.unlock_radius_m
    : false;

  function focusNextCheckpoint() {
    if (!nextCheckpoint || !nextPosition) return;
    window.dispatchEvent(new CustomEvent('magina:route-adventure-focus', { detail: {
      checkpointId: nextCheckpoint.id,
      latitude: nextPosition.latitude,
      longitude: nextPosition.longitude,
    } }));
    document.getElementById('mapa')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

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
      <a href="#telemetria">En vivo</a>
      <a href="#mapa">Mapa</a>
      <a href="#gps">GPS</a>
      <a href="#retos">Retos</a>
      <Link href={`/rutas/detalle?slug=${encodeURIComponent(slug)}`}>Ficha completa</Link>
    </nav>

    <section id="telemetria" className={styles.telemetry} aria-labelledby="live-telemetry-title">
      <div className={styles.sectionHead}>
        <div><span className={styles.kicker}>TELEMETRÍA DE MARCHA</span><h2 id="live-telemetry-title">Ahora mismo</h2></div>
        <p>Usa la misma señal GPS del grabador. No se abre un segundo seguimiento ni se guarda posición fuera de una actividad iniciada por ti.</p>
      </div>
      <div className={styles.telemetryGrid}>
        <article className={styles.telemetryCard}>
          <span>Señal GPS</span>
          <strong>{gpsStatus(gps.status)}</strong>
          <p>{gps.accuracy != null ? `Precisión aproximada ±${Math.round(gps.accuracy)} m` : 'Inicia o reanuda el recorrido para recibir posición viva.'}</p>
        </article>
        <article className={styles.telemetryCard}>
          <span>Siguiente checkpoint</span>
          <strong>{nextCheckpoint?.title ?? (orderedCheckpoints.length ? 'Todos descubiertos' : 'Sin retos activos')}</strong>
          <p>{nextCheckpoint ? `Etapa ${orderedCheckpoints.findIndex((checkpoint) => checkpoint.id === nextCheckpoint.id) + 1} · +${nextCheckpoint.points} XP` : 'La capa lúdica no bloquea el seguimiento seguro del track.'}</p>
        </article>
        <article className={`${styles.telemetryCard} ${insideUnlockRadius ? styles.readyCard : ''}`}>
          <span>Proximidad</span>
          <strong>{metres(distanceToNext)}</strong>
          <p>{nextCheckpoint ? (insideUnlockRadius ? `Ya estás dentro del radio de ${nextCheckpoint.unlock_radius_m} m.` : `Radio de desbloqueo: ${nextCheckpoint.unlock_radius_m} m.`) : 'No hay un checkpoint pendiente que medir.'}</p>
        </article>
        <article className={styles.telemetryCard}>
          <span>Conexión</span>
          <strong>{online ? 'Con conexión' : 'Sin conexión'}</strong>
          <p>{online ? 'Los retos y puntos pueden sincronizarse con el servidor.' : 'El GPS puede seguir midiendo, pero los envíos y desbloqueos esperarán conexión.'}</p>
        </article>
      </div>
      {nextCheckpoint && nextPosition ? <div className={styles.telemetryActions}>
        <button type="button" onClick={focusNextCheckpoint}>⌖ Ver siguiente checkpoint en el mapa</button>
        <span>{insideUnlockRadius ? '✓ Estás suficientemente cerca para intentar desbloquearlo.' : 'Acércate siguiendo siempre el track y la señalización real.'}</span>
      </div> : null}
    </section>

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
