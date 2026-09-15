'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import { ArrowIcon, MapPinIcon, RainIcon } from './icons';
import { RadarAlertSettings } from './radar-alert-settings';
import { FarmMap, type FarmMapData, type MapGeometry, type RadarMapOverlay } from './farm-map';
import { apiFetch, apiFetchBlob, ApiRequestError } from '../lib/api-client';

type FieldSummary = { id: string; name: string; municipality: string | null };
type FieldsPayload = { fields: FieldSummary[] };

type PointGeometry = { type: 'Point'; coordinates: [number, number] };
type MapContextPayload = {
  field: {
    id: string;
    name: string;
    geometry: MapGeometry | null;
    centroid: PointGeometry | null;
    representative_point: PointGeometry | null;
    geometry_status?: string | null;
  };
};

type RadarObservation = {
  id: string;
  observed_at: string | null;
  fetched_at?: string | null;
  coverage_status: 'covered' | 'partial' | 'outside' | 'unavailable' | null;
  precipitation_detected: boolean | null;
  nearest_echo: {
    distance_km: number | null;
    direction_degrees: number | null;
    direction: 'N' | 'NE' | 'E' | 'SE' | 'S' | 'SW' | 'W' | 'NW' | 'OVER_FIELD' | null;
    reflectivity_dbz: { min: number | null; max: number | null };
  } | null;
  analysis_radius_km: number | null;
  quality_flags: string[];
  analysis_version: string | null;
  source: string | null;
  product: string | null;
};

type RadarLatestPayload = {
  field_id: string;
  observation: RadarObservation | null;
  summary: string;
  attribution: string;
  freshness?: { status: 'fresh' | 'stale' | 'unknown' | 'unavailable'; age_seconds: number | null; stale_after_minutes: number };
  overlay?: { status: 'ready' | 'unavailable'; bbox: [number, number, number, number] | null; url: string | null };
  semantics?: 'observed_reflectivity_not_forecast';
};

type WeatherPayload = {
  forecast: {
    elaboratedAt: string | null;
    days: Array<{
      date: string;
      precipitationProbabilityPercent: number | null;
      temperatureMinC: number | null;
      temperatureMaxC: number | null;
      windMaxKmh: number | null;
    }>;
  };
  cache_status: 'fresh' | 'refreshed' | 'stale';
  fetched_at: string;
  stale: boolean;
};

type SourceState<T> =
  | { kind: 'idle' | 'loading' }
  | { kind: 'ready'; data: T }
  | { kind: 'empty' }
  | { kind: 'error'; message: string };

type FieldState =
  | { kind: 'idle' | 'loading' }
  | { kind: 'ready'; field: FieldSummary }
  | { kind: 'empty' }
  | { kind: 'error'; message: string };

function formatObservedAt(value: string | null | undefined) {
  if (!value) return 'Hora no disponible';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Hora no disponible';
  return new Intl.DateTimeFormat('es-ES', {
    day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', timeZoneName: 'short',
  }).format(date);
}

function reflectivityLabel(observation: RadarObservation | null) {
  const band = observation?.nearest_echo?.reflectivity_dbz;
  if (!band || band.min == null) return null;
  if (band.max == null) return `${band.min} dBZ o más`;
  return `${band.min}–${band.max} dBZ`;
}

function weatherToday(weather: WeatherPayload) {
  const today = new Date().toISOString().slice(0, 10);
  return weather.forecast.days.find((day) => day.date === today) ?? weather.forecast.days[0] ?? null;
}

function SourceStatus({ label, state, stale = false }: { label: string; state: string; stale?: boolean }) {
  return <span className={stale ? 'active-alert' : 'tag'}>{label}: {state}</span>;
}

