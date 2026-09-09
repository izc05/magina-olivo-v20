'use client';

import { FormEvent, useState } from 'react';
import Link from 'next/link';
import type { RecordField, RecordType } from '@/lib/record-types';
import { lasCenillas } from '@/lib/demo-data';
import { ArrowIcon, MapPinIcon } from '@/components/icons';

function Field({ field }: { field: RecordField }) {
  const common = {
    id: field.name,
    name: field.name,
    required: field.required,
    className: 'record-control',
  };

  return (
    <label className={field.kind === 'textarea' ? 'record-field wide' : 'record-field'} htmlFor={field.name}>
      <span>{field.label}{field.required ? ' *' : ''}</span>
      <div className="record-input-wrap">
        {field.kind === 'select' ? (
          <select {...common} defaultValue="">
            <option value="" disabled>Seleccionar</option>
            {field.options?.map((option) => <option key={option}>{option}</option>)}
          </select>
        ) : field.kind === 'textarea' ? (
          <textarea {...common} rows={4} placeholder={field.placeholder} />
        ) : (
          <input
            {...common}
            type={field.kind}
            placeholder={field.placeholder}
            inputMode={field.inputMode}
            step={field.kind === 'number' ? 'any' : undefined}
          />
        )}
        {field.suffix && <b className="record-suffix">{field.suffix}</b>}
      </div>
    </label>
  );
}

export function QuickRecordForm({ type }: { type: RecordType }) {
  const [saved, setSaved] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaved(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  if (saved) {
    return (
      <section className="record-success card">
        <div className="success-mark">✓</div>
        <span className="eyebrow dark">REGISTRO GUARDADO · DEMO</span>
        <h1>{type.shortLabel} añadido a {lasCenillas.name}</h1>
        <p>En la aplicación real este único registro actualizará automáticamente la historia de la finca y los apartados relacionados.</p>
        <div className="success-effects">
          <span>✓ Historia de la finca</span>
          <span>✓ Campaña {lasCenillas.campaign}</span>
          {type.slug !== 'observacion' && <span>✓ Costes, si has indicado importe</span>}
          {type.followUp && <span>✓ Calendario, si has programado seguimiento</span>}
        </div>
        <div className="record-actions">
          <button type="button" className="secondary-action" onClick={() => setSaved(false)}>Registrar otro</button>
          <Link className="primary action-link" href="/mi-campo/fincas/las-cenillas">Ver Las Cenillas <ArrowIcon /></Link>
        </div>
      </section>
    );
  }

  return (
    <form className="quick-record-form" onSubmit={handleSubmit}>
      <section className="record-farm-context card">
        <span className="record-farm-symbol">🌳</span>
        <div>
          <small>REGISTRANDO EN</small>
          <strong>{lasCenillas.name}</strong>
          <span><MapPinIcon /> {lasCenillas.municipality} · {lasCenillas.oliveTrees} olivas</span>
        </div>
        <Link href="/mi-campo">Cambiar</Link>
      </section>

      <section className="card record-panel">
        <div className="record-panel-head">
          <span className="record-type-symbol">{type.symbol}</span>
          <div>
            <span className="eyebrow dark">REGISTRO RÁPIDO</span>
            <h2>{type.question}</h2>
            <p>Solo lo imprescindible. El resto es opcional.</p>
          </div>
        </div>
        <div className="record-fields">
          {type.essential.map((field) => <Field key={field.name} field={field} />)}
        </div>
      </section>

      {type.details && (
        <details className="card record-details">
          <summary>Más detalles <span>Opcional</span></summary>
          <div className="record-fields detail-fields">
            {type.details.map((field) => <Field key={field.name} field={field} />)}
            <label className="record-field wide photo-field">
              <span>Foto o documento</span>
              <input className="record-control file-control" type="file" accept="image/*,.pdf" />
              <small>Foto del trabajo, factura, ticket o documento relacionado.</small>
            </label>
          </div>
        </details>
      )}

      {type.followUp && (
        <section className="card record-follow-up">
          <div>
            <span className="eyebrow dark">DESPUÉS</span>
            <h3>¿Quieres dejarlo programado?</h3>
            <p>Si indicas una fecha aparecerá automáticamente en el calendario.</p>
          </div>
          <div className="record-fields follow-up-fields">
            {type.followUp.map((field) => <Field key={field.name} field={field} />)}
          </div>
        </section>
      )}

      <section className="record-save-bar">
        <small>Los datos de esta pantalla son una demostración visual.</small>
        <button className="primary" type="submit">Guardar {type.shortLabel.toLowerCase()} →</button>
      </section>
    </form>
  );
}
