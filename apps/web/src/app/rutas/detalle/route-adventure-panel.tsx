'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { ApiRequestError } from '../../../lib/api-client';
import {
  completeRouteAdventure,
  loadPublicRouteAdventure,
  loadRouteAdventureProgress,
  startRouteAdventure,
  unlockRouteAdventureCheckpoint,
  type PublicRouteAdventure,
  type RouteAdventureCheckpoint,
  type RouteAdventureProgress,
} from '../../../lib/public-routes-source';
import styles from '../routes-public.module.css';

function km(value: number | string | null) {
  if (value == null) return null;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? `${(numeric / 1000).toFixed(1)} km` : null;
}

function checkpointKind(kind: RouteAdventureCheckpoint['kind']) {
  return ({
    landmark: 'Lugar', trivia: 'Reto', observation: 'Observación', photo: 'Foto',
    collection: 'Coleccionable', rest: 'Descanso',
  } satisfies Record<RouteAdventureCheckpoint['kind'], string>)[kind];
}

function badgeLabel(code: string) {
  if (code === 'primer_paso') return 'Primer paso';
  if (code === 'explorador_magina') return 'Explorador de Mágina';
  if (code === 'ruta_100') return 'Ruta al 100 %';
  return code.replaceAll('_', ' ');
}

function locateUser() {
  return new Promise<{ latitude: number; longitude: number }>((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(new Error('geolocation_unavailable'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ latitude: position.coords.latitude, longitude: position.coords.longitude }),
      reject,
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 5000 },
    );
  });
}

function apiMessage(error: unknown) {
  if (error instanceof ApiRequestError && error.payload && typeof error.payload === 'object') {
    const payload = error.payload as Record<string, unknown>;
    if (payload.error === 'checkpoint_too_far') {
      const distance = typeof payload.distance_m === 'number' ? payload.distance_m : null;
      const radius = typeof payload.unlock_radius_m === 'number' ? payload.unlock_radius_m : null;
      return distance != null && radius != null
        ? `Aún estás a unos ${distance} m del punto. Acércate a menos de ${radius} m para desbloquearlo.`
        : 'Todavía no estás suficientemente cerca de este punto.';
    }
    if (payload.error === 'adventure_incomplete') return 'Todavía quedan checkpoints obligatorios por completar.';
    if (payload.error === 'authentication_required') return 'Necesitas iniciar sesión para guardar el progreso.';
  }
  if (error instanceof Error && error.message === 'geolocation_unavailable') return 'Este dispositivo no ofrece geolocalización.';
  return 'No se ha podido actualizar la aventura. Comprueba la conexión y vuelve a intentarlo.';
}

