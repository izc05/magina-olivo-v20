'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ApiRequestError } from '../../../lib/api-client';
import { loadPublicRoutes, type PublicRouteSummary } from '../../../lib/public-routes-source';
import {
  appendRouteActivityPoint,
  deleteRouteActivity,
  finishRouteActivity,
  loadCurrentRouteActivity,
  loadRouteActivitySummary,
  pauseRouteActivity,
  resumeRouteActivity,
  startRouteActivity,
  type RouteActivity,
  type RouteActivitySummary,
} from '../../../lib/route-activity-source';
import styles from './activity.module.css';

function km(value: number) {
  return `${(Math.max(0, value) / 1000).toFixed(value >= 100000 ? 0 : 2)} km`;
}

function duration(seconds: number) {
  const safe = Math.max(0, Math.round(seconds));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const secs = safe % 60;
  if (hours) return `${hours} h ${String(minutes).padStart(2, '0')} min`;
  return `${minutes}:${String(secs).padStart(2, '0')}`;
}

function dateLabel(value: string) {
  return new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

function currentSeconds(activity: RouteActivity | null, now: number) {
  if (!activity) return 0;
  let total = activity.active_seconds;
  if (activity.status === 'active' && activity.active_started_at) {
    total += Math.max(0, Math.floor((now - new Date(activity.active_started_at).getTime()) / 1000));
  }
  return total;
}

function requestPosition() {
  return new Promise<GeolocationPosition>((resolve, reject) => {
    if (!('geolocation' in navigator)) return reject(new Error('geolocation_unavailable'));
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 20000,
      maximumAge: 0,
    });
  });
}

function isGeolocationError(error: unknown): error is GeolocationPositionError {
  return typeof error === 'object' && error !== null && 'code' in error && typeof (error as { code?: unknown }).code === 'number';
}

function activityError(error: unknown) {
  if (error instanceof ApiRequestError) {
    const payload = error.payload && typeof error.payload === 'object' ? error.payload as Record<string, unknown> : null;
    if (error.status === 401) return 'Necesitas iniciar sesión para grabar y guardar un recorrido.';
    if (payload?.error === 'activity_already_open') return 'Ya tienes un recorrido abierto. Lo he recuperado para que puedas continuarlo.';
    if (payload?.error === 'route_not_recordable') return 'Esa ruta no está publicada con track validado y no puede asociarse a la grabación.';
    if (payload?.error === 'activity_not_active') return 'El recorrido ya no está activo. Actualiza el estado antes de continuar.';
  }
  if (isGeolocationError(error)) {
    if (error.code === 1) return 'No has concedido permiso de ubicación. No se ha iniciado ninguna grabación.';
    if (error.code === 2) return 'El dispositivo no puede obtener una posición GPS fiable ahora mismo.';
    if (error.code === 3) return 'La ubicación está tardando demasiado. Prueba de nuevo en una zona con mejor cobertura.';
  }
  if (error instanceof Error && error.message === 'geolocation_unavailable') return 'Este dispositivo no ofrece geolocalización.';
  return 'No se ha podido completar la operación. Comprueba la conexión y vuelve a intentarlo.';
}

