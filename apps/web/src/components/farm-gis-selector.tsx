'use client';

import { useMemo, useState } from 'react';
import { apiFetch } from '@/lib/api-client';
import { FarmMap, type FarmMapData, type MapGeometry, type MapLandReference } from '@/components/farm-map';

type GisSource = 'catastro' | 'sigpac';

type CatastroItem = {
  id: string;
  nationalCadastralReference: string;
  label: string | null;
  areaM2: number | null;
  geometry: MapGeometry;
};

type SigpacItem = {
  id: string;
  poligono: number | null;
  parcela: number | null;
  recinto: number | null;
  surfaceM2: number | null;
  usoSigpac: string | null;
  geometry: MapGeometry;
};

export type GisSelection = {
  source: GisSource;
  reference: string;
  label: string;
  areaHa: number | null;
  geometry: MapGeometry;
  setAsGeometry: boolean;
};

export type FarmMapContext = {
  field: {
    id: string;
    name: string;
    geometry: MapGeometry | null;
    geometry_source: string | null;
    geometry_status: string;
    calculated_area_ha: number | null;
    centroid?: { type: 'Point'; coordinates: [number, number] } | null;
    representative_point?: { type: 'Point'; coordinates: [number, number] } | null;
  };
  references: MapLandReference[];
};

type Candidate = Omit<GisSelection, 'setAsGeometry'>;

type Props = {
  workspaceId: string;
  fieldName: string;
  initialContext?: FarmMapContext | null;
  onSelectionChange: (selection: GisSelection | null) => void;
};

function areaLabel(areaHa: number | null) {
  return areaHa == null ? 'superficie no informada' : `${areaHa.toLocaleString('es-ES', { maximumFractionDigits: 3 })} ha`;
}

function candidateFromCatastro(item: CatastroItem): Candidate {
  return {
    source: 'catastro',
    reference: item.nationalCadastralReference,
    label: item.label?.trim() || `Parcela ${item.nationalCadastralReference}`,
    areaHa: item.areaM2 == null ? null : item.areaM2 / 10_000,
    geometry: item.geometry,
  };
}

function candidateFromSigpac(item: SigpacItem): Candidate {
  const parcel = [
    item.poligono == null ? null : `Pol. ${item.poligono}`,
    item.parcela == null ? null : `Parc. ${item.parcela}`,
    item.recinto == null ? null : `Rec. ${item.recinto}`,
  ].filter(Boolean).join(' · ');
  return {
    source: 'sigpac',
    reference: item.id,
    label: parcel || `Recinto SIGPAC ${item.id}`,
    areaHa: item.surfaceM2 == null ? null : item.surfaceM2 / 10_000,
    geometry: item.geometry,
  };
}

