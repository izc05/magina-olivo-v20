'use client';

import { useEffect, useState } from 'react';
import {
  dateTimeLocalToIso,
  localDateTimeValue,
  postponeIso,
  updatePlannedTask,
} from '@/lib/planned-task-data-source';

type ActionableTask = {
  id: string;
  title: string;
  scheduledAt: string;
  fieldName?: string;
  taskKind?: string;
  sourceDomainType?: string;
  notes?: string;
  status?: 'planned' | 'postponed' | 'completed' | 'cancelled';
};

function taskTypeLabel(value?: string) {
  const labels: Record<string, string> = {
    treatment: 'Tratamiento',
    irrigation: 'Riego',
    fertilization: 'Abonado',
    pruning: 'Poda',
    harvest: 'Cosecha',
    harvest_delivery: 'Entrega de cosecha',
    work: 'Trabajo',
    observation: 'Observación',
    other: 'Otro',
  };
  if (!value) return 'Otro';
  return labels[value] ?? value.replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function PlannedTaskActions({
  item,
  workspaceId,
  onChanged,
}: {
  item: ActionableTask;
  workspaceId: string;
  onChanged: () => void | Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState(item.title);
  const [notes, setNotes] = useState(item.notes ?? '');
  const [dateValue, setDateValue] = useState(localDateTimeValue(item.scheduledAt));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setTitle(item.title);
    setNotes(item.notes ?? '');
    setDateValue(localDateTimeValue(item.scheduledAt));
  }, [item.notes, item.scheduledAt, item.title]);

  async function run(patch: {
    scheduledAt?: string;
    title?: string;
    notes?: string | null;
    status?: 'planned' | 'postponed' | 'cancelled';
  }) {
    setSaving(true);
    setError(null);
    try {
      await updatePlannedTask({ workspaceId, taskId: item.id, ...patch });
      await onChanged();
      setOpen(false);
    } catch (cause) {
      console.error('Unable to update planned task', cause);
      setError('No se ha podido actualizar la tarea.');
    } finally {
      setSaving(false);
    }
  }

  const immutable = item.status === 'completed';
  const cancelled = item.status === 'cancelled';

  return <div className="planned-task-actions">
    <button type="button" className="secondary-action" onClick={() => setOpen((value) => !value)}>
      {open ? 'Cerrar' : immutable ? 'Ver' : 'Revisar'}
    </button>

    {open ? <div className="planned-task-review card">
      <p><strong>{item.title}</strong></p>
      <p><small>Finca: {item.fieldName ?? 'Sin finca'} · Tipo: {taskTypeLabel(item.taskKind ?? item.sourceDomainType)}</small></p>

      {immutable ? <p className="subtle">Esta tarea ya está completada y queda como parte del historial.</p> : <>
        <div className="record-fields">
          <label className="record-field wide"><span>Tarea</span><input className="record-control" value={title} maxLength={180} onChange={(event) => setTitle(event.target.value)} /></label>
          <label className="record-field"><span>Fecha y hora</span><input className="record-control" type="datetime-local" value={dateValue} onChange={(event) => setDateValue(event.target.value)} /></label>
          <label className="record-field wide"><span>Notas</span><textarea className="record-control" value={notes} rows={3} maxLength={3000} onChange={(event) => setNotes(event.target.value)} /></label>
        </div>

        <div className="record-actions">
          <button type="button" className="primary" disabled={saving || !title.trim() || !dateValue} onClick={() => void run({ title: title.trim(), notes: notes.trim() || null, scheduledAt: dateTimeLocalToIso(dateValue), status: cancelled ? 'planned' : item.status === 'postponed' ? 'postponed' : 'planned' })}>{saving ? 'Guardando…' : cancelled ? 'Reactivar y guardar' : 'Guardar cambios'}</button>
          {!cancelled ? <button type="button" className="secondary-action" disabled={saving} onClick={() => void run({ scheduledAt: postponeIso(item.scheduledAt, 1), status: 'postponed' })}>Aplazar 1 día</button> : null}
          {!cancelled ? <button type="button" className="danger-action" disabled={saving} onClick={() => void run({ status: 'cancelled' })}>Cancelar tarea</button> : null}
        </div>
      </>}

      {item.notes && immutable ? <p>{item.notes}</p> : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
    </div> : null}
  </div>;
}