function focusCheckpoint(checkpoint: RouteAdventureCheckpoint) {
  const latitude = Number(checkpoint.latitude);
  const longitude = Number(checkpoint.longitude);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return;
  window.dispatchEvent(new CustomEvent('magina:route-elevation-focus', { detail: {
    latitude,
    longitude,
    distance_m: Number(checkpoint.distance_m ?? 0),
    elevation_m: 0,
    grade_percent: null,
  } }));
  document.querySelector('[aria-label="Mapa inteligente del trazado de la ruta"]')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

export function RouteAdventurePanel({ routeId, slug }: { routeId: string; slug: string }) {
  const [definition, setDefinition] = useState<PublicRouteAdventure | null>(null);
  const [progress, setProgress] = useState<RouteAdventureProgress | null>(null);
  const [loading, setLoading] = useState(true);
  const [authRequired, setAuthRequired] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    loadPublicRouteAdventure(slug)
      .then(async (value) => {
        if (cancelled) return;
        setDefinition(value);
        if (!value.enabled) return;
        try {
          const current = await loadRouteAdventureProgress(routeId);
          if (!cancelled) setProgress(current);
        } catch (error) {
          if (!cancelled && error instanceof ApiRequestError && error.status === 401) setAuthRequired(true);
        }
      })
      .catch(() => { if (!cancelled) setDefinition(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [routeId, slug]);

  const unlocked = useMemo(() => new Set(progress?.unlocks.map((item) => item.checkpoint_id) ?? []), [progress]);
  const percent = progress?.stats.total_checkpoints
    ? Math.round((progress.stats.unlocked_checkpoints / progress.stats.total_checkpoints) * 100)
    : 0;

  if (loading) return null;
  if (!definition?.enabled || !definition.adventure) return null;

  async function start() {
    setBusy('start'); setMessage(null);
    try {
      const next = await startRouteAdventure(routeId);
      setProgress(next); setAuthRequired(false);
      setMessage('Aventura iniciada. Los checkpoints se desbloquean cuando estés físicamente cerca.');
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) setAuthRequired(true);
      setMessage(apiMessage(error));
    } finally { setBusy(null); }
  }

  async function unlock(checkpoint: RouteAdventureCheckpoint) {
    if (checkpoint.question && !answers[checkpoint.id]) {
      setMessage('Elige una respuesta antes de comprobar este reto.');
      return;
    }
    setBusy(checkpoint.id); setMessage('Comprobando tu posición…');
    try {
      const position = await locateUser();
      const result = await unlockRouteAdventureCheckpoint(routeId, checkpoint.id, {
        ...position,
        answer_key: answers[checkpoint.id] ?? null,
      });
      if (!result.unlocked) {
        setMessage('Respuesta incorrecta. Puedes volver a intentarlo cuando quieras.');
        return;
      }
      setProgress(result.progress);
      setMessage(`¡${checkpoint.title} desbloqueado! +${checkpoint.points} puntos.`);
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) setAuthRequired(true);
      setMessage(apiMessage(error));
    } finally { setBusy(null); }
  }

  async function complete() {
    setBusy('complete'); setMessage(null);
    try {
      const result = await completeRouteAdventure(routeId);
      setProgress(result.progress);
      setMessage(definition.adventure?.completion_message ?? '¡Aventura completada! Has superado los checkpoints obligatorios.');
    } catch (error) {
      setMessage(apiMessage(error));
    } finally { setBusy(null); }
  }

  const active = progress?.run?.status === 'active';
  const completed = progress?.run?.status === 'completed';

  return <section className={styles.communitySection} aria-labelledby="route-adventure-title">
    <div className={styles.communityHeader}>
      <div><span className={styles.eyebrow}>Explora jugando</span><h2 id="route-adventure-title">{definition.adventure.title}</h2></div>
      <p>{definition.adventure.intro ?? 'Recorre la ruta real, descubre puntos del territorio y desbloquea pequeños retos por el camino.'}</p>
    </div>

    <div className={styles.notice}>{definition.notice ?? 'El juego no sustituye la navegación ni los avisos oficiales.'}</div>

    {progress?.run ? <div className={styles.infoGrid}>
      <article className={styles.infoCard}><h3>Progreso</h3><strong>{progress.stats.unlocked_checkpoints}/{progress.stats.total_checkpoints}</strong><p>{percent}% de checkpoints descubiertos</p></article>
      <article className={styles.infoCard}><h3>Puntuación</h3><strong>{progress.run.score}/{progress.stats.total_points}</strong><p>Puntos conseguidos en esta aventura</p></article>
      <article className={styles.infoCard}><h3>Obligatorios</h3><strong>{progress.stats.required_remaining}</strong><p>{progress.stats.required_remaining === 0 ? 'Ya puedes cerrar la aventura.' : 'Aún pendientes para completar el recorrido lúdico.'}</p></article>
    </div> : null}

    {progress?.badges.length ? <div className={styles.actionRow} aria-label="Insignias conseguidas">
      {progress.badges.map((badge) => <span key={badge} className={styles.secondaryAction}>{badgeLabel(badge)}</span>)}
    </div> : null}

    {authRequired ? <div className={styles.infoCard}>
      <h3>Guarda tu aventura</h3><p>Puedes ver los retos sin cuenta, pero para iniciar el recorrido y conservar puntos necesitas iniciar sesión.</p>
      <Link className={styles.primaryAction} href={`/login?next=${encodeURIComponent(`/rutas/detalle?slug=${slug}`)}`}>Iniciar sesión</Link>
    </div> : null}

    {!progress?.run && !authRequired ? <div className={styles.actionRow}>
      <button className={styles.primaryAction} type="button" disabled={busy !== null} onClick={start}>{busy === 'start' ? 'Iniciando…' : 'Iniciar Modo Aventura'}</button>
    </div> : null}

    {message ? <p role="status" aria-live="polite">{message}</p> : null}

    <div className={styles.reviewGrid}>
      {definition.checkpoints.map((checkpoint, index) => {
        const done = unlocked.has(checkpoint.id);
        const options = Array.isArray(checkpoint.answer_options)
          ? checkpoint.answer_options.filter((option) => option && typeof option.key === 'string' && typeof option.label === 'string')
          : [];
        return <article className={styles.reviewCard} key={checkpoint.id}>
          <div className={styles.reviewMeta}>
            <strong>{done ? '✓ ' : ''}Etapa {index + 1} · {checkpointKind(checkpoint.kind)}</strong>
            <span>{checkpoint.is_required ? 'Obligatorio' : 'Extra'} · {checkpoint.points} pt</span>
          </div>
          <h3>{checkpoint.title}</h3>
          {checkpoint.description ? <p>{checkpoint.description}</p> : null}
          <small>{km(checkpoint.distance_m) ? `${km(checkpoint.distance_m)} · ` : ''}radio de desbloqueo {checkpoint.unlock_radius_m} m</small>
          {checkpoint.question && !done ? <fieldset style={{ border: 0, padding: 0, margin: '16px 0 0' }}>
            <legend><strong>{checkpoint.question}</strong></legend>
            {options.map((option) => <label key={option.key} style={{ display: 'block', marginTop: 8 }}>
              <input type="radio" name={`answer-${checkpoint.id}`} value={option.key} checked={answers[checkpoint.id] === option.key} onChange={() => setAnswers((current) => ({ ...current, [checkpoint.id]: option.key }))} />{' '}{option.label}
            </label>)}
            {checkpoint.hint ? <small style={{ display: 'block', marginTop: 8 }}>Pista: {checkpoint.hint}</small> : null}
          </fieldset> : null}
          <div className={styles.actionRow} style={{ marginTop: 12 }}>
            <button className={styles.secondaryAction} type="button" onClick={() => focusCheckpoint(checkpoint)}>Ver punto en mapa</button>
            {active && !done ? <button className={styles.primaryAction} type="button" disabled={busy !== null} onClick={() => unlock(checkpoint)}>{busy === checkpoint.id ? 'Comprobando…' : 'Estoy aquí'}</button> : null}
            {done ? <span>Desbloqueado</span> : null}
          </div>
        </article>;
      })}
    </div>

    {active && progress.stats.required_remaining === 0 ? <div className={styles.actionRow}>
      <button className={styles.primaryAction} type="button" disabled={busy !== null} onClick={complete}>{busy === 'complete' ? 'Cerrando aventura…' : 'Completar aventura'}</button>
    </div> : null}

    {completed ? <div className={styles.infoCard}>
      <h3>Recorrido lúdico completado</h3><p>{definition.adventure.completion_message ?? 'Has desbloqueado todos los checkpoints necesarios de esta aventura.'}</p>
      <p><small>Esto certifica el progreso del mini‑juego, no una validación oficial de actividad deportiva ni del estado del sendero.</small></p>
    </div> : null}
  </section>;
}
