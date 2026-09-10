'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import { ArrowIcon, MapPinIcon, RainIcon } from './icons';
import { RadarAlertSettings } from './radar-alert-settings';
import { apiFetch, ApiRequestError } from '../lib/api-client';

type FieldSummary = {
  id: string;
  name: string;
  municipality: string | null;
};

type FieldsPayload = {
  fields: FieldSummary[];
};

type RadarObservation = {
  id: string;
  observed_at: string | null;
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
  semantics?: 'observed_reflectivity_not_forecast';
};

type RadarState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'ready'; field: FieldSummary; radar: RadarLatestPayload }
  | { kind: 'empty' }
  | { kind: 'error'; message: string };

function formatObservedAt(value: string | null) {
  if (!value) return 'Hora no disponible';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Hora no disponible';
  return new Intl.DateTimeFormat('es-ES', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
  }).format(date);
}

function reflectivityLabel(observation: RadarObservation | null) {
  const band = observation?.nearest_echo?.reflectivity_dbz;
  if (!band || band.min == null) return null;
  if (band.max == null) return `${band.min} dBZ o más`;
  return `${band.min}–${band.max} dBZ`;
}

export function RadarObservationPanel() {
  const { status, apiConfigured, selectedWorkspaceId } = useAuth();
  const [state, setState] = useState<RadarState>({ kind: 'idle' });

  useEffect(() => {
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId) {
      setState({ kind: 'idle' });
      return;
    }

    let cancelled = false;
    setState({ kind: 'loading' });

    void (async () => {
      try {
        const fieldList = await apiFetch<FieldsPayload>('/api/v1/fields', {
          workspaceId: selectedWorkspaceId,
        });
        if (cancelled) return;
        const preferredId = typeof window !== 'undefined'
          ? new URLSearchParams(window.location.search).get('fieldId')
          : null;
        const field = (preferredId && fieldList.fields.find((item) => item.id === preferredId))
          ?? fieldList.fields[0];
        if (!field) {
          setState({ kind: 'empty' });
          return;
        }

        const radar = await apiFetch<RadarLatestPayload>(`/api/v1/fields/${field.id}/radar/latest`, {
          workspaceId: selectedWorkspaceId,
        });
        if (!cancelled) setState({ kind: 'ready', field, radar });
      } catch (error) {
        if (cancelled) return;
        if (error instanceof ApiRequestError && error.status === 404) {
          setState({ kind: 'empty' });
          return;
        }
        setState({ kind: 'error', message: 'No hemos podido consultar el radar de la finca.' });
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const reflectivity = useMemo(
    () => state.kind === 'ready' ? reflectivityLabel(state.radar.observation) : null,
    [state],
  );

  if (!apiConfigured) {
    return <>
      <section className="card radar-context">
        <div className="radar-farm-thumb" />
        <div><strong>Vista de demostración</strong><small><MapPinIcon /> La observación real se activa al conectar la API.</small></div>
        <span className="active-alert">PREVIEW</span>
      </section>
      <section className="radar-map premium-radar">
        <div className="radar-label">Radar AEMET · preview visual</div>
        <div className="radar-pin"><MapPinIcon /> Tu finca</div>
      </section>
      <section className="section rain-warning">
        <span className="rain-warning-icon"><RainIcon /></span>
        <div><strong>Sin datos radar reales en esta preview</strong><small>No mostramos distancias, intensidades ni horas inventadas.</small></div>
      </section>
    </>;
  }

  if (status === 'loading' || state.kind === 'loading') {
    return <section className="card radar-context"><div><strong>Consultando radar…</strong><small>Buscando la última observación disponible.</small></div></section>;
  }

  if (status !== 'authenticated') {
    return <section className="card rain-settings">
      <div><span className="eyebrow dark">MI CAMPO</span><h2>Entra para ver el radar de tus fincas</h2><p>La geometría y las observaciones asociadas a una finca son privadas.</p></div>
      <Link href="/perfil" className="detail-link">Entrar <ArrowIcon /></Link>
    </section>;
  }

  if (state.kind === 'empty') {
    return <section className="card rain-settings">
      <div><span className="eyebrow dark">RADAR</span><h2>Aún no hay una finca disponible</h2><p>Crea una finca y confirma su ubicación para poder analizar el radar a su alrededor.</p></div>
      <Link href="/mi-campo/fincas/nueva" className="detail-link">Añadir finca <ArrowIcon /></Link>
    </section>;
  }

  if (state.kind === 'error') {
    return <section className="card rain-settings"><div><strong>Radar temporalmente no disponible</strong><p>{state.message}</p></div></section>;
  }

  if (state.kind !== 'ready') return null;

  const { field, radar } = state;
  const observation = radar.observation;

  return <>
    <section className="card radar-context">
      <div className="radar-farm-thumb" />
      <div><strong>{field.name}</strong><small><MapPinIcon /> {field.municipality ?? 'Ubicación de finca'}</small></div>
      <span className="active-alert">AEMET</span>
    </section>

    <section className="radar-map premium-radar">
      <div className="radar-label">Observación · {formatObservedAt(observation?.observed_at ?? null)}</div>
      <div className="radar-legend"><span>Reflectividad observada</span><span>No es previsión</span></div>
      <div className="radar-pin"><MapPinIcon /> {field.name}</div>
    </section>

    <section className="section rain-warning">
      <span className="rain-warning-icon"><RainIcon /></span>
      <div>
        <strong>{radar.summary}</strong>
        <small>{reflectivity ? `Eco más próximo: ${reflectivity}. ` : ''}Dato de reflectividad observado; no estima hora de llegada.</small>
      </div>
      <ArrowIcon />
    </section>

    <RadarAlertSettings fieldId={field.id} />

    <section className="section card rain-settings">
      <div><span className="eyebrow dark">LECTURA SEGURA</span><h2>Radar observado, no pronóstico</h2><p>La previsión meteorológica y las futuras alertas de movimiento se calculan por separado. Mágina no convierte automáticamente dBZ en mm/h.</p></div>
      <div className="settings-grid">
        <span><b>Cobertura</b> {observation?.coverage_status ?? 'sin dato'}</span>
        <span><b>Radio analizado</b> {observation?.analysis_radius_km != null ? `${observation.analysis_radius_km} km` : 'sin dato'}</span>
        <span><b>Fuente</b> {radar.attribution}</span>
      </div>
    </section>
  </>;
}