export function RadarObservationPanel() {
  const { status, apiConfigured, selectedWorkspaceId } = useAuth();
  const [refreshKey, setRefreshKey] = useState(0);
  const [fieldState, setFieldState] = useState<FieldState>({ kind: 'idle' });
  const [mapState, setMapState] = useState<SourceState<MapContextPayload>>({ kind: 'idle' });
  const [radarState, setRadarState] = useState<SourceState<RadarLatestPayload>>({ kind: 'idle' });
  const [weatherState, setWeatherState] = useState<SourceState<WeatherPayload>>({ kind: 'idle' });
  const [overlayState, setOverlayState] = useState<SourceState<RadarMapOverlay>>({ kind: 'idle' });

  const refresh = useCallback(() => setRefreshKey((value) => value + 1), []);

  useEffect(() => {
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId) {
      setFieldState({ kind: 'idle' });
      return;
    }

    let cancelled = false;
    setFieldState({ kind: 'loading' });
    void (async () => {
      try {
        const fieldList = await apiFetch<FieldsPayload>('/api/v1/fields', { workspaceId: selectedWorkspaceId });
        if (cancelled) return;
        const preferredId = typeof window !== 'undefined' ? new URLSearchParams(window.location.search).get('fieldId') : null;
        const field = (preferredId && fieldList.fields.find((item) => item.id === preferredId)) ?? fieldList.fields[0];
        setFieldState(field ? { kind: 'ready', field } : { kind: 'empty' });
      } catch {
        if (!cancelled) setFieldState({ kind: 'error', message: 'No hemos podido cargar tus fincas.' });
      }
    })();
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status, refreshKey]);

  useEffect(() => {
    if (fieldState.kind !== 'ready' || !selectedWorkspaceId) return;
    const { field } = fieldState;
    let cancelled = false;
    let objectUrl: string | null = null;

    setMapState({ kind: 'loading' });
    setRadarState({ kind: 'loading' });
    setWeatherState({ kind: 'loading' });
    setOverlayState({ kind: 'loading' });

    void apiFetch<MapContextPayload>(`/api/v1/fields/${field.id}/map-context`, { workspaceId: selectedWorkspaceId })
      .then((data) => {
        if (cancelled) return;
        setMapState(data.field.geometry ? { kind: 'ready', data } : { kind: 'empty' });
      })
      .catch(() => { if (!cancelled) setMapState({ kind: 'error', message: 'No se ha podido cargar la geometría de la finca.' }); });

    void apiFetch<WeatherPayload>(`/api/v1/fields/${field.id}/weather/daily`, { workspaceId: selectedWorkspaceId })
      .then((data) => { if (!cancelled) setWeatherState(data.forecast.days.length ? { kind: 'ready', data } : { kind: 'empty' }); })
      .catch((error) => {
        if (cancelled) return;
        const noData = error instanceof ApiRequestError && (error.status === 404 || error.status === 422);
        setWeatherState(noData ? { kind: 'empty' } : { kind: 'error', message: 'La previsión AEMET no está disponible ahora.' });
      });

    void apiFetch<RadarLatestPayload>(`/api/v1/fields/${field.id}/radar/latest`, { workspaceId: selectedWorkspaceId })
      .then(async (radar) => {
        if (cancelled) return;
        setRadarState(radar.observation ? { kind: 'ready', data: radar } : { kind: 'empty' });
        if (!radar.overlay || radar.overlay.status !== 'ready' || !radar.overlay.url || !radar.overlay.bbox) {
          setOverlayState({ kind: 'empty' });
          return;
        }
        try {
          const blob = await apiFetchBlob(radar.overlay.url, { workspaceId: selectedWorkspaceId });
          if (cancelled) return;
          objectUrl = URL.createObjectURL(blob);
          setOverlayState({ kind: 'ready', data: { imageUrl: objectUrl, bbox: radar.overlay.bbox } });
        } catch {
          if (!cancelled) setOverlayState({ kind: 'error', message: 'El raster radar no se ha podido cargar.' });
        }
      })
      .catch((error) => {
        if (cancelled) return;
        const noData = error instanceof ApiRequestError && error.status === 404;
        setRadarState(noData ? { kind: 'empty' } : { kind: 'error', message: 'La observación radar no está disponible ahora.' });
        setOverlayState({ kind: 'empty' });
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [fieldState, selectedWorkspaceId]);

  const reflectivity = useMemo(
    () => radarState.kind === 'ready' ? reflectivityLabel(radarState.data.observation) : null,
    [radarState],
  );

  if (!apiConfigured) {
    return <section className="card rain-settings">
      <div><strong>API no configurada</strong><p>La vista radar funcional necesita la API para cargar AEMET, geometría y observaciones reales. No se muestran datos simulados.</p></div>
    </section>;
  }

  if (status === 'loading' || fieldState.kind === 'loading') {
    return <section className="card radar-context" aria-busy="true"><div><strong>Cargando clima de la finca…</strong><small>Consultando finca, AEMET y radar.</small></div></section>;
  }

  if (status !== 'authenticated') {
    return <section className="card rain-settings">
      <div><span className="eyebrow dark">MI CAMPO</span><h2>Entra para ver el clima de tus fincas</h2><p>La geometría y las observaciones asociadas a una finca son privadas.</p></div>
      <Link href="/perfil" className="detail-link">Entrar <ArrowIcon /></Link>
    </section>;
  }

  if (fieldState.kind === 'empty') {
    return <section className="card rain-settings">
      <div><span className="eyebrow dark">CLIMA</span><h2>Aún no hay una finca disponible</h2><p>Crea una finca para consultar la previsión y, cuando tenga geometría, situarla sobre el radar.</p></div>
      <Link href="/mi-campo/fincas/nueva" className="detail-link">Añadir finca <ArrowIcon /></Link>
    </section>;
  }

  if (fieldState.kind === 'error') {
    return <section className="card rain-settings"><strong>No se han podido cargar las fincas</strong><p>{fieldState.message}</p><button type="button" className="detail-link" onClick={refresh}>Reintentar</button></section>;
  }
  if (fieldState.kind !== 'ready') return null;

  const field = fieldState.field;
  const mapData: FarmMapData | null = mapState.kind === 'ready' ? {
    id: mapState.data.field.id,
    name: mapState.data.field.name,
    geometry: mapState.data.field.geometry,
    centroid: mapState.data.field.centroid,
    representative_point: mapState.data.field.representative_point,
    references: [],
  } : null;
  const radar = radarState.kind === 'ready' ? radarState.data : null;
  const observation = radar?.observation ?? null;
  const weather = weatherState.kind === 'ready' ? weatherState.data : null;
  const today = weather ? weatherToday(weather) : null;
  const radarStale = radar?.freshness?.status === 'stale';
  const weatherStale = weather?.stale === true;

  return <>
    <section className="card radar-context">
      <div className="radar-farm-thumb" />
      <div><strong>{field.name}</strong><small><MapPinIcon /> {field.municipality ?? 'Municipio sin indicar'}</small></div>
      <button type="button" className="detail-link" onClick={refresh} aria-label="Actualizar clima y radar">Actualizar</button>
    </section>

    <section className="section card rain-settings" aria-label="Estado de fuentes meteorológicas">
      <div className="settings-grid">
        <SourceStatus label="Finca" state={mapState.kind === 'ready' ? 'geometría lista' : mapState.kind === 'loading' ? 'cargando' : mapState.kind === 'empty' ? 'sin geometría' : 'error'} />
        <SourceStatus label="AEMET" state={weatherState.kind === 'ready' ? (weatherStale ? 'dato anterior' : 'actual') : weatherState.kind === 'loading' ? 'cargando' : weatherState.kind === 'empty' ? 'sin datos' : 'error'} stale={weatherStale} />
        <SourceStatus label="Radar" state={radarState.kind === 'ready' ? (radarStale ? 'observación antigua' : 'actual') : radarState.kind === 'loading' ? 'cargando' : radarState.kind === 'empty' ? 'sin datos' : 'error'} stale={radarStale} />
      </div>
    </section>

    {mapData ? <section className="radar-map" aria-label="Mapa de finca con radar observado">
      <div className="radar-label">{observation ? `Radar observado · ${formatObservedAt(observation.observed_at)}` : 'Finca · radar sin observación'}</div>
      <FarmMap data={mapData} radarOverlay={overlayState.kind === 'ready' ? overlayState.data : null} />
      {overlayState.kind === 'loading' ? <p>Preparando overlay radar…</p> : null}
      {overlayState.kind === 'error' ? <p>{overlayState.message} La geometría de la finca sigue disponible.</p> : null}
      {overlayState.kind === 'empty' && radarState.kind === 'ready' ? <p>Esta observación no dispone de raster validado para superponer.</p> : null}
    </section> : <section className="card rain-settings">
      <strong>{mapState.kind === 'loading' ? 'Cargando mapa…' : mapState.kind === 'error' ? 'Mapa temporalmente no disponible' : 'Finca sin geometría'}</strong>
      <p>{mapState.kind === 'error' ? mapState.message : 'El clima puede consultarse, pero el overlay radar necesita una geometría canónica de la finca.'}</p>
    </section>}

    <section className="section rain-warning">
      <span className="rain-warning-icon"><RainIcon /></span>
      <div>
        <strong>{radarState.kind === 'loading' ? 'Consultando la última observación radar…' : radarState.kind === 'error' ? radarState.message : radarState.kind === 'empty' ? 'Todavía no hay observación radar para esta finca.' : radar?.summary}</strong>
        <small>{reflectivity ? `Eco más próximo: ${reflectivity}. ` : ''}{radarStale ? 'La observación es antigua; pulsa Actualizar para reconsultar. ' : ''}Reflectividad observada; no es pronóstico ni estima hora de llegada.</small>
      </div>
    </section>

    <section className="section card rain-settings">
      <div><span className="eyebrow dark">PREVISIÓN AEMET</span><h2>{weatherState.kind === 'loading' ? 'Consultando previsión…' : weatherState.kind === 'error' ? 'Previsión temporalmente no disponible' : weatherState.kind === 'empty' ? 'Sin previsión para esta finca' : today ? `${today.temperatureMinC ?? '—'}–${today.temperatureMaxC ?? '—'} °C` : 'Sin dato diario'}</h2>
        {weatherState.kind === 'error' ? <p>{weatherState.message} El radar y el mapa siguen funcionando de forma independiente.</p> : null}
        {today ? <p>Probabilidad de precipitación: {today.precipitationProbabilityPercent ?? '—'}% · Viento máx.: {today.windMaxKmh ?? '—'} km/h.{weatherStale ? ' Se muestra la última previsión guardada porque AEMET no respondió al refresco.' : ''}</p> : null}
      </div>
    </section>

    <RadarAlertSettings fieldId={field.id} />

    <section className="section card rain-settings">
      <div><span className="eyebrow dark">LECTURA SEGURA</span><h2>Observación y previsión separadas</h2><p>El overlay representa reflectividad radar AEMET georreferenciada. Mágina no convierte automáticamente dBZ en mm/h ni calcula nowcast o ETA sin una metodología validada.</p></div>
      <div className="settings-grid">
        <span><b>Cobertura radar</b> {observation?.coverage_status ?? 'sin dato'}</span>
        <span><b>Radio analizado</b> {observation?.analysis_radius_km != null ? `${observation.analysis_radius_km} km` : 'sin dato'}</span>
        <span><b>Fuente</b> {radar?.attribution ?? 'AEMET'}</span>
      </div>
    </section>
  </>;
}
