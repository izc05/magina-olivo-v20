'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiFetch, ApiRequestError } from '../lib/api-client';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import { RouteContentAdmin } from './route-content-admin';

type AdminRoute = {
  id: string;
  slug: string;
  name: string;
  route_type: 'hiking' | 'mtb' | 'cycling' | 'trail' | 'family' | 'mixed';
  difficulty: 'easy' | 'moderate' | 'hard' | 'very_hard' | null;
  distance_m: number | null;
  duration_minutes: number | null;
  elevation_gain_m: number | null;
  elevation_loss_m: number | null;
  status: 'draft' | 'review' | 'published' | 'archived';
  validation_status: 'unverified' | 'editorial' | 'official';
  track_status: 'missing' | 'uploaded' | 'validated' | 'rejected';
  circular: boolean;
  family_friendly: boolean;
  short_description: string | null;
  safety_notes: string | null;
};

type RouteTrack = {
  id: string;
  version: number;
  validation_status: 'uploaded' | 'validated' | 'rejected';
  distance_m: number | null;
  source_name: string | null;
  source_url: string | null;
  created_at: string;
};

type RouteDetail = { route: AdminRoute; tracks: RouteTrack[] };

type Session = {
  user: { primary_email: string | null };
  platform_access: { role: 'super_admin' | 'admin' | 'editor' | 'support' };
};

const emptyForm = {
  name: '',
  slug: '',
  route_type: 'hiking' as AdminRoute['route_type'],
  difficulty: '' as '' | NonNullable<AdminRoute['difficulty']>,
  duration_minutes: '',
  short_description: '',
  safety_notes: '',
  circular: false,
  family_friendly: false,
};

