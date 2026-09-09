'use client';

import { FormEvent, useState } from 'react';
import Link from 'next/link';
import type { RecordField, RecordType } from '@/lib/record-types';
import { activityLabels, recordSlugToActivityType } from '@/lib/domain';
import { saveLocalActivity } from '@/lib/local-prototype-store';
import { useFieldContext } from '@/lib/use-field-context';
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
          <input {...common} type={field.kind} placeholder={field.placeholder} inputMode={field.inputMode} step={field.kind === 'number' ? 'any' : undefined} />
        )}
        {field.suffix && <b className="record-suffix">{field.suffix}</b>}
      </div>
    </label>
  );
}

function buildSummary(data: Record<string, string>, fallback: string) {
  const parts = [data.reason, data.product, data.task, data.machine, data.concept, data.type].filter((value): value is string => Boolean(value));
  if (data.quantity) parts.push(`${data.quantity} kg`);
  if (parts.length) return parts.join(' · ');
  if (data.notes) return data.notes.slice(0, 90);
  return fallback;
}

export function QuickRecordForm({ type }: { type: RecordType }) {
  const [saved, setSaved] = useState(false);
  const { context, ready } = useFieldContext();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ready) return;
    const formData = new FormData(event.currentTarget);
    const data: Record<string, string> = {};
    for (const [key, value] of formData.entries()) {
      if (typeof value === 'string' && value.trim()) data[key] = value.trim();
    }

    const activityType = recordSlugToActivityType[type.slug as keyof typeof recordSlugToActivityType];
    if (!activityType) return;

    const costRaw = data.cost ?? data.amount;
    const cost = costRaw ? Number(costRaw.replace(',', '.')) : undefined;
    const followUpOn = data.nextDate ?? data.reviewDate;
    const followUpTime = data.nextTime;
    const id = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `local-${Date.now()}`;

    saveLocalActivity({
      id,
      fieldId: context.id,
      campaign: context.campaign,
      type: activityType,
      occurredOn: data.date ?? new Date().toISOString().slice(0, 10),
      title: activityLabels[activityType],
      summary: buildSummary(data, `Registro de ${type.shortLabel.toLowerCase()}`),
      costEur: cost !== undefined && Number.isFinite(cost) ? cost : undefined,
      followUpOn,
      followUpTime,
      data,
      source: 'prototype-local',
      createdAt: new Date().toISOString(),
    });

    setSaved(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  const historyHref = context.local
    ? `/mi-campo/fincas/local/modulo?fieldId=${encodeURIComponent(context.id)}&view=historia`
    : '/mi-campo/fincas/las-cenillas/historia';

  if (saved) {
    return (
      <section className="record-success card">
        <div className="success-mark">✓</div>
        <span className="eyebrow dark">GUARDADO EN ESTE DISPOSITIVO</span>
        <h1>{type.shortLabel} añadido a {context.name}</h1>
        <p>El registro se ha guardado con la finca seleccionada. Historia, Costes y Calendario lo reutilizan cuando corresponde.</p>
        <div className="success-effects">
          <span>✓ Historia de {context.name}</span>
          <span>✓ Campaña {context.campaign}</span>
          {type.slug !== 'observacion' && <span>✓ Costes, si has indicado importe</span>}
          {type.followUp && <span>✓ Calendario, si has programado seguimiento</span>}
        </div>
        <div className="record-actions">
          <button type="button" className="secondary-action" onClick={() => setSaved(false)}>Registrar otro</button>
          <Link className="primary action-link" href={historyHref}>Ver Historia <ArrowIcon /></Link>
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
          <strong>{context.name}</strong>
          <span><MapPinIcon /> {context.municipality}{context.oliveTrees ? ` · ${context.oliveTrees} olivas` : ''}</span>
        </div>
        <Link href="/mi-campo">Cambiar</Link>
      </section>

      <section className="card record-panel">
        <div className="record-panel-head">
          <span className="record-type-symbol">{type.symbol}</span>
          <div><span className="eyebrow dark">REGISTRO RÁPIDO</span><h2>{type.question}</h2><p>Solo lo imprescindible. El resto es opcional.</p></div>
        </div>
        <div className="record-fields">{type.essential.map((field) => <Field key={field.name} field={field} />)}</div>
      </section>

      {type.details && <details className="card record-details"><summary>Más detalles <span>Opcional</span></summary><div className="record-fields detail-fields">{type.details.map((field) => <Field key={field.name} field={field} />)}<label className="record-field wide photo-field"><span>Foto o documento</span><input className="record-control file-control" type="file" accept="image/*,.pdf" /><small>Foto del trabajo, factura, ticket o documento relacionado. En esta fase aún no se guarda el archivo, solo el resto del registro.</small></label></div></details>}

      {type.followUp && <section className="card record-follow-up"><div><span className="eyebrow dark">DESPUÉS</span><h3>¿Quieres dejarlo programado?</h3><p>Si indicas una fecha aparecerá automáticamente en el calendario local de esta finca.</p></div><div className="record-fields follow-up-fields">{type.followUp.map((field) => <Field key={field.name} field={field} />)}</div></section>}

      <section className="record-save-bar"><small>Prototipo local-first: los datos se guardan únicamente en este navegador.</small><button className="primary" type="submit" disabled={!ready}>Guardar {type.shortLabel.toLowerCase()} →</button></section>
    </form>
  );
}