export function RouteActivityRecorder() {
  const [routes, setRoutes] = useState<PublicRouteSummary[]>([]);
  const [selectedRouteId, setSelectedRouteId] = useState('');
  const [activity, setActivity] = useState<RouteActivity | null>(null);
  const [summary, setSummary] = useState<RouteActivitySummary | null>(null);
  const [authRequired, setAuthRequired] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [gpsMessage, setGpsMessage] = useState('GPS detenido');
  const [busy, setBusy] = useState(false);
  const [tick, setTick] = useState(Date.now());
  const watchIdRef = useRef<number | null>(null);
  const lastSentRef = useRef(0);
  const sendingRef = useRef(false);

  const stopWatching = useCallback(() => {
    if (watchIdRef.current != null && 'geolocation' in navigator) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    sendingRef.current = false;
    setGpsMessage('GPS detenido');
  }, []);

  const refreshPrivate = useCallback(async () => {
    try {
      const [current, nextSummary] = await Promise.all([
        loadCurrentRouteActivity(),
        loadRouteActivitySummary(),
      ]);
      setActivity(current.activity);
      setSummary(nextSummary);
      setAuthRequired(false);
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) {
        setAuthRequired(true);
        setActivity(null);
        setSummary(null);
      } else {
        setMessage(activityError(error));
      }
    }
  }, []);

  useEffect(() => {
    void loadPublicRoutes().then(setRoutes).catch(() => setRoutes([]));
    void refreshPrivate();
  }, [refreshPrivate]);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    stopWatching();
    if (!activity || activity.status !== 'active' || authRequired) return;
    if (!('geolocation' in navigator)) {
      setGpsMessage('Geolocalización no disponible');
      return;
    }

    setGpsMessage('Buscando señal GPS…');
    watchIdRef.current = navigator.geolocation.watchPosition(
      (position) => {
        const now = Date.now();
        const accuracy = Math.round(position.coords.accuracy);
        setGpsMessage(accuracy <= 25 ? `GPS bueno · ±${accuracy} m` : accuracy <= 80 ? `GPS aceptable · ±${accuracy} m` : `GPS débil · ±${accuracy} m`);
        if (now - lastSentRef.current < 8000 || sendingRef.current) return;
        lastSentRef.current = now;
        sendingRef.current = true;
        void appendRouteActivityPoint(activity.id, {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracy_m: position.coords.accuracy,
          altitude_m: position.coords.altitude,
          recorded_at: new Date(position.timestamp).toISOString(),
        }).then((result) => {
          setActivity(result.activity);
          if (!result.accepted && result.reason === 'low_accuracy') setMessage('Señal GPS demasiado imprecisa: ese punto no se ha guardado ni suma distancia.');
          if (!result.accepted && result.reason === 'implausible_jump') setMessage('Se ha descartado un salto GPS anómalo para no inflar la distancia.');
        }).catch((error) => setMessage(activityError(error))).finally(() => {
          sendingRef.current = false;
        });
      },
      (error) => {
        setGpsMessage('Sin señal GPS');
        setMessage(activityError(error));
      },
      { enableHighAccuracy: true, timeout: 20000, maximumAge: 5000 },
    );

    return stopWatching;
  }, [activity?.id, activity?.status, authRequired, stopWatching]);

  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === 'hidden' && activity?.status === 'active') {
        stopWatching();
        setMessage('Grabación pausada al salir de la pantalla para evitar seguimiento oculto en segundo plano.');
        void pauseRouteActivity(activity.id, true)
          .then((result) => setActivity(result.activity))
          .catch(() => undefined);
      }
      if (document.visibilityState === 'visible' && !authRequired) void refreshPrivate();
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [activity?.id, activity?.status, authRequired, refreshPrivate, stopWatching]);

  const liveSeconds = currentSeconds(activity, tick);
  const speed = useMemo(() => liveSeconds > 0 && activity ? (activity.distance_m / 1000) / (liveSeconds / 3600) : 0, [activity, liveSeconds]);

  async function start() {
    setBusy(true); setMessage(null);
    try {
      const first = await requestPosition();
      const result = await startRouteActivity(selectedRouteId || null);
      setActivity(result.activity);
      setAuthRequired(false);
      const seeded = await appendRouteActivityPoint(result.activity.id, {
        latitude: first.coords.latitude,
        longitude: first.coords.longitude,
        accuracy_m: first.coords.accuracy,
        altitude_m: first.coords.altitude,
        recorded_at: new Date(first.timestamp).toISOString(),
      });
      setActivity(seeded.activity);
      setMessage('Recorrido iniciado. Mantén esta pantalla activa para registrar el trayecto.');
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 409) await refreshPrivate();
      if (error instanceof ApiRequestError && error.status === 401) setAuthRequired(true);
      setMessage(activityError(error));
    } finally { setBusy(false); }
  }

  async function pause() {
    if (!activity) return;
    setBusy(true); stopWatching();
    try {
      const result = await pauseRouteActivity(activity.id);
      setActivity(result.activity);
      setMessage('Recorrido pausado. Mientras esté pausado no se guardan posiciones ni distancia.');
    } catch (error) { setMessage(activityError(error)); }
    finally { setBusy(false); }
  }

  async function resume() {
    if (!activity) return;
    setBusy(true); setMessage(null);
    try {
      const first = await requestPosition();
      const result = await resumeRouteActivity(activity.id);
      setActivity(result.activity);
      const seeded = await appendRouteActivityPoint(result.activity.id, {
        latitude: first.coords.latitude,
        longitude: first.coords.longitude,
        accuracy_m: first.coords.accuracy,
        altitude_m: first.coords.altitude,
        recorded_at: new Date(first.timestamp).toISOString(),
      });
      setActivity(seeded.activity);
      setMessage('Grabación reanudada.');
    } catch (error) { setMessage(activityError(error)); }
    finally { setBusy(false); }
  }

  async function finish() {
    if (!activity) return;
    setBusy(true); stopWatching();
    try {
      const result = await finishRouteActivity(activity.id);
      setMessage(`Recorrido guardado: ${km(result.activity.distance_m)} en ${duration(result.activity.active_seconds)}.`);
      setActivity(null);
      setSummary(await loadRouteActivitySummary());
    } catch (error) { setMessage(activityError(error)); }
    finally { setBusy(false); }
  }

  async function discardCurrent() {
    if (!activity) return;
    setBusy(true); stopWatching();
    try {
      await deleteRouteActivity(activity.id);
      setActivity(null);
      setMessage('Grabación descartada y sus puntos GPS eliminados.');
      setSummary(await loadRouteActivitySummary());
    } catch (error) { setMessage(activityError(error)); }
    finally { setBusy(false); }
  }

  async function removeHistory(id: string) {
    setBusy(true);
    try {
      await deleteRouteActivity(id);
      setSummary(await loadRouteActivitySummary());
      setMessage('Recorrido eliminado junto con su track GPS privado.');
    } catch (error) { setMessage(activityError(error)); }
    finally { setBusy(false); }
  }

  return <main className={styles.shell}>
    <Link href="/aventura" className={styles.back}>← Mágina Aventura</Link>
    <section className={styles.hero}>
      <span className={styles.eyebrow}>Actividad privada · GPS voluntario</span>
      <h1>Grabar recorrido</h1>
      <p>Registra distancia y tiempo reales cuando tú lo decidas. Esta función está separada de los kilómetros conquistados del juego: repetir una ruta puede sumar actividad deportiva, pero no infla el territorio completado.</p>
      <div className={styles.privacy}><strong>Privacidad por diseño.</strong> El GPS solo se guarda después de pulsar «Iniciar grabación». Al salir de esta pantalla se pausa automáticamente. El track es privado, no aparece en rankings públicos y puedes borrarlo.</div>
    </section>

    {message ? <p className={styles.message} role="status" aria-live="polite">{message}</p> : null}

    {authRequired ? <section className={styles.card}>
      <h2>Necesitas tu cuenta</h2><p>La grabación contiene datos privados de ubicación y debe quedar vinculada a tu usuario.</p>
      <Link className={styles.login} href={`/login?next=${encodeURIComponent('/aventura/actividad')}`}>Iniciar sesión →</Link>
    </section> : null}

    {!authRequired ? <div className={styles.grid}>
      <section className={styles.card}>
        <div className={styles.statusRow}>
          <span className={`${styles.chip} ${activity?.status === 'active' ? styles.active : activity?.status === 'paused' ? styles.paused : ''}`}>{activity?.status === 'active' ? '● Grabando' : activity?.status === 'paused' ? 'Ⅱ Pausado' : '○ Sin grabación'}</span>
          <span className={styles.chip}>{gpsMessage}</span>
        </div>

        {!activity ? <>
          <h2>Nuevo recorrido</h2>
          <p>Puedes asociarlo a una ruta publicada y validada o grabarlo como recorrido libre.</p>
          <label>
            <span className={styles.note}>Ruta asociada</span>
            <select className={styles.select} value={selectedRouteId} onChange={(event) => setSelectedRouteId(event.target.value)} disabled={busy}>
              <option value="">Recorrido libre</option>
              {routes.map((route) => <option key={route.id} value={route.id}>{route.name}{route.municipality_name ? ` · ${route.municipality_name}` : ''}</option>)}
            </select>
          </label>
          <div className={styles.actions}><button type="button" className={styles.primary} onClick={start} disabled={busy}>{busy ? 'Preparando GPS…' : '▶ Iniciar grabación'}</button></div>
        </> : <>
          <h2>{activity.route_name ?? 'Recorrido libre'}</h2>
          <div className={styles.metrics}>
            <div className={styles.metric}><strong>{km(activity.distance_m)}</strong><span>Distancia real</span></div>
            <div className={styles.metric}><strong>{duration(liveSeconds)}</strong><span>Tiempo activo</span></div>
            <div className={styles.metric}><strong>{speed.toFixed(1)} km/h</strong><span>Media aproximada</span></div>
            <div className={styles.metric}><strong>{activity.point_count}</strong><span>Puntos válidos</span></div>
          </div>
          <p className={styles.quality}>Los puntos con precisión peor de ±80 m, saltos GPS anómalos y muestras demasiado seguidas no suman distancia.</p>
          <div className={styles.actions}>
            {activity.status === 'active' ? <button type="button" className={styles.secondary} onClick={pause} disabled={busy}>Ⅱ Pausar</button> : <button type="button" className={styles.primary} onClick={resume} disabled={busy}>▶ Reanudar</button>}
            <button type="button" className={styles.primary} onClick={finish} disabled={busy}>■ Finalizar y guardar</button>
            <button type="button" className={styles.danger} onClick={discardCurrent} disabled={busy}>Descartar</button>
          </div>
        </>}
      </section>

      <section className={styles.card}>
        <h2>Actividad acumulada</h2>
        {summary ? <>
          <div className={styles.metrics}>
            <div className={styles.metric}><strong>{km(summary.summary.recorded_distance_m)}</strong><span>Km registrados</span></div>
            <div className={styles.metric}><strong>{summary.summary.completed_activities}</strong><span>Recorridos</span></div>
            <div className={styles.metric}><strong>{duration(summary.summary.recorded_active_seconds)}</strong><span>Tiempo activo</span></div>
            <div className={styles.metric}><strong>{km(summary.summary.longest_activity_m)}</strong><span>Más largo</span></div>
          </div>
          <p className={styles.note}>{summary.privacy}</p>
        </> : <p className={styles.empty}>Todavía no hay actividad registrada.</p>}
      </section>
    </div> : null}

    {!authRequired && summary ? <section className={styles.card} style={{ marginTop: 18 }}>
      <h2>Historial privado</h2>
      {summary.recent.length ? <div className={styles.history}>{summary.recent.map((item) => <article className={styles.historyItem} key={item.id}>
        <div className={styles.historyHead}><strong>{item.route_name ?? 'Recorrido libre'}</strong><span>{dateLabel(item.ended_at ?? item.started_at)}</span></div>
        <div className={styles.historyMeta}><span>{km(item.distance_m)}</span><span>{duration(item.active_seconds)}</span><span>{item.point_count} puntos GPS</span></div>
        <div className={styles.historyActions}>
          <Link className={styles.historyLink} href={`/aventura/actividad/${encodeURIComponent(item.id)}`}>Ver mapa privado →</Link>
          <button type="button" className={styles.tinyDanger} disabled={busy} onClick={() => void removeHistory(item.id)}>Eliminar track y actividad</button>
        </div>
      </article>)}</div> : <p className={styles.empty}>Cuando finalices tu primer recorrido aparecerá aquí.</p>}
    </section> : null}
  </main>;
}