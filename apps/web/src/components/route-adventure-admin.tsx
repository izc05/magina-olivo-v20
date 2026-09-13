'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../lib/api-client';

type AdventureConfig = {
  route_id: string;
  enabled: boolean;
  title: string;
  intro: string | null;
  completion_message: string | null;
};

type RoutePoint = {
  id: string;
  name: string;
  kind: string;
  distance_m: number | string | null;
  latitude: number | string;
  longitude: number | string;
};

type AnswerOption = { key: string; label: string };

type Checkpoint = {
  id: string;
  route_point_id: string | null;
  title: string;
  description: string | null;
  kind: 'landmark' | 'trivia' | 'observation' | 'photo' | 'collection' | 'rest';
  distance_m: number | string | null;
  unlock_radius_m: number;
  points: number;
  is_required: boolean;
  question: string | null;
  answer_options: AnswerOption[];
  correct_answer_key: string | null;
  hint: string | null;
  sort_order: number;
  active: boolean;
  latitude: number | string;
  longitude: number | string;
};

type AdventureAdminPayload = {
  adventure: AdventureConfig;
  checkpoints: Checkpoint[];
  route_points: RoutePoint[];
  metrics: { active_runs: number; completed_runs: number; abandoned_runs: number; total_runs: number; average_score: number | string };
};

type CheckpointForm = {
  id: string | null;
  route_point_id: string;
  title: string;
  description: string;
  kind: Checkpoint['kind'];
  latitude: string;
  longitude: string;
  distance_m: string;
  unlock_radius_m: string;
  points: string;
  is_required: boolean;
  question: string;
  options: string;
  correct_answer_key: string;
  hint: string;
  sort_order: string;
  active: boolean;
};

const emptyCheckpoint: CheckpointForm = {
  id: null,
  route_point_id: '',
  title: '',
  description: '',
  kind: 'landmark',
  latitude: '',
  longitude: '',
  distance_m: '',
  unlock_radius_m: '60',
  points: '100',
  is_required: true,
  question: '',
  options: '',
  correct_answer_key: '',
  hint: '',
  sort_order: '0',
  active: true,
};

function parseOptions(raw: string): AnswerOption[] {
  return raw.split('\n').map((line) => line.trim()).filter(Boolean).map((line, index) => {
    const separator = line.indexOf('|');
    if (separator === -1) return { key: String.fromCharCode(97 + index), label: line };
    return { key: line.slice(0, separator).trim(), label: line.slice(separator + 1).trim() };
  }).filter((option) => option.key && option.label);
}

function optionsText(options: AnswerOption[]) {
  return options.map((option) => `${option.key}|${option.label}`).join('\n');
}

function kindLabel(kind: Checkpoint['kind']) {
  return ({ landmark: 'Lugar', trivia: 'Pregunta', observation: 'Observación', photo: 'Foto', collection: 'Coleccionable', rest: 'Descanso' })[kind];
}

