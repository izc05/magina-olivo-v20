'use client';

import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../lib/api-client';

type Point = { id: string; name: string; kind: string; latitude: number; longitude: number; distance_m: number | null };
type Media = { id: string; kind: string; origin: string; url: string; caption: string | null; ai_disclosure: string | null };
type Source = { id: string; source_name: string; source_url: string; source_kind: string };
type RouteContent = { points: Point[]; media: Media[]; sources: Source[] };

export function RouteContentAdmin({ routeId, editable, busy }: { routeId: string; editable: boolean; busy: boolean }) {
  const [content, setContent] = useState<RouteContent>({ points: [], media: [], sources: [] });
  const [localBusy, setLocalBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [point, setPoint] = useState({ name: '', kind: 'viewpoint', latitude: '', longitude: '', distance_m: '' });
  const [source, setSource] = useState({ source_name: '', source_url: '', source_kind: 'reference' });
  const [media, setMedia] = useState({ kind: 'photo', origin: 'real', url: '', caption: '', ai_disclosure: '' });

  const load = useCallback(async () => {
    const response = await apiFetch<RouteContent>(`/api/v1/admin/routes/${routeId}/content`);
    setContent(response);
  }, [routeId]);

  useEffect(() => { void load().catch(() => setError('No se ha podido cargar POI, fuentes y multimedia.')); }, [load]);

  async function execute(task: () => Promise<void>) {
    setLocalBusy(true); setError(null);
    try { await task(); await load(); }
    catch (cause) { console.error(cause); setError('No se ha podido guardar este contenido.'); }
    finally { setLocalBusy(false); }
  }

  const disabled = !editable || busy || localBusy;

  async function addPoint() {
    const latitude = Number(point.latitude.replace(',', '.'));
    const longitude = Number(point.longitude.replace(',', '.'));
    if (!point.name.trim() || !Number.isFinite(latitude) || !Number.isFinite(longitude)) { setError('POI: indica nombre, latitud y longitud válidas.'); return; }
    await execute(async () => {
      await apiFetch(`/api/v1/admin/routes/${routeId}/points`, { method: 'POST', body: JSON.stringify({
        name: point.name.trim(), kind: point.kind, latitude, longitude,
        distance_m: point.distance_m ? Number(point.distance_m) : null,
      }) });
      setPoint({ name: '', kind: 'viewpoint', latitude: '', longitude: '', distance_m: '' });
    });
  }

  async function addSource() {
    if (!source.source_name.trim() || !source.source_url.trim()) { setError('Fuente: indica nombre y URL.'); return; }
    await execute(async () => {
      await apiFetch(`/api/v1/admin/routes/${routeId}/sources`, { method: 'POST', body: JSON.stringify({
        source_name: source.source_name.trim(), source_url: source.source_url.trim(), source_kind: source.source_kind,
      }) });
      setSource({ source_name: '', source_url: '', source_kind: 'reference' });
    });
  }

  async function addMedia() {
    if (!media.url.trim()) { setError('Multimedia: indica una URL o ruta interna.'); return; }
    const ai = media.origin === 'ai_generated';
    if (ai && !media.ai_disclosure.trim()) { setError('El contenido generado por IA necesita una declaración visible.'); return; }
    await execute(async () => {
      await apiFetch(`/api/v1/admin/routes/${routeId}/media`, { method: 'POST', body: JSON.stringify({
        kind: media.kind, origin: media.origin, url: media.url.trim(), caption: media.caption.trim() || null,
        ai_disclosure: ai ? media.ai_disclosure.trim() : null,
      }) });
      setMedia({ kind: 'photo', origin: 'real', url: '', caption: '', ai_disclosure: '' });
    });
  }

  return <div className="routes-admin-card">
    <div className="routes-admin-card-title"><div><span>Contenido de ruta</span><h2>POI, fuentes y multimedia</h2></div><strong>{content.points.length + content.sources.length + content.media.length} elementos</strong></div>
    {error ? <div className="routes-admin-notice error">{error}</div> : null}

    <div className="routes-content-columns">
      <section>
        <h3>Puntos de interés</h3>
        <div className="routes-mini-form"><input placeholder="Nombre" value={point.name} onChange={(e) => setPoint((v) => ({ ...v, name: e.target.value }))}/><select value={point.kind} onChange={(e) => setPoint((v) => ({ ...v, kind: e.target.value }))}><option value="viewpoint">Mirador</option><option value="water">Agua</option><option value="parking">Aparcamiento</option><option value="recreation_area">Área recreativa</option><option value="heritage">Patrimonio</option><option value="cave">Cueva</option><option value="bridge">Puente</option><option value="rest">Descanso</option><option value="photo_spot">Foto</option><option value="warning">Aviso</option><option value="start">Inicio</option><option value="finish">Fin</option><option value="other">Otro</option></select><input placeholder="Latitud" value={point.latitude} onChange={(e) => setPoint((v) => ({ ...v, latitude: e.target.value }))}/><input placeholder="Longitud" value={point.longitude} onChange={(e) => setPoint((v) => ({ ...v, longitude: e.target.value }))}/><input placeholder="Distancia m (opcional)" value={point.distance_m} onChange={(e) => setPoint((v) => ({ ...v, distance_m: e.target.value }))}/><button disabled={disabled} onClick={() => void addPoint()}>Añadir POI</button></div>
        <div className="routes-admin-tracks">{content.points.map((item) => <article key={item.id}><div><strong>{item.name}</strong><span>{item.kind} · {item.latitude}, {item.longitude}</span></div><button className="danger" disabled={disabled} onClick={() => void execute(() => apiFetch(`/api/v1/admin/routes/${routeId}/points/${item.id}`, { method:'DELETE' }))}>Eliminar</button></article>)}</div>
      </section>

      <section>
        <h3>Fuentes</h3>
        <div className="routes-mini-form"><input placeholder="Nombre de la fuente" value={source.source_name} onChange={(e) => setSource((v) => ({ ...v, source_name: e.target.value }))}/><input placeholder="https://…" value={source.source_url} onChange={(e) => setSource((v) => ({ ...v, source_url: e.target.value }))}/><select value={source.source_kind} onChange={(e) => setSource((v) => ({ ...v, source_kind: e.target.value }))}><option value="official">Oficial</option><option value="reference">Referencia</option><option value="track">Track</option><option value="media">Multimedia</option><option value="editorial">Editorial</option></select><button disabled={disabled} onClick={() => void addSource()}>Añadir fuente</button></div>
        <div className="routes-admin-tracks">{content.sources.map((item) => <article key={item.id}><div><strong>{item.source_name}</strong><span>{item.source_kind}</span></div><button className="danger" disabled={disabled} onClick={() => void execute(() => apiFetch(`/api/v1/admin/routes/${routeId}/sources/${item.id}`, { method:'DELETE' }))}>Eliminar</button></article>)}</div>
      </section>

      <section>
        <h3>Multimedia</h3>
        <div className="routes-mini-form"><select value={media.kind} onChange={(e) => setMedia((v) => ({ ...v, kind: e.target.value }))}><option value="photo">Foto</option><option value="hero_image">Hero</option><option value="thumbnail">Miniatura</option><option value="real_video">Vídeo real</option><option value="drone_video">Vídeo dron</option><option value="ai_image">Imagen IA</option><option value="ai_video">Vídeo IA</option><option value="map_animation">Animación mapa</option><option value="elevation_animation">Animación desnivel</option></select><select value={media.origin} onChange={(e) => { const origin=e.target.value; setMedia((v) => ({ ...v, origin, kind: origin === 'ai_generated' && !v.kind.startsWith('ai_') ? 'ai_image' : origin !== 'ai_generated' && v.kind.startsWith('ai_') ? 'photo' : v.kind })); }}><option value="real">Real</option><option value="official">Oficial</option><option value="licensed">Licenciada</option><option value="ai_generated">Generada por IA</option></select><input placeholder="URL o /ruta-interna" value={media.url} onChange={(e) => setMedia((v) => ({ ...v, url: e.target.value }))}/><input placeholder="Pie / descripción" value={media.caption} onChange={(e) => setMedia((v) => ({ ...v, caption: e.target.value }))}/>{media.origin === 'ai_generated' ? <input placeholder="Declaración IA obligatoria" value={media.ai_disclosure} onChange={(e) => setMedia((v) => ({ ...v, ai_disclosure: e.target.value }))}/> : null}<button disabled={disabled} onClick={() => void addMedia()}>Añadir multimedia</button></div>
        <div className="routes-admin-tracks">{content.media.map((item) => <article key={item.id}><div><strong>{item.kind}</strong><span>{item.origin} · {item.caption ?? item.url}</span></div><button className="danger" disabled={disabled} onClick={() => void execute(() => apiFetch(`/api/v1/admin/routes/${routeId}/media/${item.id}`, { method:'DELETE' }))}>Eliminar</button></article>)}</div>
      </section>
    </div>
  </div>;
}
