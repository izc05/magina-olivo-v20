'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { apiFetch, ApiRequestError } from '../lib/api-client';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';

type AdminRoute = {
  id: string;
  slug: string;
  name: string;
  status: 'draft' | 'review' | 'published' | 'archived';
  track_status: 'missing' | 'uploaded' | 'validated' | 'rejected';
  distance_m: number | null;
};

type Session = {
  user: { primary_email: string | null };
  platform_access: { role: 'super_admin' | 'admin' | 'editor' | 'support' };
};

type ImportResult = {
  route: { id: string; slug: string; name: string };
  track: { id: string; version: number };
  validation_status: 'uploaded';
  requires_editor_validation: true;
  route_moved_to_review: boolean;
  comparison: {
    previous_distance_m: number | null;
    imported_distance_m: number;
    distance_delta_m: number | null;
    distance_delta_percent: number | null;
  };
  metrics: {
    point_count: number;
    distance_m: number;
    elevation_gain_m: number | null;
    elevation_loss_m: number | null;
    min_altitude_m: number | null;
    max_altitude_m: number | null;
    bbox: [number, number, number, number];
  };
  notice: string;
};

function km(value: number | null) {
  return value == null ? '—' : `${(value / 1000).toFixed(2)} km`;
}

function errorMessage(error: unknown) {
  if (error instanceof ApiRequestError && error.payload && typeof error.payload === 'object') {
    const code = String((error.payload as Record<string, unknown>).error ?? '');
    const labels: Record<string, string> = {
      kml_empty: 'El fichero KML está vacío.',
      kml_too_large: 'El KML supera el límite admitido.',
      kml_root_missing: 'El fichero no contiene una raíz KML válida.',
      kml_linestring_missing: 'No se ha encontrado un LineString en el KML.',
      kml_multiple_linestrings_unsupported: 'El KML contiene varios LineString. Revísalo o conviértelo a un único trazado antes de importarlo.',
      kml_coordinates_missing: 'El KML no contiene coordenadas de trazado.',
      kml_track_too_short: 'El trazado contiene menos de dos puntos.',
      kml_invalid_coordinate: 'El KML contiene una coordenada no válida.',
      kml_invalid_latitude: 'El KML contiene una latitud fuera de rango.',
      kml_invalid_longitude: 'El KML contiene una longitud fuera de rango.',
      kml_invalid_altitude: 'El KML contiene una altitud no válida.',
      route_not_found: 'La ruta seleccionada ya no existe.',
    };
    if (labels[code]) return labels[code];
  }
  return 'No se ha podido importar el KML. Revisa el fichero, la fuente y vuelve a intentarlo.';
}

