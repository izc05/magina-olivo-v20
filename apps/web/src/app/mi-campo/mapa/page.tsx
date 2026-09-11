'use client';

import { useEffect, useMemo, useState } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { FarmMap, type FarmMapData, type MapGeometry } from '@/components/farm-map';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { Topbar } from '@/components/topbar';
import { useAuth } from '@/components/auth-provider';
import { apiFetch } from '@/lib/api-client';

const demoGeometry: MapGeometry = {
  type: 'Polygon',
  coordinates: [[
    [-3.4684, 37.6514],
    [-3.4664, 37.6512],
    [-3.4658, 37.6528],
    [-3.4677, 37.6532],
    [-3.4684, 37.6514],
  ]],
};

const demoData: FarmMapData = {
  id: 'demo-las-cenillas',
  name: 'Las Cenillas · demostración',
  geometry: demoGeometry,
  representative_point: { type: 'Point', coordinates: [-3.4671, 37.6522] },
  centroid: { type: 'Point', coordinates: [-3.4671, 37.6522] },
  references: [],
};

type FieldsPayload = {
  fields: Array<{
    id: string;
    name: string;
    municipality: string | null;
    province: string | null;
  }>;
};

type MapContextPayload = {
  field: {
    id: string;
    name: string;
    municipality: string | null;
    province: string | null;
    calculated_area_ha: number | null;
    geometry_source: string | null;
    geometry_status: string;
    geometry_checked_at: string | null;
    geometry: MapGeometry | null;
    centroid: { type: 'Point'; coordinates: [number, number] } | null;
    representative_point: { type: 'Point'; coordinates: [number, number] } | null;
  };
  references: FarmMapData['references'];
};

export default function MiCampoMapPage() {
  const { status, apiConfigured, previewEnabled, selectedWorkspaceId } = useAuth();
  const [context, setContext] = useState<MapContextPayload | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId) {
      setContext(null);
      setLoading(false);
      setError(null);
      return () => { cancelled = true; };
    }

    setLoading(true);
    setError(null);
    void (async () => {
      try {
        const fields = await apiFetch<FieldsPayload>('/api/v1/fields', { workspaceId: selectedWorkspaceId });
        const field = fields.fields[0];
        if (!field) {
          if (!cancelled) setError('Aún no hay fincas en este espacio.');
          return;
        }
        const mapContext = await apiFetch<MapContextPayload>(`/api/v1/fields/${field.id}/map-context`, {
          workspaceId: selectedWorkspaceId,
        });
        if (!cancelled) setContext(mapContext);
      } catch (requestError) {
        console.error('Unable to load finca map context', requestError);
        if (!cancelled) setError('No se ha podido cargar el mapa de la finca.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const mapData = useMemo<FarmMapData | null>(() => {
    if (context) {
      return {
        id: context.field.id,
        name: context.field.name,
        geometry: context.field.geometry,
        centroid: context.field.centroid,
        representative_point: context.field.representative_point,
        references: context.references,
      };
    }
    return previewEnabled ? demoData : null;
  }, [context, previewEnabled]);

  const catastroCount = context?.references.filter((item) => item.source === 'catastro').length ?? 0;
  const sigpacCount = context?.references.filter((item) => item.source === 'sigpac').length ?? 0;
  const liveMode = apiConfigured && status === 'authenticated';

  return <main className="app-shell">
    <Topbar/>
    <div className="page map-platform-page">
      <header className="map-platform-header">
        <div>
          <span className="eyebrow">MI CAMPO · MAPA V20</span>
          <h1>Tu finca sobre el terreno</h1>
          <p>Una sola vista para la finca, Catastro, SIGPAC y las próximas capas de ortofoto y lluvia.</p>
        </div>
      </header>

      {!apiConfigured && !previewEnabled ? <section className="card map-platform-empty"><h2>Mapa privado no disponible</h2><p>Esta instalación no tiene configurada la API de Mi Campo.</p></section> : null}

      {apiConfigured && status === 'anonymous' ? <section className="card map-platform-empty">
        <h2>Entra para ver tus fincas</h2>
        <p>El mapa de Mi Campo es privado. La Guía de Mágina continúa disponible sin cuenta.</p>
        <GoogleSignInButton/>
      </section> : null}

      {apiConfigured && status === 'loading' ? <section className="card map-platform-empty"><h2>Comprobando tu sesión…</h2><p>Preparando el contexto privado del mapa.</p></section> : null}

      {mapData && (previewEnabled || status === 'authenticated') ? <>
        <section className="card map-platform-shell">
          <FarmMap data={mapData}/>
          <div className="map-platform-statusbar">
            <span className="map-chip"><i/> Finca</span>
            <span className="map-chip catastro"><i/> Catastro {liveMode ? catastroCount : '· preview'}</span>
            <span className="map-chip sigpac"><i/> SIGPAC {liveMode ? sigpacCount : '· preview'}</span>
            <span className="map-chip">📍 Mi ubicación</span>
          </div>
        </section>

        {loading ? <section className="card map-platform-empty"><h2>Cargando datos reales…</h2><p>La vista se actualizará con la geometría del workspace.</p></section> : null}
        {error ? <section className="card map-platform-empty"><h2>Mapa todavía sin datos</h2><p>{error}</p></section> : null}

        <section className="map-platform-info">
          <article className="card"><span className="map-info-icon">▱</span><div><strong>{context?.field.geometry_status === 'verified' ? 'Geometría verificada' : liveMode ? 'Geometría por completar' : 'Geometría demo'}</strong><small>{context?.field.geometry_source ? `Origen: ${context.field.geometry_source}` : 'La finca seguirá siendo la entidad principal'}</small></div></article>
          <article className="card"><span className="map-info-icon">◎</span><div><strong>{context?.field.calculated_area_ha != null ? `${context.field.calculated_area_ha.toLocaleString('es-ES')} ha` : 'Superficie pendiente'}</strong><small>{context ? `${context.field.municipality ?? 'Municipio sin indicar'} · ${context.field.province ?? 'Provincia sin indicar'}` : 'Vista conceptual de Sierra Mágina'}</small></div></article>
          <article className="card"><span className="map-info-icon">⌁</span><div><strong>{context ? `${context.references.length} referencias` : 'Referencias de preview'}</strong><small>Vincular Catastro/SIGPAC no cambia la finca sin confirmación.</small></div></article>
        </section>

        {previewEnabled && !apiConfigured ? <section className="card no-duplicate-note"><strong>Modo preview explícito</strong><p>Esta geometría es solo una demostración visual de GitHub Pages y no representa una finca persistida.</p></section> : null}
      </> : null}
    </div>
    <BottomNav active="/mi-campo"/>
  </main>;
}
