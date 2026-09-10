'use client';

import { FormEvent, useState } from 'react';
import Link from 'next/link';
import type { RecordField, RecordType } from '@/lib/record-types';
import { activityLabels, recordSlugToActivityType } from '@/lib/domain';
import { saveLocalActivity } from '@/lib/local-prototype-store';
import { useFieldContext } from '@/lib/use-field-context';
import { saveApiRecord, supportsApiRecord } from '@/lib/record-api-source';
import { completePlannedTask } from '@/lib/planned-task-data-source';
import { useAuth } from '@/components/auth-provider';
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
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [completionWarning, setCompletionWarning] = useState<string | null>(null);
  const [savedRemotely, setSavedRemotely] = useState(false);
  const { context, ready, found } = useFieldContext();
  const { selectedWorkspaceId } = useAuth();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ready || !found || saving) return;
    const formData = new FormData(event.currentTarget);
    const data: Record<string, string> = {};
    for (const [key, value] of formData.entries()) {
      if (typeof value === 'string' && value.trim()) data[key] = value.trim();
    }

    const activityType = recordSlugToActivityType[type.slug as keyof typeof recordSlugToActivityType];
    if (!activityType) return;

    setSaving(true);
    setSaveError(null);
    setCompletionWarning(null);

    try {
      if (context.source === 'api' && selectedWorkspaceId && supportsApiRecord(type.slug)) {
        const savedRecord = await saveApiRecord({ slug: type.slug, fieldId: context.id, workspaceId: selectedWorkspaceId, data });
        setSavedRemotely(true);

        const plannedEventId = new URLSearchParams(window.location.search).get('plannedEventId');
        if (plannedEventId && savedRecord.domainType !== 'expense') {
          try {
            await completePlannedTask({
              workspaceId: selectedWorkspaceId,
              taskId: plannedEventId,
              domainType: savedRecord.domainType,
              domainRecordId: savedRecord.recordId,
            });
          } catch (completionError) {
            console.warn('Record saved but planned task could not be linked', completionError);
            setCompletionWarning('El registro se ha guardado correctamente, pero la tarea prevista sigue pendiente. Puedes revisarla desde Hoy.');
          }
        }
      } else {
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
        setSavedRemotely(false);
      }

      setSaved(true);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (error) {
      console.error('Unable to save finca record', error);
      setSaveError('No se ha podido guardar el registro. Revisa la conexión o los datos e inténtalo de nuevo.');
    } finally {
      setSaving(false);
    }
  }

  if (ready && !found) {
    return <section className="record-success card"><h1>Finca no encontrada</h1><p>No se guardará ningún registro hasta identificar correctamente la finca de destino.</p><Link className="primary action-link" href="/mi-campo">Volver a Mi Campo</Link></section>;
  }

  if (saved) {
    return (
      <section className="record-success card">
        <div className="success-mark">✓</div>
        <span className="eyebrow dark">{savedRemotely ? 'GUARDADO EN MÁGINA' : 'GUARDADO EN ESTE DISPOSITIVO'}</span>
        <h1>{type.shortLabel} añadido a {context.name}</h1>
        <p>{savedRemotely ? 'El backend ha guardado el registro y sus proyecciones asociadas.' : context.source === 'api' ? 'Este tipo todavía se conserva como borrador local mientras se conecta al modelo Trabajo.' : 'El registro se ha guardado con la finca seleccionada.'}</p>
        {completionWarning ? <p className="form-error" role="status">{completionWarning}</p> : null}
        <div className="success-effects">
          <span>✓ Finca correcta: {context.name}</span>
          {savedRemotely ? <span>✓ Historial y costes derivados en servidor cuando corresponde</span> : <span>✓ Registro local preservado</span>}
          {savedRemotely && !completionWarning && typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('plannedEventId') ? <span>✓ Tarea prevista enlazada al registro real</span> : null}
          {type.followUp && <span>✓ Seguimiento, si has indicado fecha</span>}
        </div>
        <div className="record-actions">
          <button type="button" className="secondary-action" onClick={() => setSaved(false)}>Registrar otro</button>
          <Link className="primary action-link" href={context.returnHref}>Volver a la finca <ArrowIcon /></Link>
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
          <strong>{ready ? context.name : 'Cargando finca…'}</strong>
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

      {type.details && <details className="card record-details"><summary>Más detalles <span>Opcional</span></summary><div className="record-fields detail-fields">{type.details.map((field) => <Field key={field.name} field={field} />)}<label className="record-field wide photo-field"><span>Foto o documento</span><input className="record-control file-control" type="file" accept="image/*,.pdf" /><small>El archivo se conectará mediante el flujo documental; los datos del registro sí se guardan ya por su vía correspondiente.</small></label></div></details>}

      {type.followUp && <section className="card record-follow-up"><div><span className="eyebrow dark">DESPUÉS</span><h3>¿Quieres dejarlo programado?</h3><p>Si indicas una fecha quedará asociada al seguimiento del registro.</p></div><div className="record-fields follow-up-fields">{type.followUp.map((field) => <Field key={field.name} field={field} />)}</div></section>}

      {saveError ? <p className="form-error" role="alert">{saveError}</p> : null}
      <section className="record-save-bar"><small>{context.source === 'api' && supportsApiRecord(type.slug) ? 'Se guardará en el servidor real de Mágina.' : 'Modo local-first para este tipo de registro.'}</small><button className="primary" type="submit" disabled={!ready || !found || saving}>{saving ? 'Guardando…' : `Guardar ${type.shortLabel.toLowerCase()} →`}</button></section>
    </form>
  );
}