export function RouteKmlImporter() {
  const auth = useAuth();
  const [session, setSession] = useState<Session | null>(null);
  const [routes, setRoutes] = useState<AdminRoute[]>([]);
  const [routeId, setRouteId] = useState('');
  const [sourceName, setSourceName] = useState('Junta de Andalucía · Ventana del Visitante');
  const [sourceUrl, setSourceUrl] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [denied, setDenied] = useState(false);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    let cancelled = false;
    Promise.all([
      apiFetch<Session>('/api/v1/admin/session'),
      apiFetch<{ routes: AdminRoute[] }>('/api/v1/admin/routes'),
    ]).then(([nextSession, response]) => {
      if (cancelled) return;
      setSession(nextSession);
      setRoutes(response.routes);
      setDenied(false);
    }).catch((caught) => {
      if (cancelled) return;
      if (caught instanceof ApiRequestError && caught.status === 403) setDenied(true);
      else setError('No se ha podido cargar el catálogo administrativo de rutas.');
    });
    return () => { cancelled = true; };
  }, [auth.status]);

  const selected = useMemo(() => routes.find((route) => route.id === routeId) ?? null, [routeId, routes]);
  const editable = session ? session.platform_access.role !== 'support' : false;
  const canImport = Boolean(editable && selected && file && sourceName.trim() && sourceUrl.trim() && !busy);

  async function importKml() {
    if (!canImport || !file || !selected) return;
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const kml = await file.text();
      const response = await apiFetch<ImportResult>(`/api/v1/admin/routes/${selected.id}/kml`, {
        method: 'POST',
        body: JSON.stringify({
          kml,
          source_name: sourceName.trim(),
          source_url: sourceUrl.trim(),
        }),
      });
      setResult(response);
      const refreshed = await apiFetch<{ routes: AdminRoute[] }>('/api/v1/admin/routes');
      setRoutes(refreshed.routes);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  if (auth.status === 'loading') return <main className="routes-admin-login"><strong>Comprobando acceso…</strong></main>;
  if (auth.status === 'anonymous') return <main className="routes-admin-login"><div><h1>Importar KML oficial</h1><p>Inicia sesión con una cuenta autorizada.</p><GoogleSignInButton /></div></main>;
  if (denied) return <main className="routes-admin-login"><div><h1>Acceso restringido</h1><p>Esta cuenta no tiene permisos de plataforma.</p></div></main>;

  return <main className="routes-admin-shell">
    <header className="routes-admin-header">
      <div><Link href="/admin/rutas">← Gestión de rutas</Link><span>Importación oficial · KML</span><h1>Importar track KML</h1><p>Convierte un KML oficial en un track de revisión sin saltarte la validación editorial.</p></div>
    </header>

    {error ? <div className="routes-admin-notice error">{error}</div> : null}
    {result ? <div className="routes-admin-notice ok">KML importado como versión {result.track.version}. Aún no está validado ni publicado.</div> : null}

    <section className="routes-admin-editor" style={{ maxWidth: 980, margin: '0 auto', width: '100%' }}>
      <div className="routes-admin-card">
        <div className="routes-admin-card-title"><div><span>Paso 1</span><h2>Ruta de destino</h2></div><strong>{selected?.track_status ?? 'sin seleccionar'}</strong></div>
        <label>Ruta
          <select value={routeId} onChange={(event) => { setRouteId(event.target.value); setResult(null); }}>
            <option value="">Selecciona una ruta…</option>
            {routes.map((route) => <option key={route.id} value={route.id}>{route.name} · {route.status} · {route.track_status}</option>)}
          </select>
        </label>
        {selected ? <div className="routes-admin-metrics"><article><span>Distancia actual</span><strong>{km(selected.distance_m)}</strong></article><article><span>Estado</span><strong>{selected.status}</strong></article><article><span>Track</span><strong>{selected.track_status}</strong></article></div> : null}
      </div>

      <div className="routes-admin-card">
        <div className="routes-admin-card-title"><div><span>Paso 2</span><h2>Procedencia oficial</h2></div><strong>Trazabilidad</strong></div>
        <div className="routes-admin-form-grid">
          <label>Fuente<input value={sourceName} onChange={(event) => setSourceName(event.target.value)} placeholder="Organismo oficial" /></label>
          <label className="wide">URL de la ficha o descarga oficial<input type="url" value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} placeholder="https://…" /></label>
        </div>
        <p className="routes-admin-help">La URL queda guardada junto al track. No uses un KML de procedencia desconocida para marcar una ruta como oficial.</p>
      </div>

      <div className="routes-admin-card">
        <div className="routes-admin-card-title"><div><span>Paso 3</span><h2>Fichero KML</h2></div><strong>{file?.name ?? 'pendiente'}</strong></div>
        <label className="routes-admin-upload">Seleccionar KML real<input type="file" accept=".kml,application/vnd.google-earth.kml+xml,application/xml,text/xml" disabled={!editable || busy} onChange={(event) => { setFile(event.target.files?.[0] ?? null); setResult(null); }} /></label>
        <div className="routes-admin-actions"><button disabled={!canImport} onClick={() => void importKml()}>{busy ? 'Procesando KML…' : 'Importar como track pendiente de validación'}</button></div>
        <p className="routes-admin-help">La importación nunca valida automáticamente. Si la ruta estaba publicada, vuelve a revisión hasta que un editor valide expresamente el nuevo track.</p>
      </div>

      {result ? <div className="routes-admin-card">
        <div className="routes-admin-card-title"><div><span>Resultado</span><h2>Revisión técnica obligatoria</h2></div><strong>Versión {result.track.version}</strong></div>
        <div className="routes-admin-metrics">
          <article><span>Distancia KML</span><strong>{km(result.metrics.distance_m)}</strong></article>
          <article><span>Puntos</span><strong>{result.metrics.point_count}</strong></article>
          <article><span>Desnivel +</span><strong>{result.metrics.elevation_gain_m == null ? 'Sin altitud' : `${result.metrics.elevation_gain_m} m`}</strong></article>
          <article><span>Diferencia previa</span><strong>{result.comparison.distance_delta_percent == null ? 'Sin referencia' : `${result.comparison.distance_delta_percent}%`}</strong></article>
        </div>
        {result.route_moved_to_review ? <div className="routes-admin-notice error">La ruta estaba publicada y ha vuelto a revisión automáticamente por seguridad.</div> : null}
        <p className="routes-admin-help">{result.notice}</p>
        <div className="routes-admin-actions"><Link className="admin-button" href="/admin/rutas">Abrir gestión de rutas para validar el track</Link></div>
      </div> : null}
    </section>
  </main>;
}
