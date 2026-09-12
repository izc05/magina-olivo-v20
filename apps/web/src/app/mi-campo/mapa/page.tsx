'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
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

type FieldSummary = {
  id: string;
  name: string;
  municipality: string | null;
  province: string | null;
};

type FieldsPayload = { fields: FieldSummary[] };

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

type LinkSource = 'catastro' | 'sigpac';

function geometrySourceLabel(source: string | null) {
  if (source === 'catastro') return 'Catastro';
  if (source === 'sigpac') return 'SIGPAC';
  if (source === 'manual') return 'Contorno dibujado';
  return 'Límite principal';
}

export default function MiCampoMapPage() {
  const { status, apiConfigured, previewEnabled, selectedWorkspaceId } = useAuth();
  const [fields, setFields] = useState<FieldSummary[]>([]);
  const [selectedFieldId, setSelectedFieldId] = useState('');
  const [context, setContext] = useState<MapContextPayload | null>(null);
  const [loadingFields, setLoadingFields] = useState(false);
  const [loadingContext, setLoadingContext] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [catastroReference, setCatastroReference] = useState('');
  const [sigpacFeatureId, setSigpacFeatureId] = useState('');
  const [setAsGeometry, setSetAsGeometry] = useState(true);
  const [linking, setLinking] = useState<LinkSource | null>(null);
  const [linkFeedback, setLinkFeedback] = useState<string | null>(null);
  const [linkError, setLinkError] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);

  const liveMode = apiConfigured && status === 'authenticated' && Boolean(selectedWorkspaceId);

  useEffect(() => {
    let cancelled = false;

    if (!liveMode || !selectedWorkspaceId) {
      setFields([]);
      setSelectedFieldId('');
      setContext(null);
      setLoadingFields(false);
      setError(null);
      return () => { cancelled = true; };
    }

    setLoadingFields(true);
    setError(null);
    void apiFetch<FieldsPayload>('/api/v1/fields', { workspaceId: selectedWorkspaceId })
      .then((payload) => {
        if (cancelled) return;
        setFields(payload.fields);
        if (!payload.fields.length) {
          setSelectedFieldId('');
          setContext(null);
          setError('Aún no tienes fincas en Mi Campo.');
          return;
        }

        const requestedFieldId = typeof window === 'undefined'
          ? null
          : new URLSearchParams(window.location.search).get('fieldId');
        const requestedExists = requestedFieldId && payload.fields.some((field) => field.id === requestedFieldId);
        setSelectedFieldId((current) => {
          if (current && payload.fields.some((field) => field.id === current)) return current;
          if (requestedExists) return requestedFieldId!;
          return payload.fields[0].id;
        });
      })
      .catch((requestError) => {
        console.error('Unable to load fincas for map', requestError);
        if (!cancelled) setError('No se han podido cargar tus fincas.');
      })
      .finally(() => {
        if (!cancelled) setLoadingFields(false);
      });

    return () => { cancelled = true; };
  }, [liveMode, selectedWorkspaceId]);

  useEffect(() => {
    let cancelled = false;
    if (!liveMode || !selectedWorkspaceId || !selectedFieldId) {
      setContext(null);
      setLoadingContext(false);
      return () => { cancelled = true; };
    }

    setLoadingContext(true);
    setError(null);
    setContext(null);
    void apiFetch<MapContextPayload>(`/api/v1/fields/${selectedFieldId}/map-context`, {
      workspaceId: selectedWorkspaceId,
    })
      .then((payload) => {
        if (!cancelled) setContext(payload);
      })
      .catch((requestError) => {
        console.error('Unable to load finca map context', requestError);
        if (!cancelled) setError('No se ha podido cargar el mapa de esta finca.');
      })
      .finally(() => {
        if (!cancelled) setLoadingContext(false);
      });

    return () => { cancelled = true; };
  }, [liveMode, refreshToken, selectedFieldId, selectedWorkspaceId]);

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
    return previewEnabled && !apiConfigured ? demoData : null;
  }, [apiConfigured, context, previewEnabled]);

  const catastroCount = context?.references.filter((item) => item.source === 'catastro').length ?? 0;
  const sigpacCount = context?.references.filter((item) => item.source === 'sigpac').length ?? 0;

  async function linkReference(event: FormEvent<HTMLFormElement>, source: LinkSource) {
    event.preventDefault();
    if (!selectedWorkspaceId || !selectedFieldId || linking) return;

    const normalizedCatastro = catastroReference.trim().toUpperCase();
    const normalizedSigpac = sigpacFeatureId.trim();

    if (source === 'catastro' && !/^[A-Z0-9]{14}$/.test(normalizedCatastro)) {
      setLinkError('La referencia catastral debe tener 14 letras o números.');
      setLinkFeedback(null);
      return;
    }
    if (source === 'sigpac' && !/^\d{1,20}$/.test(normalizedSigpac)) {
      setLinkError('El identificador del recinto SIGPAC debe contener solo números.');
      setLinkFeedback(null);
      return;
    }

    setLinking(source);
    setLinkError(null);
    setLinkFeedback(null);

    try {
      if (source === 'catastro') {
        await apiFetch(`/api/v1/fields/${selectedFieldId}/land-references/catastro`, {
          method: 'POST',
          workspaceId: selectedWorkspaceId,
          body: JSON.stringify({ reference: normalizedCatastro, set_as_geometry: setAsGeometry }),
        });
        setCatastroReference('');
        setLinkFeedback(setAsGeometry
          ? 'Catastro vinculado. El límite de la parcela ya se usa como límite principal de la finca.'
          : 'Catastro vinculado como referencia de la finca.');
      } else {
        await apiFetch(`/api/v1/fields/${selectedFieldId}/land-references/sigpac`, {
          method: 'POST',
          workspaceId: selectedWorkspaceId,
          body: JSON.stringify({ feature_id: normalizedSigpac, set_as_geometry: setAsGeometry }),
        });
        setSigpacFeatureId('');
        setLinkFeedback(setAsGeometry
          ? 'SIGPAC vinculado. El límite del recinto ya se usa como límite principal de la finca.'
          : 'SIGPAC vinculado como referencia de la finca.');
      }
      setRefreshToken((value) => value + 1);
    } catch (requestError) {
      console.error('Unable to link land reference', requestError);
      setLinkError(source === 'catastro'
        ? 'No se ha podido comprobar esa referencia en Catastro. Revisa el dato y vuelve a intentarlo.'
        : 'No se ha podido comprobar ese recinto en SIGPAC. Revisa el dato y vuelve a intentarlo.');
    } finally {
      setLinking(null);
    }
  }

  return <main className="app-shell">
    <Topbar/>
    <div className="page map-platform-page">
      <header className="map-platform-header">
        <div>
          <span className="eyebrow">MI CAMPO · MAPA</span>
          <h1>Tu finca sobre el terreno</h1>
          <p>Consulta sus límites y vincula Catastro o SIGPAC sin cambiar el nombre ni la identidad de tu finca.</p>
        </div>
      </header>

      {!apiConfigured && !previewEnabled ? <section className="card map-platform-empty"><h2>Mapa no disponible</h2><p>Mi Campo no está conectado en este entorno.</p></section> : null}

      {apiConfigured && status === 'anonymous' ? <section className="card map-platform-empty">
        <h2>Entra para ver tus fincas</h2>
        <p>El mapa de Mi Campo es privado. La Guía de Mágina continúa disponible sin cuenta.</p>
        <GoogleSignInButton/>
      </section> : null}

      {apiConfigured && status === 'loading' ? <section className="card map-platform-empty"><h2>Comprobando tu sesión…</h2><p>Preparando tus fincas.</p></section> : null}

      {liveMode ? <section className="card map-linker-panel">
        <div className="map-linker-heading">
          <div>
            <span className="eyebrow dark">LÍMITES DE LA FINCA</span>
            <h2>Añade los límites reales</h2>
            <p>Elige la finca y vincula una parcela de Catastro o un recinto SIGPAC. Puedes guardarlo solo como referencia o usarlo como límite principal.</p>
          </div>
          <label className="map-field-selector">
            <span>Finca</span>
            <select value={selectedFieldId} onChange={(event) => {
              setSelectedFieldId(event.target.value);
              setLinkFeedback(null);
              setLinkError(null);
            }} disabled={loadingFields || !fields.length}>
              {!fields.length ? <option value="">{loadingFields ? 'Cargando fincas…' : 'Sin fincas'}</option> : null}
              {fields.map((field) => <option key={field.id} value={field.id}>{field.name}{field.municipality ? ` · ${field.municipality}` : ''}</option>)}
            </select>
          </label>
        </div>

        {fields.length ? <>
          <label className="map-primary-boundary"><input type="checkbox" checked={setAsGeometry} onChange={(event) => setSetAsGeometry(event.target.checked)} /> <span><strong>Usar como límite principal</strong><small>Desmárcalo si solo quieres guardar la referencia para consultarla.</small></span></label>
          <div className="map-link-grid">
            <form className="map-link-form" onSubmit={(event) => void linkReference(event, 'catastro')}>
              <div><span className="map-source-badge catastro">Catastro</span><h3>Referencia catastral</h3><p>Introduce la referencia de 14 caracteres de la parcela.</p></div>
              <label><span>Referencia catastral</span><input value={catastroReference} onChange={(event) => setCatastroReference(event.target.value.toUpperCase())} maxLength={14} autoCapitalize="characters" placeholder="14 letras o números" /></label>
              <button className="primary" type="submit" disabled={linking !== null || !selectedFieldId}>{linking === 'catastro' ? 'Comprobando…' : 'Vincular Catastro'}</button>
            </form>
            <form className="map-link-form" onSubmit={(event) => void linkReference(event, 'sigpac')}>
              <div><span className="map-source-badge sigpac">SIGPAC</span><h3>Recinto SIGPAC</h3><p>Introduce el identificador numérico del recinto.</p></div>
              <label><span>ID del recinto SIGPAC</span><input value={sigpacFeatureId} onChange={(event) => setSigpacFeatureId(event.target.value.replace(/\D/g, ''))} inputMode="numeric" maxLength={20} placeholder="Identificador del recinto" /></label>
              <button className="primary" type="submit" disabled={linking !== null || !selectedFieldId}>{linking === 'sigpac' ? 'Comprobando…' : 'Vincular SIGPAC'}</button>
            </form>
          </div>
          {linkFeedback ? <p className="map-link-feedback success" role="status">✓ {linkFeedback}</p> : null}
          {linkError ? <p className="map-link-feedback error" role="alert">{linkError}</p> : null}
        </> : !loadingFields ? <div className="map-no-fields"><p>Crea primero una finca y después podrás añadir sus límites.</p><Link className="primary action-link" href="/mi-campo/fincas/nueva">Crear finca →</Link></div> : null}
      </section> : null}

      {loadingContext ? <section className="card map-platform-empty"><h2>Cargando el mapa…</h2><p>Estamos preparando los límites y referencias de la finca seleccionada.</p></section> : null}
      {error ? <section className="card map-platform-empty"><h2>Mapa todavía sin datos</h2><p>{error}</p></section> : null}

      {mapData && (previewEnabled || status === 'authenticated') ? <>
        <section className="card map-platform-shell">
          <FarmMap data={mapData}/>
          <div className="map-platform-statusbar">
            <span className="map-chip"><i/> Finca</span>
            <span className="map-chip catastro"><i/> Catastro {liveMode ? catastroCount : '· demo'}</span>
            <span className="map-chip sigpac"><i/> SIGPAC {liveMode ? sigpacCount : '· demo'}</span>
            <span className="map-chip">📍 Mi ubicación</span>
          </div>
        </section>

        <section className="map-platform-info">
          <article className="card"><span className="map-info-icon">▱</span><div><strong>{context?.field.geometry_status === 'verified' ? 'Límites confirmados' : liveMode ? 'Límites pendientes' : 'Límites de demostración'}</strong><small>{context?.field.geometry_source ? `Origen: ${geometrySourceLabel(context.field.geometry_source)}` : 'Puedes vincular Catastro o SIGPAC cuando quieras'}</small></div></article>
          <article className="card"><span className="map-info-icon">◎</span><div><strong>{context?.field.calculated_area_ha != null ? `${context.field.calculated_area_ha.toLocaleString('es-ES')} ha` : 'Superficie pendiente'}</strong><small>{context ? `${context.field.municipality ?? 'Pueblo sin indicar'} · ${context.field.province ?? 'Provincia sin indicar'}` : 'Vista de demostración de Sierra Mágina'}</small></div></article>
          <article className="card"><span className="map-info-icon">⌁</span><div><strong>{context ? `${context.references.length} referencias asociadas` : 'Referencias de demostración'}</strong><small>Catastro y SIGPAC se guardan como información de tu finca.</small></div></article>
        </section>

        {previewEnabled && !apiConfigured ? <section className="card no-duplicate-note"><strong>Modo demostración</strong><p>El contorno mostrado sirve para enseñar cómo funciona el mapa; no corresponde a una finca real guardada.</p></section> : null}
      </> : null}
    </div>
    <BottomNav active="/mi-campo"/>
  </main>;
}
