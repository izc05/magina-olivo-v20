'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { apiFetch } from '@/lib/api-client';
import { useAuth } from '@/components/auth-provider';
import { FarmGisSelector, type FarmMapContext, type GisSelection } from '@/components/farm-gis-selector';

type FieldRecord = {
  id: string;
  name: string;
  place_id: string | null;
  municipality: string | null;
  province: string | null;
  tree_count: number | null;
  variety: string | null;
  water_regime: 'secano' | 'regadio' | 'mixto' | null;
  calculated_area_ha: number | null;
  geometry_source: string | null;
  geometry_status: string;
};

type TerritoryPlace = {
  id: string;
  name: string;
  municipality_name: string;
};

type TerritoryPayload = { places: TerritoryPlace[] };

type FieldPayload = { field: FieldRecord };

export function FarmEditClient() {
  const params = useSearchParams();
  const fieldId = params.get('fieldId');
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [field, setField] = useState<FieldRecord | null>(null);
  const [mapContext, setMapContext] = useState<FarmMapContext | null>(null);
  const [places, setPlaces] = useState<TerritoryPlace[]>([]);
  const [name, setName] = useState('');
  const [trees, setTrees] = useState('');
  const [placeId, setPlaceId] = useState('');
  const [variety, setVariety] = useState('');
  const [waterRegime, setWaterRegime] = useState<FieldRecord['water_regime']>('secano');
  const [selection, setSelection] = useState<GisSelection | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingDetails, setSavingDetails] = useState(false);
  const [savingGeometry, setSavingGeometry] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [selectorVersion, setSelectorVersion] = useState(0);

  const load = useCallback(async (resetForm = true) => {
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId || !fieldId) return;
    setLoading(true);
    setError(null);
    try {
      const [fieldPayload, contextPayload, territoryPayload] = await Promise.all([
        apiFetch<FieldPayload>(`/api/v1/fields/${encodeURIComponent(fieldId)}`, { workspaceId: selectedWorkspaceId }),
        apiFetch<FarmMapContext>(`/api/v1/fields/${encodeURIComponent(fieldId)}/map-context`, { workspaceId: selectedWorkspaceId }),
        apiFetch<TerritoryPayload>('/api/v1/public/territory/places'),
      ]);
      setField(fieldPayload.field);
      setMapContext(contextPayload);
      setPlaces(territoryPayload.places);
      if (resetForm) {
        setName(fieldPayload.field.name);
        setTrees(fieldPayload.field.tree_count == null ? '' : String(fieldPayload.field.tree_count));
        setPlaceId(fieldPayload.field.place_id ?? '');
        setVariety(fieldPayload.field.variety ?? '');
        setWaterRegime(fieldPayload.field.water_regime ?? 'secano');
      }
    } catch (loadError) {
      console.error('Unable to load finca for editing', loadError);
      setError('No se ha podido cargar esta finca para editarla.');
    } finally {
      setLoading(false);
    }
  }, [apiConfigured, fieldId, selectedWorkspaceId, status]);

  useEffect(() => { void load(true); }, [load]);

  async function saveDetails() {
    if (!fieldId || !selectedWorkspaceId || savingDetails) return;
    setSavingDetails(true);
    setError(null);
    setNotice(null);
    try {
      const payload = await apiFetch<FieldPayload>(`/api/v1/fields/${encodeURIComponent(fieldId)}`, {
        method: 'PATCH',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify({
          name: name.trim(),
          place_id: placeId || null,
          tree_count: trees ? Number(trees) : null,
          variety: variety.trim() || null,
          water_regime: waterRegime,
        }),
      });
      setField(payload.field);
      setNotice('Datos de la finca guardados.');
    } catch (saveError) {
      console.error('Unable to update finca', saveError);
      setError('No se han podido guardar los datos de la finca.');
    } finally {
      setSavingDetails(false);
    }
  }

  async function saveGeometry() {
    if (!fieldId || !selectedWorkspaceId || !selection || savingGeometry) return;
    setSavingGeometry(true);
    setError(null);
    setNotice(null);
    try {
      const path = selection.source === 'catastro'
        ? `/api/v1/fields/${encodeURIComponent(fieldId)}/land-references/catastro`
        : `/api/v1/fields/${encodeURIComponent(fieldId)}/land-references/sigpac`;
      const body = selection.source === 'catastro'
        ? { reference: selection.reference, set_as_geometry: selection.setAsGeometry }
        : { feature_id: selection.reference, set_as_geometry: selection.setAsGeometry };
      await apiFetch(path, {
        method: 'POST',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify(body),
      });
      setSelection(null);
      await load(false);
      setSelectorVersion((value) => value + 1);
      setNotice(selection.setAsGeometry
        ? 'Límite vinculado y guardado como geometría principal de la finca.'
        : 'Referencia vinculada. La geometría principal anterior se mantiene.');
    } catch (saveError) {
      console.error('Unable to persist finca geometry', saveError);
      setError('No se ha podido vincular ese límite. La geometría guardada anteriormente no se ha modificado.');
    } finally {
      setSavingGeometry(false);
    }
  }

  if (!fieldId) return <section className="card"><h1>Finca no indicada</h1><p>Abre la edición desde una finca concreta de Mi Campo.</p><Link href="/mi-campo" className="secondary-action action-link">Volver a Mi Campo</Link></section>;
  if (!apiConfigured) return <section className="card"><h1>Edición no disponible</h1><p>Esta instalación no está conectada a la API privada de Mi Campo.</p></section>;
  if (status === 'loading' || loading) return <section className="card"><p>Cargando finca…</p></section>;
  if (status !== 'authenticated' || !selectedWorkspaceId) return <section className="card"><h1>Inicia sesión</h1><p>Necesitas una sesión activa para editar una finca.</p></section>;
  if (!field || !mapContext) return <section className="card"><h1>No se ha podido abrir la finca</h1><p>{error ?? 'La finca no está disponible.'}</p></section>;

  return <>
    <section className="card new-farm-summary">
      <div><small>MI CAMPO · EDITAR FINCA</small><strong>{field.name}</strong><span>{field.geometry_status === 'verified' ? 'Límites verificados' : 'Sin geometría confirmada'}</span></div>
      <Link href={`/mi-campo/fincas/ver?id=${encodeURIComponent(field.id)}`} className="secondary-action action-link">Volver a la finca</Link>
    </section>

    <section className="card record-panel">
      <div className="record-panel-head"><div><span className="eyebrow dark">DATOS DE LA FINCA</span><h2>Lo que reconoces y gestionas</h2><p>Catastro y SIGPAC no sustituyen estos datos: solo aportan referencias y límites.</p></div></div>
      <div className="record-fields">
        <label className="record-field wide"><span>Nombre de la finca *</span><input className="record-control" value={name} onChange={(event) => setName(event.target.value)} /></label>
        <label className="record-field"><span>Nº de olivas</span><input className="record-control" type="number" min="1" value={trees} onChange={(event) => setTrees(event.target.value)} /></label>
        <label className="record-field"><span>Pueblo / localidad</span><select className="record-control" value={placeId} onChange={(event) => setPlaceId(event.target.value)}><option value="">Sin localidad vinculada</option>{places.map((place) => <option key={place.id} value={place.id}>{place.name}{place.name !== place.municipality_name ? ` · ${place.municipality_name}` : ''}</option>)}</select></label>
        <label className="record-field"><span>Variedad</span><input className="record-control" value={variety} onChange={(event) => setVariety(event.target.value)} /></label>
        <label className="record-field"><span>Régimen</span><select className="record-control" value={waterRegime ?? 'secano'} onChange={(event) => setWaterRegime(event.target.value as FieldRecord['water_regime'])}><option value="secano">Secano</option><option value="regadio">Regadío</option><option value="mixto">Mixto</option></select></label>
      </div>
      <div className="record-actions"><button type="button" className="primary" onClick={() => void saveDetails()} disabled={savingDetails || !name.trim()}>{savingDetails ? 'Guardando…' : 'Guardar datos de la finca'}</button></div>
    </section>

    <FarmGisSelector key={selectorVersion} workspaceId={selectedWorkspaceId} fieldName={field.name} initialContext={mapContext} onSelectionChange={setSelection} />

    <section className="card locate-result">
      <h3>{mapContext.field.geometry ? 'Geometría principal guardada' : 'Finca sin geometría'}</h3>
      <p>{mapContext.field.geometry
        ? `${mapContext.field.geometry_source === 'catastro' ? 'Catastro' : mapContext.field.geometry_source === 'sigpac' ? 'SIGPAC' : 'Límite propio'}${mapContext.field.calculated_area_ha == null ? '' : ` · ${mapContext.field.calculated_area_ha.toLocaleString('es-ES', { maximumFractionDigits: 3 })} ha`}. Al volver a editar, este límite se recupera desde la base de datos.`
        : 'Puedes seguir usando la finca sin límites. Cuando selecciones uno real, quedará persistido y se recuperará en futuras ediciones.'}</p>
      <p className="subtle">Referencias vinculadas: {mapContext.references.length}</p>
      <button type="button" className="primary" onClick={() => void saveGeometry()} disabled={!selection || savingGeometry}>{savingGeometry ? 'Guardando límite…' : 'Guardar límite seleccionado'}</button>
    </section>

    {notice ? <p className="card" role="status">{notice}</p> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}
  </>;
}
