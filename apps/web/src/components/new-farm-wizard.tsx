'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { saveLocalField } from '@/lib/local-prototype-store';
import { apiFetch } from '@/lib/api-client';
import { ArrowIcon, MapPinIcon, SproutIcon } from '@/components/icons';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';

type LocateMode = 'mapa' | 'catastro' | 'sigpac' | 'dibujar' | null;
type WaterRegime = 'Secano' | 'Regadío' | 'Mixto';

type TerritoryPlace = {
  id: string;
  name: string;
  slug: string;
  kind: string;
  municipality_id: string;
  municipality_name: string;
  province_name: string;
};

type TerritoryPayload = { places: TerritoryPlace[] };
type CreatedFieldPayload = {
  replayed: boolean;
  field: { id: string; name: string };
  territory: {
    place_id: string;
    place_name: string;
    municipality_id: string;
    municipality_name: string;
  } | null;
};

function apiWaterRegime(value: WaterRegime) {
  if (value === 'Regadío') return 'regadio' as const;
  if (value === 'Mixto') return 'mixto' as const;
  return 'secano' as const;
}

export function NewFarmWizard() {
  const { status, apiConfigured, selectedWorkspaceId } = useAuth();
  const [step, setStep] = useState(1);
  const [name, setName] = useState('');
  const [trees, setTrees] = useState('');
  const [placeId, setPlaceId] = useState('');
  const [fallbackMunicipality, setFallbackMunicipality] = useState('Huelma');
  const [places, setPlaces] = useState<TerritoryPlace[]>([]);
  const [territoryLoading, setTerritoryLoading] = useState(false);
  const [variety, setVariety] = useState('Picual');
  const [waterRegime, setWaterRegime] = useState<WaterRegime>('Secano');
  const [mode, setMode] = useState<LocateMode>(null);
  const [linked, setLinked] = useState(false);
  const [savedId, setSavedId] = useState<string | null>(null);
  const [savedRemotely, setSavedRemotely] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!apiConfigured) return;
    setTerritoryLoading(true);
    void apiFetch<TerritoryPayload>('/api/v1/public/territory/places')
      .then((payload) => {
        if (cancelled) return;
        setPlaces(payload.places);
        const huelma = payload.places.find((place) => place.slug === 'huelma');
        setPlaceId((current) => current || huelma?.id || payload.places[0]?.id || '');
      })
      .catch((error) => {
        console.error('Unable to load Mágina territory catalog', error);
        if (!cancelled) setPlaces([]);
      })
      .finally(() => {
        if (!cancelled) setTerritoryLoading(false);
      });
    return () => { cancelled = true; };
  }, [apiConfigured]);

  const selectedPlace = useMemo(() => places.find((place) => place.id === placeId) ?? null, [placeId, places]);
  const municipalityLabel = selectedPlace?.name || fallbackMunicipality.trim() || 'Sin indicar';

  function continueBasics(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaveError(null);
    if (apiConfigured && status !== 'authenticated') {
      setSaveError('Inicia sesión para guardar esta finca en Mi Campo.');
      return;
    }
    setStep(2);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function finish() {
    if (saving) return;
    setSaving(true);
    setSaveError(null);

    try {
      if (apiConfigured) {
        if (status !== 'authenticated' || !selectedWorkspaceId) {
          setSaveError('Necesitas una sesión activa para guardar la finca.');
          return;
        }

        const operationId = crypto.randomUUID();
        const entityId = crypto.randomUUID();
        const created = await apiFetch<CreatedFieldPayload>('/api/v1/fields', {
          method: 'POST',
          workspaceId: selectedWorkspaceId,
          body: JSON.stringify({
            client_operation_id: operationId,
            entity_id: entityId,
            name: name.trim() || 'Nueva finca',
            ...(placeId ? { place_id: placeId } : { municipality: fallbackMunicipality.trim() || undefined, province: 'Jaén' }),
            tree_count: trees ? Number(trees) : undefined,
            variety: variety || undefined,
            water_regime: apiWaterRegime(waterRegime),
          }),
        });
        setSavedId(created.field.id);
        setSavedRemotely(true);
      } else {
        const id = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `field-${Date.now()}`;
        saveLocalField({
          id,
          name: name.trim() || 'Nueva finca',
          municipality: municipalityLabel,
          oliveTrees: trees ? Number(trees) : undefined,
          variety,
          waterRegime,
          createdAt: new Date().toISOString(),
        });
        setSavedId(id);
        setSavedRemotely(false);
      }

      setStep(3);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (error) {
      console.error('Unable to save finca', error);
      setSaveError('No se ha podido guardar la finca. Revisa la conexión y vuelve a intentarlo.');
    } finally {
      setSaving(false);
    }
  }

  if (step === 3) {
    const fieldHref = savedRemotely ? '/mi-campo' : savedId ? `/mi-campo/fincas/local?id=${encodeURIComponent(savedId)}` : '/mi-campo';
    const registerHref = savedId ? `/mi-campo/registrar?fieldId=${encodeURIComponent(savedId)}` : '/mi-campo/registrar';
    return <section className="card new-farm-success">
      <div className="success-mark"><SproutIcon /></div>
      <span className="eyebrow dark">{savedRemotely ? 'FINCA GUARDADA EN MI CAMPO' : 'GUARDADA EN ESTE DISPOSITIVO'}</span>
      <h1>{name || 'Nueva finca'}</h1>
      <p>{trees || '—'} olivas · {municipalityLabel} · La finca ya tiene identidad propia para guardar trabajos, documentos y territorio.</p>
      <div className="success-effects">
        <span>✓ Finca creada</span>
        <span>{selectedPlace ? `✓ Localidad: ${selectedPlace.name} · ${selectedPlace.municipality_name}` : '○ Municipio sin vínculo oficial'}</span>
        <span>{linked && !savedRemotely ? '✓ Localización demo asociada' : '○ Geometría oficial pendiente, opcional'}</span>
      </div>
      <div className="record-actions"><Link href={fieldHref} className="secondary-action action-link">Volver a Mi Campo</Link><Link href={registerHref} className="primary action-link">Registrar trabajo <ArrowIcon /></Link></div>
    </section>;
  }

  if (step === 2) {
    return <div className="new-farm-location-flow">
      <section className="card new-farm-summary"><span className="new-farm-tree"><SproutIcon /></span><div><small>NUEVA FINCA</small><strong>{name}</strong><span>{trees} olivas · {municipalityLabel}</span></div><button onClick={() => setStep(1)}>Editar</button></section>
      <section className="card locate-panel"><span className="eyebrow dark">PASO 2 · UBICACIÓN</span><h2>¿Quieres localizarla ahora?</h2><p>Es recomendable, pero no obligatorio. La finca existe por sí misma; Catastro y SIGPAC son referencias que podrás vincular después.</p><div className="locate-grid">{[
        ['mapa','📍','Buscar en mapa','Toca la finca sobre el mapa'],['catastro','▦','Catastro','Buscar por referencia catastral'],['sigpac','▱','SIGPAC','Recintos agrícolas'],['dibujar','✎','Dibujar','Marca tú mismo el contorno'],
      ].map(([key,symbol,label,text]) => <button type="button" key={key} className={mode === key ? 'locate-choice active' : 'locate-choice'} onClick={() => setMode(key as LocateMode)}><span>{symbol}</span><strong>{label}</strong><small>{text}</small></button>)}</div></section>
      {mode && <section className="card locate-result">
        {mode === 'catastro' && <><h3>Referencia catastral</h3><div className="locate-search"><input placeholder="14 caracteres · parcela rústica" /><button type="button">Buscar</button></div><p>La búsqueda oficial ya existe en la API V20. En la siguiente iteración conectaremos esta caja al resultado cartográfico antes de confirmar.</p></>}
        {mode === 'sigpac' && <><h3>Buscar en SIGPAC</h3><div className="triple-locate"><input placeholder="Polígono"/><input placeholder="Parcela"/><input placeholder="Recinto"/></div><p>La API V20 ya puede recuperar recintos oficiales y asociar varios a una misma finca.</p></>}
        {mode === 'mapa' && <><h3>Selecciona sobre el mapa</h3><div className="mock-field-map"><span><MapPinIcon/> {name}</span></div><p>El MapPlatform real ya está disponible en Mi Campo → Mapa. Aquí conectaremos su selector de geometría.</p></>}
        {mode === 'dibujar' && <><h3>Dibuja el contorno</h3><div className="mock-field-map draw"><span>✎ Toca puntos alrededor de la finca</span></div><p>Será la opción correcta cuando la finca no coincida exactamente con una parcela administrativa.</p></>}
        {!apiConfigured ? <label className="link-confirm"><input type="checkbox" checked={linked} onChange={(event) => setLinked(event.target.checked)} /> Usar esta localización para la demo</label> : null}
      </section>}
      {saveError ? <p className="form-error" role="alert">{saveError}</p> : null}
      <div className="new-farm-actions"><button className="secondary-action" type="button" onClick={() => void finish()} disabled={saving}>{saving ? 'Guardando…' : 'Guardar sin localizar'}</button><button className="primary" type="button" onClick={() => void finish()} disabled={!mode || saving}>{saving ? 'Guardando…' : 'Guardar finca →'}</button></div>
    </div>;
  }

  return <form className="new-farm-basics" onSubmit={continueBasics}>
    {apiConfigured && status === 'anonymous' ? <section className="card new-farm-summary"><div><strong>Inicia sesión para crear tu finca</strong><span>La Guía sigue siendo pública; Mi Campo guarda datos privados asociados a tu cuenta.</span><GoogleSignInButton /></div></section> : null}
    <section className="card record-panel"><div className="record-panel-head"><span className="record-type-symbol"><SproutIcon /></span><div><span className="eyebrow dark">PASO 1 · LO BÁSICO</span><h2>¿Cómo llamáis a esta finca?</h2><p>Usa el nombre de siempre. No hace falta conocer Catastro.</p></div></div><div className="record-fields">
      <label className="record-field wide"><span>Nombre de la finca *</span><input className="record-control" value={name} onChange={(e) => setName(e.target.value)} required placeholder="Ej. Las Cenillas" /></label>
      <label className="record-field"><span>Nº de olivas *</span><input className="record-control" value={trees} onChange={(e) => setTrees(e.target.value)} required inputMode="numeric" type="number" min="1" placeholder="23" /></label>
      {apiConfigured ? <label className="record-field"><span>Pueblo / localidad</span><select className="record-control" value={placeId} onChange={(event) => setPlaceId(event.target.value)} disabled={territoryLoading}><option value="">{territoryLoading ? 'Cargando pueblos…' : 'Seleccionar'}</option>{places.map((place) => <option value={place.id} key={place.id}>{place.name}{place.name !== place.municipality_name ? ` · ${place.municipality_name}` : ''}</option>)}</select></label> : <label className="record-field"><span>Pueblo / localidad</span><input className="record-control" value={fallbackMunicipality} onChange={(e) => setFallbackMunicipality(e.target.value)} /></label>}
    </div></section>
    <details className="card record-details"><summary>Datos opcionales <span>Más adelante</span></summary><div className="record-fields detail-fields">
      <label className="record-field"><span>Variedad</span><select className="record-control" value={variety} onChange={(e) => setVariety(e.target.value)}><option>Picual</option><option>Hojiblanca</option><option>Arbequina</option><option>Otra</option></select></label>
      <label className="record-field"><span>Régimen</span><select className="record-control" value={waterRegime} onChange={(e) => setWaterRegime(e.target.value as WaterRegime)}><option>Secano</option><option>Regadío</option><option>Mixto</option></select></label>
      <label className="record-field wide"><span>Notas</span><textarea className="record-control" rows={3} placeholder="Cómo llegar, nombre antiguo, referencias familiares…" /></label>
    </div></details>
    {saveError ? <p className="form-error" role="alert">{saveError}</p> : null}
    <div className="record-save-bar"><small>Primero creamos tu finca. La localización oficial se confirma después.</small><button className="primary" type="submit">Continuar: localizar finca →</button></div>
  </form>;
}
