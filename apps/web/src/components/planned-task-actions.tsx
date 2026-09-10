'use client';

import { useState } from 'react';
import type { AgendaItem } from '@/lib/agenda-data-source';
import { dateTimeLocalToIso, localDateTimeValue, postponeIso, updatePlannedTask } from '@/lib/planned-task-api';

export function PlannedTaskActions({
  item,
  workspaceId,
  onChanged,
}: {
  item: AgendaItem;
  workspaceId: string;
  onChanged: () => void | Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [editingDate, setEditingDate] = useState(false);
  const [dateValue, setDateValue] = useState(localDateTimeValue(item.scheduledAt));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(patch: Parameters<typeof updatePlannedTask>[2]) {
    setSaving(true);
    setError(null);
    try {
      await updatePlannedTask(workspaceId, item.id, patch);
      await onChanged();
      setEditingDate(false);
    } catch (cause) {
      console.error('Unable to update planned task', cause);
      setError('No se ha podido actualizar la tarea.');
    } finally {
      setSaving(false);
    }
  }

  return <div className="planned-task-actions">
    <button type="button" className="secondary-action" onClick={() => setOpen((value) => !value)}>
      {open ? 'Cerrar' : 'Revisar'}
    </button>

    {open ? <div className="planned-task-review card">
      <p><strong>{item.title}</strong></p>
      <p><small>Finca: {item.fieldName ?? 'Sin finca'} · Tipo: {item.taskKind ?? item.sourceDomainType ?? 'otro'}</small></p>
      {item.notes ? <p>{item.notes}</p> : <p className="subtle">Sin notas.</p>}

      <div className="record-actions">
        <button type="button" className="secondary-action" disabled={saving} onClick={() => void run({ scheduled_at: postponeIso(item.scheduledAt, 1), status: 'postponed' })}>Aplazar 1 día</button>
        <button type="button" className="secondary-action" disabled={saving} onClick={() => setEditingDate((value) => !value)}>Cambiar fecha</button>
        <button type="button" className="danger-action" disabled={saving} onClick={() => void run({ status: 'cancelled' })}>Cancelar tarea</button>
      </div>

      {editingDate ? <div className="record-fields">
        <label className="record-field"><span>Nueva fecha y hora</span><input className="record-control" type="datetime-local" value={dateValue} onChange={(event) => setDateValue(event.target.value)} /></label>
        <button type="button" className="primary" disabled={saving || !dateValue} onClick={() => void run({ scheduled_at: dateTimeLocalToIso(dateValue), status: 'planned' })}>{saving ? 'Guardando…' : 'Guardar nueva fecha'}</button>
      </div> : null}

      {error ? <p className="form-error" role="alert">{error}</p> : null}
    </div> : null}
  </div>;
}
