'use client';

import { FormEvent, useState } from 'react';
import Link from 'next/link';
import { saveLocalField } from '@/lib/local-prototype-store';
import { ArrowIcon, MapPinIcon, SproutIcon } from '@/components/icons';

type LocateMode = 'mapa' | 'catastro' | 'sigpac' | 'dibujar' | null;

type WaterRegime = 'Secano' | 'Regadío' | 'Mixto';

export function NewFarmWizard() {
  const [step, setStep] = useState(1);
  const [name, setName] = useState('');
  const [trees, setTrees] = useState('');
  const [municipality, setMunicipality] = useState('Huelma');
  const [variety, setVariety] = useState('Picual');
  const [waterRegime, setWaterRegime] = useState<WaterRegime>('Secano');
  const [mode, setMode] = useState<LocateMode>(null);
  const [linked, setLinked] = useState(false);

  function continueBasics(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStep(2);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function finish() {
    const id = typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `field-${Date.now()}`;
    saveLocalField({
      id,
      name: name.trim() || 'Nueva finca',
      municipality: municipality.trim() || undefined,
      oliveTrees: trees ? Number(trees) : undefined,
      variety,
      waterRegime,
      createdAt: new Date().toISOString(),
    });
    setStep(3);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  if (step === 3) {
    return (
      <section className="card new-farm-success">
        <div className="success-mark"><SproutIcon /></div>
        <span className="eyebrow dark">GUARDADA EN ESTE DISPOSITIVO</span>
        <h1>{name || 'Nueva finca'}</h1>
        <p>{trees || '—'} olivas · La finca ya aparece en Mi Campo aunque todavía no tenga Catastro o SIGPAC vinculado.</p>
        <div className="success-effects">
          <span>✓ Finca guardada localmente</span>
          <span>✓ Lista para registrar trabajos</span>
          <span>{linked ? '✓ Localización de demo asociada' : '○ Terreno oficial pendiente, opcional'}</span>
        </div>
        <div className="record-actions">
          <Link href="/mi-campo" className="secondary-action action-link">Volver a Mi Campo</Link>
          <Link href="/mi-campo/registrar" className="primary action-link">Registrar trabajo <ArrowIcon /></Link>
        </div>
      </section>
    );
  }

  if (step === 2) {
    return (
      <div className="new-farm-location-flow">
        <section className="card new-farm-summary">
          <span className="new-farm-tree"><SproutIcon /></span>
          <div><small>NUEVA FINCA</small><strong>{name}</strong><span>{trees} olivas · {municipality}</span></div>
          <button onClick={() => setStep(1)}>Editar</button>
        </section>

        <section className="card locate-panel">
          <span className="eyebrow dark">PASO 2 · UBICACIÓN</span>
          <h2>¿Quieres localizarla ahora?</h2>
          <p>Es recomendable, pero no obligatorio. Puedes hacerlo más tarde desde Datos → Terreno.</p>
          <div className="locate-grid">
            {[
              ['mapa','📍','Buscar en mapa','Toca la finca sobre el mapa'],
              ['catastro','▦','Catastro','Buscar por referencia catastral'],
              ['sigpac','▱','SIGPAC','Polígono y parcela agrícola'],
              ['dibujar','✎','Dibujar','Marca tú mismo el contorno'],
            ].map(([key,symbol,label,text]) => (
              <button type="button" key={key} className={mode === key ? 'locate-choice active' : 'locate-choice'} onClick={() => setMode(key as LocateMode)}>
                <span>{symbol}</span><strong>{label}</strong><small>{text}</small>
              </button>
            ))}
          </div>
        </section>

        {mode && (
          <section className="card locate-result">
            {mode === 'catastro' && <><h3>Referencia catastral</h3><div className="locate-search"><input placeholder="Ej. 23037A012001230000AB" /><button type="button">Buscar</button></div><p>La integración real consultará Catastro y recuperará la geometría cuando exista.</p></>}
            {mode === 'sigpac' && <><h3>Buscar en SIGPAC</h3><div className="triple-locate"><input placeholder="Polígono"/><input placeholder="Parcela"/><input placeholder="Recinto"/></div><p>Podremos asociar uno o varios recintos a la misma finca del agricultor.</p></>}
            {mode === 'mapa' && <><h3>Selecciona sobre el mapa</h3><div className="mock-field-map"><span><MapPinIcon/> {name}</span></div><p>Concepto visual. Más adelante se conectará a mapa real, Catastro/SIGPAC y PostGIS.</p></>}
            {mode === 'dibujar' && <><h3>Dibuja el contorno</h3><div className="mock-field-map draw"><span>✎ Toca puntos alrededor de la finca</span></div><p>Útil cuando la finca real no coincide exactamente con una parcela administrativa.</p></>}
            <label className="link-confirm"><input type="checkbox" checked={linked} onChange={(event) => setLinked(event.target.checked)} /> Usar esta localización para la demo</label>
          </section>
        )}

        <div className="new-farm-actions">
          <button className="secondary-action" type="button" onClick={finish}>Ahora no</button>
          <button className="primary" type="button" onClick={finish} disabled={!mode}>Guardar finca →</button>
        </div>
      </div>
    );
  }

  return (
    <form className="new-farm-basics" onSubmit={continueBasics}>
      <section className="card record-panel">
        <div className="record-panel-head">
          <span className="record-type-symbol"><SproutIcon /></span>
          <div><span className="eyebrow dark">PASO 1 · LO BÁSICO</span><h2>¿Cómo llamáis a esta finca?</h2><p>Usa el nombre de siempre. No hace falta conocer Catastro.</p></div>
        </div>
        <div className="record-fields">
          <label className="record-field wide"><span>Nombre de la finca *</span><input className="record-control" value={name} onChange={(e) => setName(e.target.value)} required placeholder="Ej. Las Cenillas" /></label>
          <label className="record-field"><span>Nº de olivas *</span><input className="record-control" value={trees} onChange={(e) => setTrees(e.target.value)} required inputMode="numeric" type="number" min="1" placeholder="23" /></label>
          <label className="record-field"><span>Municipio</span><input className="record-control" value={municipality} onChange={(e) => setMunicipality(e.target.value)} /></label>
        </div>
      </section>

      <details className="card record-details">
        <summary>Datos opcionales <span>Más adelante</span></summary>
        <div className="record-fields detail-fields">
          <label className="record-field"><span>Variedad</span><select className="record-control" value={variety} onChange={(e) => setVariety(e.target.value)}><option>Picual</option><option>Hojiblanca</option><option>Arbequina</option><option>Otra</option></select></label>
          <label className="record-field"><span>Régimen</span><select className="record-control" value={waterRegime} onChange={(e) => setWaterRegime(e.target.value as WaterRegime)}><option>Secano</option><option>Regadío</option><option>Mixto</option></select></label>
          <label className="record-field wide"><span>Notas</span><textarea className="record-control" rows={3} placeholder="Cómo llegar, nombre antiguo, referencias familiares…" /></label>
        </div>
      </details>

      <div className="record-save-bar"><small>Catastro y SIGPAC vendrán después; primero creamos tu finca.</small><button className="primary" type="submit">Continuar: localizar finca →</button></div>
    </form>
  );
}