export function FarmGisSelector({ workspaceId, fieldName, initialContext, onSelectionChange }: Props) {
  const [source, setSource] = useState<GisSource>('catastro');
  const [reference, setReference] = useState('');
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [selected, setSelected] = useState<Candidate | null>(null);
  const [setAsGeometry, setSetAsGeometry] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);

  const previewData = useMemo<FarmMapData>(() => ({
    id: initialContext?.field.id ?? 'new-field-preview',
    name: fieldName || initialContext?.field.name || 'Nueva finca',
    geometry: selected?.geometry ?? initialContext?.field.geometry ?? null,
    centroid: selected ? null : initialContext?.field.centroid,
    representative_point: selected ? null : initialContext?.field.representative_point,
    references: initialContext?.references ?? [],
  }), [fieldName, initialContext, selected]);

  function choose(candidate: Candidate | null) {
    setSelected(candidate);
    onSelectionChange(candidate ? { ...candidate, setAsGeometry } : null);
  }

  function changeCanonical(checked: boolean) {
    setSetAsGeometry(checked);
    if (selected) onSelectionChange({ ...selected, setAsGeometry: checked });
  }

  async function searchExact() {
    const value = reference.trim().toUpperCase();
    setError(null);
    setSearched(true);
    if (source === 'catastro' && !/^[A-Z0-9]{14}$/.test(value)) {
      setCandidates([]);
      choose(null);
      setError('La referencia catastral debe tener 14 caracteres.');
      return;
    }
    if (source === 'sigpac' && !/^\d{1,20}$/.test(value)) {
      setCandidates([]);
      choose(null);
      setError('El identificador SIGPAC debe ser numérico.');
      return;
    }

    setLoading(true);
    try {
      if (source === 'catastro') {
        const payload = await apiFetch<{ item: CatastroItem }>(`/api/v1/gis/catastro/parcels/${encodeURIComponent(value)}`, { workspaceId });
        const candidate = candidateFromCatastro(payload.item);
        setCandidates([candidate]);
        choose(candidate);
      } else {
        const payload = await apiFetch<{ item: SigpacItem }>(`/api/v1/gis/sigpac/recintos/${encodeURIComponent(value)}`, { workspaceId });
        const candidate = candidateFromSigpac(payload.item);
        setCandidates([candidate]);
        choose(candidate);
      }
    } catch (searchError) {
      console.error('Unable to search GIS reference', searchError);
      setCandidates([]);
      choose(null);
      setError(`No se ha podido consultar ${source === 'catastro' ? 'Catastro' : 'SIGPAC'}. La finca no se modificará.`);
    } finally {
      setLoading(false);
    }
  }

  async function searchNearMe() {
    setError(null);
    setSearched(true);
    if (!navigator.geolocation) {
      setError('Este navegador no permite obtener tu posición. Puedes buscar por referencia.');
      return;
    }

    setLoading(true);
    navigator.geolocation.getCurrentPosition(async (position) => {
      try {
        const longitude = position.coords.longitude;
        const latitude = position.coords.latitude;
        const delta = 0.012;
        const query = new URLSearchParams({
          minLon: String(longitude - delta),
          minLat: String(latitude - delta),
          maxLon: String(longitude + delta),
          maxLat: String(latitude + delta),
        });
        if (source === 'catastro') {
          const payload = await apiFetch<{ items: CatastroItem[] }>(`/api/v1/gis/catastro/parcels?${query}`, { workspaceId });
          setCandidates(payload.items.map(candidateFromCatastro));
        } else {
          const payload = await apiFetch<{ items: SigpacItem[] }>(`/api/v1/gis/sigpac/recintos?${query}`, { workspaceId });
          setCandidates(payload.items.map(candidateFromSigpac));
        }
        choose(null);
      } catch (searchError) {
        console.error('Unable to search nearby GIS features', searchError);
        setCandidates([]);
        choose(null);
        setError(`No se ha podido consultar ${source === 'catastro' ? 'Catastro' : 'SIGPAC'} cerca de tu posición.`);
      } finally {
        setLoading(false);
      }
    }, () => {
      setLoading(false);
      setError('No se ha podido usar tu posición. Puedes buscar por referencia sin compartir ubicación.');
    }, { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 });
  }

  const hasPersistedGeometry = Boolean(initialContext?.field.geometry);

  return <section className="card locate-panel" data-testid="gis-selector">
    <span className="eyebrow dark">LÍMITES DE LA FINCA</span>
    <h2>Localiza la finca con un límite real</h2>
    <p>Catastro y SIGPAC se usan solo para encontrar y verificar el límite. La entidad que guardarás y editarás seguirá siendo la <strong>Finca</strong>.</p>

    <div className="locate-grid" role="group" aria-label="Fuente para localizar la finca">
      <button type="button" className={source === 'catastro' ? 'locate-choice active' : 'locate-choice'} onClick={() => { setSource('catastro'); setCandidates([]); choose(null); setError(null); }}>
        <span>▦</span><strong>Catastro</strong><small>Capa técnica</small>
      </button>
      <button type="button" className={source === 'sigpac' ? 'locate-choice active' : 'locate-choice'} onClick={() => { setSource('sigpac'); setCandidates([]); choose(null); setError(null); }}>
        <span>▱</span><strong>SIGPAC</strong><small>Capa técnica</small>
      </button>
    </div>

    <div className="locate-search">
      <label className="record-field wide">
        <span>{source === 'catastro' ? 'Referencia catastral' : 'ID del recinto SIGPAC'}</span>
        <input
          className="record-control"
          aria-label={source === 'catastro' ? 'Referencia catastral' : 'ID del recinto SIGPAC'}
          value={reference}
          onChange={(event) => setReference(event.target.value)}
          placeholder={source === 'catastro' ? '14 caracteres' : 'Identificador numérico'}
          disabled={loading}
        />
      </label>
      <button type="button" className="secondary-action" onClick={() => void searchExact()} disabled={loading || !reference.trim()}>{loading ? 'Consultando…' : 'Buscar referencia'}</button>
      <button type="button" className="secondary-action" onClick={() => void searchNearMe()} disabled={loading}>{loading ? 'Consultando…' : 'Buscar cerca de mí'}</button>
    </div>

    {error ? <p className="form-error" role="alert">{error}</p> : null}
    {searched && !loading && !error && candidates.length === 0 ? <p className="subtle" role="status">No se han encontrado límites en esta consulta. La finca puede guardarse sin geometría y completarse después.</p> : null}

    {candidates.length ? <div className="card feed today-list" aria-label="Límites encontrados">
      {candidates.map((candidate) => <button
        type="button"
        key={`${candidate.source}-${candidate.reference}`}
        className="feed-row"
        aria-pressed={selected?.source === candidate.source && selected.reference === candidate.reference}
        onClick={() => choose(candidate)}
      >
        <span className="feed-copy"><strong>{candidate.label}</strong><small>{candidate.source === 'catastro' ? 'Catastro' : 'SIGPAC'} · {candidate.reference} · {areaLabel(candidate.areaHa)}</small></span>
        <span>{selected?.reference === candidate.reference ? '✓ Seleccionado' : 'Seleccionar'}</span>
      </button>)}
    </div> : null}

    <div className="map-shell">
      <FarmMap data={previewData} />
    </div>

    {selected ? <div className="locate-result" data-testid="gis-selection">
      <h3>Límite seleccionado</h3>
      <p><strong>{selected.label}</strong><br />{selected.source === 'catastro' ? 'Catastro' : 'SIGPAC'} · {selected.reference} · {areaLabel(selected.areaHa)}</p>
      <label className="link-confirm"><input type="checkbox" checked={setAsGeometry} onChange={(event) => changeCanonical(event.target.checked)} /> Usar este límite como geometría principal de la finca</label>
    </div> : hasPersistedGeometry ? <p className="subtle" role="status">Esta finca ya tiene límites guardados. Puedes seleccionar otro para sustituir su geometría principal.</p> : <p className="subtle" role="status">Esta finca todavía no tiene límites guardados.</p>}
  </section>;
}
