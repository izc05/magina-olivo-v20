'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { ApiRequestError } from '../../../lib/api-client';
import {
  appendRouteActivityPoints,
  finishRouteActivity,
  loadActiveRouteActivity,
  pauseRouteActivity,
  resumeRouteActivity,
  startRouteActivity,
  type RouteActivity,
  type RouteActivityPointInput,
} from '../../../lib/route-activity-source';
import styles from '../routes-public.module.css';

function formatKm(value: number) {
  return `${(Math.max(0, value) / 1000).toFixed(2)} km`;
}

function formatDuration(seconds: number) {
  const safe = Math.max(0, Math.round(seconds));
  const h = Math.floor(safe / 3600);
  const m = Math.floor((safe % 3600) / 60);
  const s = safe % 60;
  if (h) return `${h} h ${m.toString().padStart(2, '0')} min`;
  return `${m}:${s.toString().padStart(2, '0')}`;
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

function errorMessage(error: unknown) {
  if (error instanceof ApiRequestError && error.payload && typeof error.payload === 'object') {
    const payload = error.payload as Record<string, unknown>;
    if (payload.error === 'authentication_required') return 'Necesitas iniciar sesión para guardar una actividad.';
    if (payload.error === 'another_activity_active') return 'Ya tienes otro recorrido activo. Termínalo o elimínalo desde tu perfil antes de iniciar este.';
    if (payload.error === 'activity_not_recording') return 'La actividad ya no está grabando. Actualiza el estado antes de continuar.';
    if (payload.error === 'activity_not_paused') return 'La actividad no está en pausa.';
  }
  return 'No se ha podido actualizar la actividad. Comprueba la conexión y vuelve a intentarlo.';
}

export function RouteActivityRecorder({ routeId, slug }: { routeId: string; slug: string }) {
  const [activity, setActivity] = useState<RouteActivity | null>(null);
  const [otherActivity, setOtherActivity] = useState<RouteActivity | null>(null);
  const [authRequired, setAuthRequired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [gpsState, setGpsState] = useState<'idle' | 'searching' | 'tracking' | 'error'>('idle');
  const [sessionSeconds, setSessionSeconds] = useState(0);
  const [liveDistanceM, setLiveDistanceM] = useState(0);
  const [liveElevationM, setLiveElevationM] = useState(0);

  const watchIdRef = useRef<number | null>(null);
  const sequenceCounterRef = useRef(0);
  const lastPointRef = useRef<{
    latitude: number;
    longitude: number;
    altitude: number | null;
    altitudeAccuracy: number | null;
    accuracy: number;
    timestamp: number;
  } | null>(null);
  const sendChainRef = useRef<Promise<unknown>>(Promise.resolve());

  function stopWatch() {
    if (watchIdRef.current !== null && 'geolocation' in navigator) navigator.geolocation.clearWatch(watchIdRef.current);
    watchIdRef.current = null;
    lastPointRef.current = null;
    setGpsState('idle');
  }

  function queuePoint(activityId: string, point: RouteActivityPointInput) {
    sendChainRef.current = sendChainRef.current
      .then(() => appendRouteActivityPoints(activityId, [point]))
      .catch((error) => {
        setGpsState('error');
        setMessage(errorMessage(error));
      });
  }

  function beginWatch(activityId: string) {
    if (!('geolocation' in navigator)) {
      setGpsState('error');
      setMessage('Este dispositivo o navegador no ofrece geolocalización.');
      return;
    }
    if (watchIdRef.current !== null) return;
    setGpsState('searching');
    watchIdRef.current = navigator.geolocation.watchPosition(
      (position) => {
        const now = Number.isFinite(position.timestamp) ? position.timestamp : Date.now();
        const coords = position.coords;
        const current = {
          latitude: coords.latitude,
          longitude: coords.longitude,
          altitude: coords.altitude,
          altitudeAccuracy: coords.altitudeAccuracy,
          accuracy: coords.accuracy,
          timestamp: now,
        };
        const previous = lastPointRef.current;
        if (previous) {
          const deltaSeconds = (current.timestamp - previous.timestamp) / 1000;
          const leg = haversineMetres(previous, current);
          if (
            deltaSeconds > 0 && deltaSeconds <= 120 && leg >= 2 && leg / deltaSeconds <= 15
            && current.accuracy <= 100 && previous.accuracy <= 100
          ) setLiveDistanceM((value) => value + leg);
          if (
            current.altitude !== null && previous.altitude !== null
            && current.altitudeAccuracy !== null && previous.altitudeAccuracy !== null
            && current.altitudeAccuracy <= 50 && previous.altitudeAccuracy <= 50
          ) {
            const gain = current.altitude - previous.altitude;
            if (gain > 3 && gain <= 50) setLiveElevationM((value) => value + gain);
          }
        }
        lastPointRef.current = current;
        sequenceCounterRef.current += 1;
        const sequence = Date.now() * 100 + (sequenceCounterRef.current % 100);
        queuePoint(activityId, {
          sequence,
          recorded_at: new Date(now).toISOString(),
          latitude: coords.latitude,
          longitude: coords.longitude,
          altitude_m: coords.altitude,
          horizontal_accuracy_m: coords.accuracy,
          vertical_accuracy_m: coords.altitudeAccuracy,
        });
        setGpsState('tracking');
      },
      () => {
        setGpsState('error');
        setMessage('No se puede leer el GPS. Revisa el permiso de ubicación y que el móvil tenga señal suficiente.');
      },
      { enableHighAccuracy: true, timeout: 20000, maximumAge: 5000 },
    );
  }

  useEffect(() => {
    let cancelled = false;
    loadActiveRouteActivity()
      .then(({ activity: current }) => {
        if (cancelled || !current) return;
        if (current.route_id === routeId) {
          setActivity(current);
          if (current.status === 'recording') beginWatch(current.id);
        } else {
          setOtherActivity(current);
        }
      })
      .catch((error) => {
        if (!cancelled && error instanceof ApiRequestError && error.status === 401) setAuthRequired(true);
      });
    return () => {
      cancelled = true;
      stopWatch();
    };
    // The route id is the lifecycle boundary for a foreground recording panel.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeId]);

  useEffect(() => {
    if (activity?.status !== 'recording') return;
    const timer = window.setInterval(() => setSessionSeconds((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [activity?.status]);

  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === 'hidden' && activity?.status === 'recording') {
        setMessage('Mantén Mágina Olivo abierta mientras grabas. El navegador puede suspender el GPS con la pantalla apagada; los huecos largos no se sumarán al recorrido.');
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [activity?.status]);

  async function start() {
    setBusy(true); setMessage(null);
    try {
      if (!('geolocation' in navigator)) throw new Error('geolocation_unavailable');
      const result = await startRouteActivity(routeId);
      setActivity(result.activity);
      setOtherActivity(null);
      setAuthRequired(false);
      setSessionSeconds(0);
      setLiveDistanceM(0);
      setLiveElevationM(0);
      beginWatch(result.activity.id);
      setMessage('Grabación iniciada. Solo se guardarán posiciones mientras esta actividad esté activa.');
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) setAuthRequired(true);
      setMessage(errorMessage(error));
    } finally { setBusy(false); }
  }

  async function pause() {
    if (!activity) return;
    setBusy(true); setMessage(null);
    stopWatch();
    try {
      await sendChainRef.current;
      const result = await pauseRouteActivity(activity.id);
      setActivity(result.activity);
      setMessage('Actividad en pausa. No se están enviando posiciones.');
    } catch (error) {
      setMessage(errorMessage(error));
    } finally { setBusy(false); }
  }

  async function resume() {
    if (!activity) return;
    setBusy(true); setMessage(null);
    try {
      const result = await resumeRouteActivity(activity.id);
      setActivity(result.activity);
      beginWatch(result.activity.id);
      setMessage('Grabación reanudada. Se ha abierto un segmento nuevo para no unir la pausa con una línea falsa.');
    } catch (error) {
      setMessage(errorMessage(error));
    } finally { setBusy(false); }
  }

  async function finish() {
    if (!activity) return;
    setBusy(true); setMessage(null);
    stopWatch();
    try {
      await sendChainRef.current;
      const result = await finishRouteActivity(activity.id);
      setActivity(result.activity);
      setMessage('Recorrido guardado en tu perfil. Puedes exportarlo a GPX o borrarlo cuando quieras.');
    } catch (error) {
      setMessage(errorMessage(error));
    } finally { setBusy(false); }
  }

  const recording = activity?.status === 'recording';
  const paused = activity?.status === 'paused';
  const completed = activity?.status === 'completed';

  return <section className={styles.communitySection} aria-labelledby="route-activity-title">
    <div className={styles.communityHeader}>
      <div><span className={styles.eyebrow}>Actividad privada · GPS voluntario</span><h2 id="route-activity-title">Grabar recorrido</h2></div>
      <p>Registra tu salida real por esta ruta. El GPS solo se conserva después de pulsar Iniciar y la V1 necesita que la web permanezca abierta.</p>
    </div>
    <div className={styles.notice}>No hay seguimiento oculto ni ranking de velocidad. La actividad es privada y no modifica por sí sola los kilómetros conquistados del Pasaporte.</div>

    {authRequired ? <article className={styles.infoCard}><h3>Guárdalo en tu perfil</h3><p>Necesitas una cuenta para asociar y proteger el recorrido.</p><Link className={styles.primaryAction} href={`/login?next=${encodeURIComponent(`/rutas/detalle?slug=${slug}`)}`}>Iniciar sesión</Link></article> : null}
    {otherActivity ? <article className={styles.infoCard}><h3>Ya hay una actividad abierta</h3><p>{otherActivity.route_name ?? 'Otro recorrido'} está {otherActivity.status === 'paused' ? 'en pausa' : 'grabándose'}.</p><Link className={styles.secondaryAction} href="/perfil">Gestionar en mi perfil</Link></article> : null}

    {activity ? <div className={styles.infoGrid}>
      <article className={styles.infoCard}><h3>Estado</h3><strong>{recording ? '● Grabando' : paused ? 'Ⅱ En pausa' : '✓ Finalizada'}</strong><p>{gpsState === 'tracking' ? 'GPS activo' : gpsState === 'searching' ? 'Buscando GPS…' : gpsState === 'error' ? 'GPS sin señal' : 'GPS detenido'}</p></article>
      <article className={styles.infoCard}><h3>Distancia</h3><strong>{completed ? formatKm(activity.distance_m) : formatKm(liveDistanceM)}</strong><p>{completed ? 'Calculada y filtrada por servidor' : 'Estimación en esta sesión'}</p></article>
      <article className={styles.infoCard}><h3>Tiempo</h3><strong>{completed ? formatDuration(activity.duration_seconds) : formatDuration(sessionSeconds)}</strong><p>{completed ? 'Tiempo con muestras GPS válidas' : 'Tiempo visible de esta sesión'}</p></article>
      <article className={styles.infoCard}><h3>Desnivel GPS</h3><strong>{completed ? (activity.elevation_gain_m === null ? '—' : `${Math.round(activity.elevation_gain_m)} m+`) : `${Math.round(liveElevationM)} m+`}</strong><p>Solo con altitud suficientemente precisa</p></article>
    </div> : null}

    {!activity && !authRequired && !otherActivity ? <div className={styles.actionRow}><button className={styles.primaryAction} type="button" disabled={busy} onClick={start}>{busy ? 'Iniciando…' : '▶ Iniciar recorrido'}</button></div> : null}
    {recording ? <div className={styles.actionRow}><button className={styles.secondaryAction} type="button" disabled={busy} onClick={pause}>Ⅱ Pausar</button><button className={styles.primaryAction} type="button" disabled={busy} onClick={finish}>■ Finalizar</button></div> : null}
    {paused ? <div className={styles.actionRow}><button className={styles.primaryAction} type="button" disabled={busy} onClick={resume}>▶ Reanudar</button><button className={styles.secondaryAction} type="button" disabled={busy} onClick={finish}>■ Finalizar</button></div> : null}
    {completed ? <div className={styles.actionRow}><Link className={styles.primaryAction} href="/perfil">Ver historial en mi perfil</Link></div> : null}
    {message ? <p role="status" aria-live="polite">{message}</p> : null}
  </section>;
}