export function RouteAdventureAdmin({ routeId, editable, busy }: { routeId: string; editable: boolean; busy: boolean }) {
  const [data, setData] = useState<AdventureAdminPayload | null>(null);
  const [config, setConfig] = useState({ enabled: false, title: 'Modo Aventura', intro: '', completion_message: '' });
  const [checkpoint, setCheckpoint] = useState<CheckpointForm>(emptyCheckpoint);
  const [localBusy, setLocalBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    const response = await apiFetch<AdventureAdminPayload>(`/api/v1/admin/routes/${routeId}/adventure`);
    setData(response);
    setConfig({
      enabled: Boolean(response.adventure.enabled),
      title: response.adventure.title || 'Modo Aventura',
      intro: response.adventure.intro ?? '',
      completion_message: response.adventure.completion_message ?? '',
    });
  }, [routeId]);

  useEffect(() => {
    setCheckpoint(emptyCheckpoint);
    void load().catch(() => setError('No se ha podido cargar el editor del Modo Aventura.'));
  }, [load]);

  const disabled = !editable || busy || localBusy;
  const ordered = useMemo(() => [...(data?.checkpoints ?? [])].sort((a, b) => a.sort_order - b.sort_order), [data]);

  async function execute(task: () => Promise<void>, success: string) {
    setLocalBusy(true); setError(null); setMessage(null);
    try { await task(); await load(); setMessage(success); }
    catch (cause) { console.error(cause); setError('No se ha podido guardar el Modo Aventura. Revisa coordenadas, opciones y respuesta correcta.'); }
    finally { setLocalBusy(false); }
  }

  async function saveConfig() {
    if (!config.title.trim()) { setError('El Modo Aventura necesita un título.'); return; }
    await execute(() => apiFetch(`/api/v1/admin/routes/${routeId}/adventure`, {
      method: 'PUT', body: JSON.stringify({
        enabled: config.enabled,
        title: config.title.trim(),
        intro: config.intro.trim() || null,
        completion_message: config.completion_message.trim() || null,
      }),
    }), config.enabled ? 'Modo Aventura guardado y activado.' : 'Configuración guardada; la aventura permanece oculta al público.');
  }

  function useRoutePoint(pointId: string) {
    const point = data?.route_points.find((item) => item.id === pointId);
    if (!point) { setCheckpoint((current) => ({ ...current, route_point_id: pointId })); return; }
    setCheckpoint((current) => ({
      ...current,
      route_point_id: point.id,
      title: current.title || point.name,
      latitude: String(point.latitude),
      longitude: String(point.longitude),
      distance_m: point.distance_m == null ? current.distance_m : String(point.distance_m),
    }));
  }

  function editCheckpoint(item: Checkpoint) {
    setCheckpoint({
      id: item.id,
      route_point_id: item.route_point_id ?? '',
      title: item.title,
      description: item.description ?? '',
      kind: item.kind,
      latitude: String(item.latitude),
      longitude: String(item.longitude),
      distance_m: item.distance_m == null ? '' : String(item.distance_m),
      unlock_radius_m: String(item.unlock_radius_m),
      points: String(item.points),
      is_required: item.is_required,
      question: item.question ?? '',
      options: optionsText(Array.isArray(item.answer_options) ? item.answer_options : []),
      correct_answer_key: item.correct_answer_key ?? '',
      hint: item.hint ?? '',
      sort_order: String(item.sort_order),
      active: item.active,
    });
  }

  async function saveCheckpoint() {
    const latitude = Number(checkpoint.latitude.replace(',', '.'));
    const longitude = Number(checkpoint.longitude.replace(',', '.'));
    const radius = Number(checkpoint.unlock_radius_m);
    const points = Number(checkpoint.points);
    const sortOrder = Number(checkpoint.sort_order);
    if (!checkpoint.title.trim() || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      setError('Checkpoint: indica título, latitud y longitud válidas.'); return;
    }
    const options = parseOptions(checkpoint.options);
    if (checkpoint.question.trim() && (options.length < 2 || !checkpoint.correct_answer_key.trim())) {
      setError('Una pregunta necesita al menos dos opciones y la clave de la respuesta correcta.'); return;
    }
    const payload = {
      route_point_id: checkpoint.route_point_id || null,
      title: checkpoint.title.trim(),
      description: checkpoint.description.trim() || null,
      kind: checkpoint.kind,
      latitude,
      longitude,
      distance_m: checkpoint.distance_m ? Number(checkpoint.distance_m) : null,
      unlock_radius_m: Number.isFinite(radius) ? radius : 60,
      points: Number.isFinite(points) ? points : 100,
      is_required: checkpoint.is_required,
      question: checkpoint.question.trim() || null,
      answer_options: checkpoint.question.trim() ? options : [],
      correct_answer_key: checkpoint.question.trim() ? checkpoint.correct_answer_key.trim() : null,
      hint: checkpoint.hint.trim() || null,
      sort_order: Number.isFinite(sortOrder) ? sortOrder : 0,
      active: checkpoint.active,
    };
    await execute(async () => {
      const path = checkpoint.id
        ? `/api/v1/admin/routes/${routeId}/adventure/checkpoints/${checkpoint.id}`
        : `/api/v1/admin/routes/${routeId}/adventure/checkpoints`;
      await apiFetch(path, { method: checkpoint.id ? 'PATCH' : 'POST', body: JSON.stringify(payload) });
      setCheckpoint(emptyCheckpoint);
    }, checkpoint.id ? 'Checkpoint actualizado.' : 'Checkpoint añadido a la aventura.');
  }

  async function deleteCheckpoint(id: string) {
    await execute(async () => {
      await apiFetch(`/api/v1/admin/routes/${routeId}/adventure/checkpoints/${id}`, { method: 'DELETE' });
      if (checkpoint.id === id) setCheckpoint(emptyCheckpoint);
    }, 'Checkpoint eliminado.');
  }

  return <div className="routes-admin-card">
    <div className="routes-admin-card-title">
      <div><span>Experiencia lúdica</span><h2>Modo Aventura</h2></div>
      <strong>{config.enabled ? 'Activo' : 'Oculto'} · {data?.checkpoints.length ?? 0} etapas</strong>
    </div>
    {message ? <div className="routes-admin-notice ok">{message}</div> : null}
    {error ? <div className="routes-admin-notice error">{error}</div> : null}

    <div className="routes-admin-form-grid">
      <label className="routes-admin-check"><input type="checkbox" checked={config.enabled} onChange={(event) => setConfig((current) => ({ ...current, enabled: event.target.checked }))} /> Activar en la ficha pública</label>
      <label>Título<input value={config.title} onChange={(event) => setConfig((current) => ({ ...current, title: event.target.value }))} /></label>
      <label className="wide">Introducción<textarea value={config.intro} onChange={(event) => setConfig((current) => ({ ...current, intro: event.target.value }))} placeholder="La misión o historia que presenta el recorrido…" /></label>
      <label className="wide">Mensaje final<textarea value={config.completion_message} onChange={(event) => setConfig((current) => ({ ...current, completion_message: event.target.value }))} placeholder="Texto al completar los checkpoints obligatorios…" /></label>
    </div>
    <div className="routes-admin-actions"><button disabled={disabled} onClick={() => void saveConfig()}>Guardar aventura</button></div>

    <div className="routes-admin-metrics">
      <article><span>Partidas</span><strong>{data?.metrics.total_runs ?? 0}</strong></article>
      <article><span>Activas</span><strong>{data?.metrics.active_runs ?? 0}</strong></article>
      <article><span>Completadas</span><strong>{data?.metrics.completed_runs ?? 0}</strong></article>
      <article><span>Puntuación media</span><strong>{data?.metrics.average_score ?? 0}</strong></article>
    </div>

    <div className="routes-content-columns">
      <section>
        <h3>{checkpoint.id ? 'Editar etapa' : 'Nueva etapa'}</h3>
        <div className="routes-mini-form">
          <select value={checkpoint.route_point_id} onChange={(event) => useRoutePoint(event.target.value)}>
            <option value="">Punto libre por coordenadas</option>
            {data?.route_points.map((point) => <option value={point.id} key={point.id}>{point.name} · {point.kind}</option>)}
          </select>
          <input placeholder="Título" value={checkpoint.title} onChange={(event) => setCheckpoint((current) => ({ ...current, title: event.target.value }))} />
          <select value={checkpoint.kind} onChange={(event) => setCheckpoint((current) => ({ ...current, kind: event.target.value as Checkpoint['kind'] }))}>
            <option value="landmark">Lugar / patrimonio</option><option value="trivia">Pregunta</option><option value="observation">Observación</option><option value="photo">Reto fotográfico</option><option value="collection">Coleccionable</option><option value="rest">Descanso narrativo</option>
          </select>
          <input placeholder="Latitud" value={checkpoint.latitude} onChange={(event) => setCheckpoint((current) => ({ ...current, latitude: event.target.value }))} />
          <input placeholder="Longitud" value={checkpoint.longitude} onChange={(event) => setCheckpoint((current) => ({ ...current, longitude: event.target.value }))} />
          <input placeholder="Distancia sobre ruta (m)" value={checkpoint.distance_m} onChange={(event) => setCheckpoint((current) => ({ ...current, distance_m: event.target.value }))} />
          <input type="number" min="10" max="500" placeholder="Radio GPS m" value={checkpoint.unlock_radius_m} onChange={(event) => setCheckpoint((current) => ({ ...current, unlock_radius_m: event.target.value }))} />
          <input type="number" min="0" max="10000" placeholder="Puntos" value={checkpoint.points} onChange={(event) => setCheckpoint((current) => ({ ...current, points: event.target.value }))} />
          <input type="number" placeholder="Orden" value={checkpoint.sort_order} onChange={(event) => setCheckpoint((current) => ({ ...current, sort_order: event.target.value }))} />
          <textarea placeholder="Descripción / historia del punto" value={checkpoint.description} onChange={(event) => setCheckpoint((current) => ({ ...current, description: event.target.value }))} />
          <textarea placeholder="Pregunta (opcional)" value={checkpoint.question} onChange={(event) => setCheckpoint((current) => ({ ...current, question: event.target.value }))} />
          {checkpoint.question ? <><textarea placeholder={'Opciones, una por línea. Ejemplo:\na|Castillo\nb|Molino\nc|Fuente'} value={checkpoint.options} onChange={(event) => setCheckpoint((current) => ({ ...current, options: event.target.value }))} /><input placeholder="Clave correcta, por ejemplo a" value={checkpoint.correct_answer_key} onChange={(event) => setCheckpoint((current) => ({ ...current, correct_answer_key: event.target.value }))} /><input placeholder="Pista opcional" value={checkpoint.hint} onChange={(event) => setCheckpoint((current) => ({ ...current, hint: event.target.value }))} /></> : null}
          <label className="routes-admin-check"><input type="checkbox" checked={checkpoint.is_required} onChange={(event) => setCheckpoint((current) => ({ ...current, is_required: event.target.checked }))} /> Obligatorio para terminar</label>
          <label className="routes-admin-check"><input type="checkbox" checked={checkpoint.active} onChange={(event) => setCheckpoint((current) => ({ ...current, active: event.target.checked }))} /> Visible/activo</label>
          <button disabled={disabled} onClick={() => void saveCheckpoint()}>{checkpoint.id ? 'Actualizar etapa' : 'Añadir etapa'}</button>
          {checkpoint.id ? <button className="secondary" disabled={disabled} onClick={() => setCheckpoint(emptyCheckpoint)}>Cancelar edición</button> : null}
        </div>
      </section>

      <section>
        <h3>Recorrido jugable</h3>
        <div className="routes-admin-tracks">
          {ordered.map((item, index) => <article key={item.id}>
            <div><strong>{index + 1}. {item.title}</strong><span>{kindLabel(item.kind)} · {item.points} pt · radio {item.unlock_radius_m} m · {item.is_required ? 'obligatorio' : 'extra'}{item.active ? '' : ' · oculto'}</span></div>
            <div><button disabled={disabled} onClick={() => editCheckpoint(item)}>Editar</button><button className="danger" disabled={disabled} onClick={() => void deleteCheckpoint(item.id)}>Eliminar</button></div>
          </article>)}
          {!ordered.length ? <p>Aún no hay etapas. Puedes reutilizar un POI existente o introducir coordenadas reales.</p> : null}
        </div>
      </section>
    </div>

    <p className="routes-admin-help">La aventura solo añade una capa lúdica. El track validado, la seguridad, las restricciones y los avisos oficiales siguen siendo independientes y prioritarios.</p>
  </div>;
}
