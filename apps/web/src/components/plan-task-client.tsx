'use client';

import Link from 'next/link';
import { FormEvent, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { createPlannedTask, type PlannedTaskKind } from '@/lib/planned-task-data-source';
import { useFieldContext } from '@/lib/use-field-context';

const kinds: Array<{ value: PlannedTaskKind; label: string; hint: string }> = [
  { value: 'treatment', label: 'Tratamiento', hint: 'Mágina revisará lluvia y viento.' },
  { value: 'irrigation', label: 'Riego', hint: 'Mágina revisará si se espera precipitación.' },
  { value: 'pruning', label: 'Poda', hint: 'Mágina mostrará contexto de lluvia y viento.' },
  { value: 'harvest', label: 'Cosecha', hint: 'Mágina mostrará contexto de lluvia y viento.' },
  { value: 'fertilization', label: 'Abonado', hint: 'Quedará programado en la agenda.' },
  { value: 'work', label: 'Otro trabajo', hint: 'Labor general de campo.' },
  { value: 'observation', label: 'Revisión / observación', hint: 'Recordatorio para volver a revisar algo.' },
  { value: 'other', label: 'Otro', hint: 'Tarea manual.' },
];

function toLocalInput(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function defaultLocalDateTime() {
  const now = new Date(Date.now() + 24 * 60 * 60 * 1000);
  now.setHours(9, 0, 0, 0);
  return toLocalInput(now);
}

function quickDate(kind: 'tomorrow' | 'saturday' | 'next-week') {
  const date = new Date();
  date.setHours(9, 0, 0, 0);
  if (kind === 'tomorrow') date.setDate(date.getDate() + 1);
  if (kind === 'next-week') date.setDate(date.getDate() + 7);
  if (kind === 'saturday') {
    const days = (6 - date.getDay() + 7) % 7 || 7;
    date.setDate(date.getDate() + days);
  }
  return toLocalInput(date);
}

export function PlanTaskClient() {
  const { context, ready, found } = useFieldContext();
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [kind, setKind] = useState<PlannedTaskKind>('treatment');
  const [title, setTitle] = useState('');
  const [scheduledAt, setScheduledAt] = useState(defaultLocalDateTime);
  const [notes, setNotes] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const kindMeta = useMemo(() => kinds.find((item) => item.value === kind)!, [kind]);
  const canPersist = apiConfigured && status === 'authenticated' && selectedWorkspaceId && context.source === 'api';

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSaved(false);
    if (!canPersist) {
      setError('La planificación real necesita una finca del servidor. En la preview puedes revisar el flujo, pero no se inventarán tareas remotas.');
      return;
    }
    const localDate = new Date(scheduledAt);
    if (!Number.isFinite(localDate.getTime())) {
      setError('Indica una fecha y hora válidas.');
      return;
    }
    try {
      setSaving(true);
      await createPlannedTask({
        workspaceId: selectedWorkspaceId,
        fieldId: context.id,
        title: title.trim() || kindMeta.label,
        scheduledAt: localDate.toISOString(),
        taskKind: kind,
        notes: notes.trim() || undefined,
      });
      setSaved(true);
      setTitle('');
      setNotes('');
    } catch (err) {
      console.error(err);
      setError('No se ha podido guardar la tarea prevista.');
    } finally {
      setSaving(false);
    }
  }

  if (!ready) return <section className="card"><p>Cargando finca…</p></section>;
  if (!found) return <section className="card"><h1>Finca no encontrada</h1><Link href="/mi-campo">Volver a Mi Campo</Link></section>;

  return <>
    <header className="page-title mi-campo-title">
      <div><span className="eyebrow dark">MI CAMPO · PLANIFICAR</span><h1>¿Qué quieres hacer?</h1><p>{context.name} · crea una tarea futura sin registrarla todavía como trabajo realizado.</p></div>
    </header>

    <form className="section card" onSubmit={submit}>
      <label className="form-field"><span>Tipo de tarea</span><select value={kind} onChange={(event) => setKind(event.target.value as PlannedTaskKind)}>{kinds.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select><small>{kindMeta.hint}</small></label>
      <label className="form-field"><span>Qué vas a hacer</span><input value={title} onChange={(event) => setTitle(event.target.value)} placeholder={kindMeta.label} maxLength={180} /></label>
      <div className="form-field"><span>Cuándo</span><div className="record-actions"><button type="button" className="secondary-action" onClick={() => setScheduledAt(quickDate('tomorrow'))}>Mañana</button><button type="button" className="secondary-action" onClick={() => setScheduledAt(quickDate('saturday'))}>Sábado</button><button type="button" className="secondary-action" onClick={() => setScheduledAt(quickDate('next-week'))}>+ 1 semana</button></div><input type="datetime-local" value={scheduledAt} onChange={(event) => setScheduledAt(event.target.value)} required /><small>Los accesos rápidos usan las 09:00; puedes cambiar la hora antes de guardar.</small></div>
      <label className="form-field"><span>Notas</span><textarea value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Opcional: zona, motivo, preparación…" maxLength={3000} /></label>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {saved ? <p role="status"><strong>Tarea planificada.</strong> Ya aparecerá en Hoy y podrá recibir contexto meteorológico cuando corresponda.</p> : null}
      <div className="form-actions"><button className="primary" type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Planificar tarea'}</button><Link className="secondary-action action-link" href={context.returnHref}>Volver a la finca</Link></div>
    </form>

    <section className="card register-principle"><div><strong>Planificar no es registrar</strong><small>La tarea seguirá pendiente hasta que exista un registro real asociado. Pasar la fecha no la marca como hecha.</small></div></section>
  </>;
}