function slugify(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function km(meters: number | null) {
  return meters === null ? '—' : `${(meters / 1000).toFixed(1)} km`;
}

export function RoutesAdminConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<Session | null>(null);
  const [routes, setRoutes] = useState<AdminRoute[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<RouteDetail | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [denied, setDenied] = useState(false);

  const editable = session ? session.platform_access.role !== 'support' : false;

  const load = useCallback(async () => {
    const [nextSession, response] = await Promise.all([
      apiFetch<Session>('/api/v1/admin/session'),
      apiFetch<{ routes: AdminRoute[] }>('/api/v1/admin/routes'),
    ]);
    setSession(nextSession);
    setRoutes(response.routes);
  }, []);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    setDenied(false);
    void load().catch((caught: unknown) => {
      if (caught instanceof ApiRequestError && caught.status === 403) setDenied(true);
      else setError('No se ha podido cargar el módulo de rutas.');
    });
  }, [auth.status, load]);

  const filteredRoutes = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('es');
    if (!normalized) return routes;
    return routes.filter((route) => route.name.toLocaleLowerCase('es').includes(normalized)
      || route.slug.includes(normalized)
      || route.route_type.includes(normalized));
  }, [query, routes]);

  async function selectRoute(route: AdminRoute) {
    setSelectedId(route.id);
    setForm({
      name: route.name,
      slug: route.slug,
      route_type: route.route_type,
      difficulty: route.difficulty ?? '',
      duration_minutes: route.duration_minutes === null ? '' : String(route.duration_minutes),
      short_description: route.short_description ?? '',
      safety_notes: route.safety_notes ?? '',
      circular: route.circular,
      family_friendly: route.family_friendly,
    });
    setError(null);
    try {
      const response = await apiFetch<RouteDetail>(`/api/v1/admin/routes/${route.id}`);
      setDetail(response);
    } catch {
      setError('No se ha podido cargar el detalle de la ruta.');
    }
  }

  function startNew() {
    setSelectedId(null);
    setDetail(null);
    setForm(emptyForm);
    setError(null);
    setMessage(null);
  }

  async function run(task: () => Promise<void>, success: string) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await task();
      setMessage(success);
      await load();
    } catch (caught) {
      console.error(caught);
      if (caught instanceof ApiRequestError && caught.status === 409) {
        setError('No se puede publicar: primero debe existir un track GPX validado.');
      } else {
        setError('No se ha podido completar la operación. Revisa los datos.');
      }
    } finally {
      setBusy(false);
    }
  }

  async function saveRoute() {
    if (!editable || !form.name.trim()) return;
    const payload = {
      name: form.name.trim(),
      slug: slugify(form.slug || form.name),
      route_type: form.route_type,
      difficulty: form.difficulty || null,
      duration_minutes: form.duration_minutes ? Number(form.duration_minutes) : null,
      short_description: form.short_description.trim() || null,
      safety_notes: form.safety_notes.trim() || null,
      circular: form.circular,
      family_friendly: form.family_friendly,
    };

    await run(async () => {
      if (selectedId) {
        const response = await apiFetch<{ route: AdminRoute }>(`/api/v1/admin/routes/${selectedId}`, {
          method: 'PATCH', body: JSON.stringify(payload),
        });
        await selectRoute(response.route);
      } else {
        const response = await apiFetch<{ route: AdminRoute }>('/api/v1/admin/routes', {
          method: 'POST', body: JSON.stringify(payload),
        });
        await selectRoute(response.route);
      }
    }, selectedId ? 'Ruta actualizada.' : 'Ruta creada. Ahora puedes cargar su GPX.');
  }

  async function uploadGpx(file: File) {
    if (!selectedId || !editable) return;
    const gpx = await file.text();
    await run(async () => {
      await apiFetch(`/api/v1/admin/routes/${selectedId}/gpx`, {
        method: 'POST',
        body: JSON.stringify({ gpx, source_name: file.name }),
      });
      setDetail(await apiFetch<RouteDetail>(`/api/v1/admin/routes/${selectedId}`));
    }, 'GPX procesado. Revisa sus métricas y valida el track antes de publicar.');
  }

  async function validateTrack(trackId: string, validation_status: 'validated' | 'rejected') {
    if (!selectedId || !editable) return;
    await run(async () => {
      await apiFetch(`/api/v1/admin/routes/${selectedId}/track-validation`, {
        method: 'POST', body: JSON.stringify({ track_id: trackId, validation_status }),
      });
      setDetail(await apiFetch<RouteDetail>(`/api/v1/admin/routes/${selectedId}`));
    }, validation_status === 'validated' ? 'Track validado.' : 'Track rechazado.');
  }

  async function changeStatus(status: AdminRoute['status']) {
    if (!selectedId || !editable) return;
    await run(async () => {
      const response = await apiFetch<{ route: AdminRoute }>(`/api/v1/admin/routes/${selectedId}`, {
        method: 'PATCH', body: JSON.stringify({ status }),
      });
      await selectRoute(response.route);
    }, status === 'published' ? 'Ruta publicada.' : 'Estado de la ruta actualizado.');
  }

  if (auth.status === 'loading') return <main className="routes-admin-login"><strong>Comprobando acceso…</strong></main>;
  if (auth.status === 'anonymous') return <main className="routes-admin-login"><div><h1>Administración de rutas</h1><p>Inicia sesión con una cuenta autorizada.</p><GoogleSignInButton /></div></main>;
  if (denied) return <main className="routes-admin-login"><div><h1>Acceso restringido</h1><p>Esta cuenta no tiene permisos de plataforma.</p></div></main>;

  return <main className="routes-admin-shell">
    <header className="routes-admin-header">
      <div><a href="/admin">← Centro de control</a><span>Rutas · datos reales y trazabilidad</span><h1>Gestión de rutas</h1><p>{session?.user.primary_email ?? ''}</p></div>
      <button disabled={!editable || busy} onClick={startNew}>Nueva ruta</button>
    </header>

    {message ? <div className="routes-admin-notice ok">{message}</div> : null}
    {error ? <div className="routes-admin-notice error">{error}</div> : null}

    <section className="routes-admin-layout">
      <aside className="routes-admin-list">
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar ruta…" />
        <div className="routes-admin-count">{filteredRoutes.length} rutas</div>
        {filteredRoutes.map((route) => <button key={route.id} className={selectedId === route.id ? 'active' : ''} onClick={() => void selectRoute(route)}>
          <strong>{route.name}</strong>
          <span>{km(route.distance_m)} · {route.track_status} · {route.status}</span>
        </button>)}
      </aside>

      <section className="routes-admin-editor">
        <div className="routes-admin-card">
          <div className="routes-admin-card-title"><div><span>Ficha editorial</span><h2>{selectedId ? 'Editar ruta' : 'Crear ruta'}</h2></div>{selectedId ? <code>{selectedId}</code> : null}</div>
          <div className="routes-admin-form-grid">
            <label>Nombre<input value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value, slug: current.slug || slugify(event.target.value) }))} /></label>
            <label>Slug<input value={form.slug} onChange={(event) => setForm((current) => ({ ...current, slug: slugify(event.target.value) }))} /></label>
            <label>Tipo<select value={form.route_type} onChange={(event) => setForm((current) => ({ ...current, route_type: event.target.value as AdminRoute['route_type'] }))}><option value="hiking">Senderismo</option><option value="mtb">MTB</option><option value="cycling">Ciclismo</option><option value="trail">Trail</option><option value="family">Familiar</option><option value="mixed">Mixta</option></select></label>
            <label>Dificultad<select value={form.difficulty} onChange={(event) => setForm((current) => ({ ...current, difficulty: event.target.value as typeof current.difficulty }))}><option value="">Sin clasificar</option><option value="easy">Fácil</option><option value="moderate">Moderada</option><option value="hard">Difícil</option><option value="very_hard">Muy difícil</option></select></label>
            <label>Duración estimada (min)<input type="number" min="0" value={form.duration_minutes} onChange={(event) => setForm((current) => ({ ...current, duration_minutes: event.target.value }))} /></label>
            <label className="routes-admin-check"><input type="checkbox" checked={form.circular} onChange={(event) => setForm((current) => ({ ...current, circular: event.target.checked }))} /> Circular</label>
            <label className="routes-admin-check"><input type="checkbox" checked={form.family_friendly} onChange={(event) => setForm((current) => ({ ...current, family_friendly: event.target.checked }))} /> Apta para familias</label>
            <label className="wide">Resumen<textarea value={form.short_description} onChange={(event) => setForm((current) => ({ ...current, short_description: event.target.value }))} /></label>
            <label className="wide">Seguridad<textarea value={form.safety_notes} onChange={(event) => setForm((current) => ({ ...current, safety_notes: event.target.value }))} /></label>
          </div>
          <div className="routes-admin-actions"><button disabled={!editable || busy || !form.name.trim()} onClick={() => void saveRoute()}>Guardar ficha</button>{selectedId ? <><button className="secondary" disabled={!editable || busy} onClick={() => void changeStatus('review')}>Pasar a revisión</button><button disabled={!editable || busy || detail?.route.track_status !== 'validated'} onClick={() => void changeStatus('published')}>Publicar</button></> : null}</div>
        </div>

        {selectedId ? <div className="routes-admin-card">
          <div className="routes-admin-card-title"><div><span>Track técnico</span><h2>GPX y validación</h2></div><strong>{detail?.route.track_status ?? 'cargando'}</strong></div>
          <div className="routes-admin-metrics"><article><span>Distancia</span><strong>{km(detail?.route.distance_m ?? null)}</strong></article><article><span>Desnivel +</span><strong>{detail?.route.elevation_gain_m ?? '—'} m</strong></article><article><span>Desnivel −</span><strong>{detail?.route.elevation_loss_m ?? '—'} m</strong></article></div>
          <label className="routes-admin-upload">Importar GPX real<input type="file" accept=".gpx,application/gpx+xml,application/xml,text/xml" disabled={!editable || busy} onChange={(event) => { const file = event.target.files?.[0]; if (file) void uploadGpx(file); event.currentTarget.value = ''; }} /></label>
          <div className="routes-admin-tracks">{detail?.tracks.map((track) => <article key={track.id}><div><strong>Versión {track.version}</strong><span>{track.source_name ?? 'GPX'} · {km(track.distance_m)} · {track.validation_status}</span></div><div><button disabled={!editable || busy || track.validation_status === 'validated'} onClick={() => void validateTrack(track.id, 'validated')}>Validar</button><button className="danger" disabled={!editable || busy || track.validation_status === 'rejected'} onClick={() => void validateTrack(track.id, 'rejected')}>Rechazar</button></div></article>) ?? null}</div>
          <p className="routes-admin-help">Publicar solo se habilita cuando existe un track validado. Invalidarlo devuelve automáticamente la ruta a revisión.</p>
        </div> : null}

        {selectedId ? <RouteContentAdmin routeId={selectedId} editable={editable} busy={busy} /> : null}
      </section>
    </section>
  </main>;
}
