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
  type AdventureCollectionCategory,
  type AdventureRarity,
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
    landmark: 'Lugares', trivia: 'Retos', observation: 'Observaciones', photo: 'Recuerdos',
    collection: 'Coleccionables', rest: 'Descansos',
  } satisfies Record<RouteAdventureCheckpoint['kind'], string>)[kind];
}

function checkpointIcon(kind: RouteAdventureCheckpoint['kind']) {
  return ({
    landmark: '⌖', trivia: '?', observation: '◉', photo: '▣', collection: '✦', rest: '⌂',
  } satisfies Record<RouteAdventureCheckpoint['kind'], string>)[kind];
}

function albumCategoryLabel(category: AdventureCollectionCategory) {
  return ({
    flora: 'Flora', fauna: 'Fauna', heritage: 'Patrimonio', olive_culture: 'Olivar',
    tradition: 'Tradiciones', landscape: 'Paisaje',
  } satisfies Record<AdventureCollectionCategory, string>)[category];
}

function rarityLabel(rarity: AdventureRarity) {
  return ({ common: 'Común', uncommon: 'Poco común', rare: 'Raro', legendary: 'Legendario' } satisfies Record<AdventureRarity, string>)[rarity];
}

function explorerRank(percent: number, completed: boolean) {
  if (completed && percent >= 100) return 'Guardián de la ruta';
  if (percent >= 75) return 'Explorador avanzado';
  if (percent >= 50) return 'Explorador de Mágina';
  if (percent >= 25) return 'Caminante curioso';
  if (percent > 0) return 'Primeros pasos';
  return 'Aventura por comenzar';
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
    if (payload.error === 'checkpoint_locked') {
      const remaining = typeof payload.required_previous_remaining === 'number' ? payload.required_previous_remaining : null;
      return remaining && remaining > 1
        ? `Esta etapa está bloqueada. Completa antes las ${remaining} etapas obligatorias anteriores.`
        : 'Esta etapa está bloqueada. Completa primero la etapa obligatoria anterior.';
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
  window.dispatchEvent(new CustomEvent('magina:route-adventure-focus', { detail: {
    checkpointId: checkpoint.id,
    latitude,
    longitude,
  } }));
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

  useEffect(() => {
    window.dispatchEvent(new CustomEvent('magina:route-adventure-progress', { detail: {
      unlockedCheckpointIds: progress?.unlocks.map((item) => item.checkpoint_id) ?? [],
    } }));
  }, [progress]);

  const unlocked = useMemo(() => new Set(progress?.unlocks.map((item) => item.checkpoint_id) ?? []), [progress]);
  const percent = progress?.stats.total_checkpoints
    ? Math.round((progress.stats.unlocked_checkpoints / progress.stats.total_checkpoints) * 100)
    : 0;

  if (loading) return null;
  if (!definition?.enabled || !definition.adventure) return null;
  const adventure = definition.adventure;
  const progressionMode = adventure.progression_mode;
  const isLocked = (index: number) => progressionMode === 'linear'
    && definition.checkpoints.slice(0, index).some((item) => item.is_required && !unlocked.has(item.id));

  async function start() {
    setBusy('start'); setMessage(null);
    try {
      const next = await startRouteAdventure(routeId);
      setProgress(next); setAuthRequired(false);
      setMessage(progressionMode === 'linear'
        ? 'Aventura iniciada. Avanza por etapas: cada reto obligatorio abre el siguiente tramo.'
        : 'Aventura iniciada. Los checkpoints se desbloquean cuando estés físicamente cerca.');
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
      const albumText = checkpoint.collection_category
        ? ` · ${albumCategoryLabel(checkpoint.collection_category)} · ${rarityLabel(checkpoint.rarity)}`
        : '';
      setMessage(`¡${checkpoint.title} desbloqueado! +${checkpoint.points} XP${albumText}.`);
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
      setMessage(adventure.completion_message ?? '¡Aventura completada! Has superado los checkpoints obligatorios.');
    } catch (error) {
      setMessage(apiMessage(error));
    } finally { setBusy(null); }
  }

  const active = progress?.run?.status === 'active';
  const completed = progress?.run?.status === 'completed';
  const effectiveUnlocked = progress?.stats.unlocked_checkpoints ?? 0;
  const effectiveTotal = progress?.stats.total_checkpoints ?? definition.checkpoints.length;
  const effectivePercent = effectiveTotal > 0 ? Math.round((effectiveUnlocked / effectiveTotal) * 100) : 0;
  const collectionKinds = (['landmark', 'trivia', 'observation', 'photo', 'collection', 'rest'] as const)
    .map((kind) => {
      const checkpoints = definition.checkpoints.filter((checkpoint) => checkpoint.kind === kind);
      return {
        kind,
        total: checkpoints.length,
        unlocked: checkpoints.filter((checkpoint) => unlocked.has(checkpoint.id)).length,
      };
    })
    .filter((entry) => entry.total > 0);
  const albumCategories = (['flora', 'fauna', 'heritage', 'olive_culture', 'tradition', 'landscape'] as const)
    .map((category) => {
      const checkpoints = definition.checkpoints.filter((checkpoint) => checkpoint.collection_category === category);
      return {
        category,
        total: checkpoints.length,
        unlocked: checkpoints.filter((checkpoint) => unlocked.has(checkpoint.id)).length,
      };
    })
    .filter((entry) => entry.total > 0);

  return <section className={styles.communitySection} aria-labelledby="route-adventure-title">
    <div className={styles.communityHeader}>
      <div><span className={styles.eyebrow}>Explora jugando · {progressionMode === 'linear' ? 'Por etapas' : 'Modo libre'}</span><h2 id="route-adventure-title">{adventure.title}</h2></div>
      <p>{adventure.intro ?? 'Recorre la ruta real, descubre puntos del territorio y desbloquea pequeños retos por el camino.'}</p>
    </div>

    <div className={styles.notice}>{definition.notice ?? 'El juego no sustituye la navegación ni los avisos oficiales.'}</div>
    {progressionMode === 'linear' ? <div className={styles.notice}>Esta aventura avanza por etapas. Completa los retos obligatorios anteriores para abrir los siguientes; los retos extra no bloquean el avance.</div> : null}

    {progress?.run ? <div className={styles.infoGrid}>
      <article className={styles.infoCard}><h3>Progreso</h3><strong>{progress.stats.unlocked_checkpoints}/{progress.stats.total_checkpoints}</strong><p>{percent}% de checkpoints descubiertos</p></article>
      <article className={styles.infoCard}><h3>Experiencia</h3><strong>{progress.run.score}/{progress.stats.total_points} XP</strong><p>XP conseguido en esta aventura</p></article>
      <article className={styles.infoCard}><h3>Obligatorios</h3><strong>{progress.stats.required_remaining}</strong><p>{progress.stats.required_remaining === 0 ? 'Ya puedes cerrar la aventura.' : 'Aún pendientes para completar el recorrido lúdico.'}</p></article>
    </div> : null}

    <article className={styles.infoCard} aria-labelledby="route-adventure-journal-title">
      <div className={styles.reviewMeta}>
        <strong id="route-adventure-journal-title">Cuaderno del Explorador</strong>
        <span>{effectivePercent}% descubierto</span>
      </div>
      <h3>{explorerRank(effectivePercent, completed)}</h3>
      <p>{progress?.run
        ? `Has descubierto ${effectiveUnlocked} de ${effectiveTotal} elementos de esta aventura.`
        : `Esta ruta guarda ${effectiveTotal} descubrimientos. Inicia la aventura para ir incorporándolos a tu cuaderno.`}</p>
      <div className={styles.infoGrid}>
        {collectionKinds.map((entry) => <div className={styles.infoCard} key={entry.kind}>
          <strong>{checkpointIcon(entry.kind)} {entry.unlocked}/{entry.total}</strong>
          <p>{checkpointKind(entry.kind)}</p>
        </div>)}
      </div>
      {albumCategories.length ? <><h3>Álbum de esta ruta</h3><div className={styles.infoGrid}>
        {albumCategories.map((entry) => <div className={styles.infoCard} key={entry.category}>
          <strong>{entry.unlocked}/{entry.total}</strong>
          <p>{albumCategoryLabel(entry.category)}</p>
        </div>)}
      </div></> : null}
      <div className={styles.actionRow} aria-label="Colección de esta ruta">
        {definition.checkpoints.map((checkpoint, index) => {
          const done = unlocked.has(checkpoint.id);
          const locked = isLocked(index);
          return <button
            key={checkpoint.id}
            type="button"
            className={styles.secondaryAction}
            title={done ? checkpoint.title : locked ? `Etapa ${index + 1} bloqueada por progresión` : checkpoint.title}
            onClick={() => { if (!locked) focusCheckpoint(checkpoint); }}
          >
            {done ? `✓ ${checkpoint.title}` : locked ? `🔒 Etapa ${index + 1}` : `✦ Etapa ${index + 1}`}
          </button>;
        })}
      </div>
    </article>

    {progress?.badges.length ? <div className={styles.actionRow} aria-label="Insignias conseguidas">
      {progress.badges.map((badge) => <span key={badge} className={styles.secondaryAction}>{badgeLabel(badge)}</span>)}
    </div> : null}

    {authRequired ? <div className={styles.infoCard}>
      <h3>Guarda tu aventura</h3><p>Puedes ver los retos sin cuenta, pero para iniciar el recorrido y conservar XP necesitas iniciar sesión.</p>
      <Link className={styles.primaryAction} href={`/login?next=${encodeURIComponent(`/rutas/detalle?slug=${slug}`)}`}>Iniciar sesión</Link>
    </div> : null}

    {!progress?.run && !authRequired ? <div className={styles.actionRow}>
      <button className={styles.primaryAction} type="button" disabled={busy !== null} onClick={start}>{busy === 'start' ? 'Iniciando…' : 'Iniciar Modo Aventura'}</button>
    </div> : null}

    {message ? <p role="status" aria-live="polite">{message}</p> : null}

    <div className={styles.reviewGrid}>
      {definition.checkpoints.map((checkpoint, index) => {
        const done = unlocked.has(checkpoint.id);
        const locked = isLocked(index);
        const options = Array.isArray(checkpoint.answer_options)
          ? checkpoint.answer_options.filter((option) => option && typeof option.key === 'string' && typeof option.label === 'string')
          : [];
        return <article className={styles.reviewCard} key={checkpoint.id} aria-disabled={locked}>
          <div className={styles.reviewMeta}>
            <strong>{done ? '✓ ' : locked ? '🔒 ' : ''}Etapa {index + 1} · {checkpointKind(checkpoint.kind)}</strong>
            <span>{checkpoint.is_required ? 'Obligatorio' : 'Extra'} · {checkpoint.points} XP</span>
          </div>
          <h3>{locked ? `Etapa ${index + 1} bloqueada` : checkpoint.title}</h3>
          {!locked && checkpoint.collection_category ? <p><strong>{albumCategoryLabel(checkpoint.collection_category)} · {rarityLabel(checkpoint.rarity)}</strong></p> : null}
          {locked ? <p>Completa primero las etapas obligatorias anteriores para revelar este reto.</p> : checkpoint.description ? <p>{checkpoint.description}</p> : null}
          {!locked ? <small>{km(checkpoint.distance_m) ? `${km(checkpoint.distance_m)} · ` : ''}radio de desbloqueo {checkpoint.unlock_radius_m} m</small> : null}
          {checkpoint.question && !done && !locked ? <fieldset style={{ border: 0, padding: 0, margin: '16px 0 0' }}>
            <legend><strong>{checkpoint.question}</strong></legend>
            {options.map((option) => <label key={option.key} style={{ display: 'block', marginTop: 8 }}>
              <input type="radio" name={`answer-${checkpoint.id}`} value={option.key} checked={answers[checkpoint.id] === option.key} onChange={() => setAnswers((current) => ({ ...current, [checkpoint.id]: option.key }))} />{' '}{option.label}
            </label>)}
            {checkpoint.hint ? <small style={{ display: 'block', marginTop: 8 }}>Pista: {checkpoint.hint}</small> : null}
          </fieldset> : null}
          <div className={styles.actionRow} style={{ marginTop: 12 }}>
            {!locked ? <button className={styles.secondaryAction} type="button" onClick={() => focusCheckpoint(checkpoint)}>Ver punto en mapa</button> : null}
            {active && !done && !locked ? <button className={styles.primaryAction} type="button" disabled={busy !== null} onClick={() => unlock(checkpoint)}>{busy === checkpoint.id ? 'Comprobando…' : 'Estoy aquí'}</button> : null}
            {done ? <span>Desbloqueado</span> : locked ? <span>Bloqueado</span> : null}
          </div>
        </article>;
      })}
    </div>

    {active && progress.stats.required_remaining === 0 ? <div className={styles.actionRow}>
      <button className={styles.primaryAction} type="button" disabled={busy !== null} onClick={complete}>{busy === 'complete' ? 'Cerrando aventura…' : 'Completar aventura'}</button>
    </div> : null}

    {completed ? <div className={styles.infoCard}>
      <h3>Recorrido lúdico completado</h3><p>{adventure.completion_message ?? 'Has desbloqueado todos los checkpoints necesarios de esta aventura.'}</p>
      <p><small>Esto certifica el progreso del mini‑juego, no una validación oficial de actividad deportiva ni del estado del sendero.</small></p>
    </div> : null}
  </section>;
}
