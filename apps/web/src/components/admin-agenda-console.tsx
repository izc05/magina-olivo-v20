'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError, apiFetch } from '../lib/api-client';
import { adminApi, type AdminSession } from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import styles from './admin-agenda-console.module.css';

type TaskStatus = 'planned' | 'postponed' | 'completed' | 'cancelled';
type TaskKind = 'treatment' | 'irrigation' | 'fertilization' | 'pruning' | 'harvest' | 'work' | 'observation' | 'other';
type AdminTask = {
  id: string;
  workspace_id: string;
  workspace_name: string;
  field_id: string | null;
  field_name: string | null;
  title: string;
  scheduled_at: string;
  status: TaskStatus;
  source: string;
  source_domain_type: string | null;
  task_kind: TaskKind | null;
  notes: string | null;
  created_by_name: string | null;
  completed_domain_type: string | null;
  completed_domain_record_id: string | null;
  completed_at: string | null;
};
type AgendaCounts = { planned: number; postponed: number; overdue: number; completed_30d: number };
type AdminField = { id: string; workspace_id: string; workspace_name: string; name: string; status: string };
type TaskDraft = { title: string; scheduled_at: string; task_kind: TaskKind; notes: string; status: 'planned' | 'postponed' | 'cancelled' };

type FilterStatus = 'active' | 'all' | TaskStatus;

const statusLabels: Record<TaskStatus, string> = { planned: 'Planificada', postponed: 'Pospuesta', completed: 'Completada', cancelled: 'Cancelada' };
const kindLabels: Record<TaskKind, string> = { treatment: 'Tratamiento', irrigation: 'Riego', fertilization: 'Abonado', pruning: 'Poda', harvest: 'Cosecha', work: 'Trabajo', observation: 'Observación', other: 'Otra' };

function editable(session: AdminSession | null) {
  return session?.platform_access.role === 'admin' || session?.platform_access.role === 'super_admin';
}
function localInput(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}
function isoFromLocal(value: string) {
  return new Date(value).toISOString();
}
function taskDraft(task: AdminTask): TaskDraft {
  return { title: task.title, scheduled_at: localInput(task.scheduled_at), task_kind: task.task_kind ?? 'other', notes: task.notes ?? '', status: task.status === 'completed' ? 'planned' : task.status };
}
function when(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function AdminAgendaConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [denied, setDenied] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [tasks, setTasks] = useState<AdminTask[]>([]);
  const [counts, setCounts] = useState<AgendaCounts>({ planned: 0, postponed: 0, overdue: 0, completed_30d: 0 });
  const [fields, setFields] = useState<AdminField[]>([]);
  const [statusFilter, setStatusFilter] = useState<FilterStatus>('active');
  const [query, setQuery] = useState('');
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [draft, setDraft] = useState<TaskDraft | null>(null);
  const [newFieldId, setNewFieldId] = useState('');
  const [newTitle, setNewTitle] = useState('');
  const [newWhen, setNewWhen] = useState('');
  const [newKind, setNewKind] = useState<TaskKind>('work');
  const [newNotes, setNewNotes] = useState('');

  const canEdit = editable(session);
  const selectedTask = useMemo(() => tasks.find((item) => item.id === selectedTaskId) ?? null, [tasks, selectedTaskId]);

  const load = useCallback(async (filter: FilterStatus = statusFilter) => {
    const params = new URLSearchParams({ status: filter, limit: '300' });
    if (query.trim()) params.set('q', query.trim());
    const [agendaPayload, fieldPayload] = await Promise.all([
      apiFetch<{ tasks: AdminTask[]; counts: AgendaCounts }>(`/api/v1/admin/agenda?${params}`),
      apiFetch<{ fields: AdminField[] }>('/api/v1/admin/fields?status=active'),
    ]);
    setTasks(agendaPayload.tasks);
    setCounts(agendaPayload.counts);
    setFields(fieldPayload.fields);
    setSelectedTaskId((current) => current && agendaPayload.tasks.some((item) => item.id === current) ? current : agendaPayload.tasks[0]?.id ?? null);
  }, [query, statusFilter]);

  const hydrate = useCallback(async () => {
    if (auth.status !== 'authenticated') return;
    setLoading(true);
    setDenied(false);
    setError(null);
    try {
      const current = await adminApi.session();
      setSession(current);
      await load();
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 403) {
        setDenied(true);
        setSession(null);
      } else {
        console.error(caught);
        setError('No se ha podido cargar la agenda administrativa.');
      }
    } finally {
      setLoading(false);
    }
  }, [auth.status, load]);

  useEffect(() => {
    if (auth.status === 'authenticated') void hydrate();
    if (auth.status === 'anonymous') setLoading(false);
  }, [auth.status, hydrate]);

  useEffect(() => { setDraft(selectedTask ? taskDraft(selectedTask) : null); }, [selectedTask]);

  async function run(task: () => Promise<void>, success: string) {
    setBusy(true); setError(null); setMessage(null);
    try { await task(); setMessage(success); }
    catch (caught) {
      console.error(caught);
      if (caught instanceof ApiRequestError && caught.status === 409) setError('La tarea ya está completada y no puede modificarse desde Administración.');
      else setError('No se ha podido completar la operación.');
    } finally { setBusy(false); }
  }

  async function saveTask() {
    if (!selectedTask || !draft || !canEdit || selectedTask.status === 'completed') return;
    await run(async () => {
      await apiFetch(`/api/v1/admin/agenda/${selectedTask.id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          title: draft.title.trim(), scheduled_at: isoFromLocal(draft.scheduled_at), task_kind: draft.task_kind,
          notes: draft.notes.trim() || null, status: draft.status,
        }),
      });
      await load();
    }, 'Tarea actualizada y auditada.');
  }

  async function createTask() {
    if (!canEdit || !newFieldId || !newTitle.trim() || !newWhen) return;
    await run(async () => {
      const result = await apiFetch<{ task: AdminTask }>('/api/v1/admin/agenda', {
        method: 'POST',
        body: JSON.stringify({ field_id: newFieldId, title: newTitle.trim(), scheduled_at: isoFromLocal(newWhen), task_kind: newKind, notes: newNotes.trim() || null }),
      });
      setNewTitle(''); setNewWhen(''); setNewNotes(''); setSelectedTaskId(result.task.id);
      await load();
    }, 'Tarea creada y auditada.');
  }

  async function changeFilter(value: FilterStatus) {
    setStatusFilter(value);
    setLoading(true);
    try { await load(value); } finally { setLoading(false); }
  }

  if (auth.status === 'loading' || loading) return <main className={styles.gate}><div className={styles.gateCard}><strong>Cargando agenda global…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className={styles.gate}><div className={styles.gateCard}><span>Administración V20</span><h1>Agenda protegida</h1><GoogleSignInButton /></div></main>;
  if (denied) return <main className={styles.gate}><div className={styles.gateCard}><h1>Acceso restringido</h1><Link href="/">Volver</Link></div></main>;
  if (!session) return <main className={styles.gate}><div className={styles.gateCard}><h1>No disponible</h1><p>{error}</p></div></main>;

  return <main className={styles.shell}>
    <header className={styles.hero}>
      <div><span className={styles.eyebrow}>Mágina Olivo V20 · Administración</span><h1>Agenda global</h1><p>Tareas planificadas de toda la plataforma con control administrativo y auditoría.</p></div>
      <div className={styles.nav}><span>{session.platform_access.role.replace('_', ' ')}</span><Link href="/admin/gestion">Gestión</Link><Link href="/admin/campanas-planes">Campañas y planes</Link><Link href="/admin/operaciones">Operaciones</Link></div>
    </header>

    {message ? <div className={styles.success}>{message}</div> : null}{error ? <div className={styles.error}>{error}</div> : null}
    <section className={styles.metrics}><article><span>Planificadas</span><strong>{counts.planned}</strong></article><article><span>Pospuestas</span><strong>{counts.postponed}</strong></article><article><span>Atrasadas</span><strong>{counts.overdue}</strong></article><article><span>Completadas 30 d</span><strong>{counts.completed_30d}</strong></article></section>

    {canEdit ? <section className={styles.createPanel}>
      <div><span className={styles.eyebrow}>Nueva tarea</span><h2>Planificar desde Administración</h2><p>La tarea se crea como manual en el workspace de la finca elegida.</p></div>
      <div className={styles.createGrid}>
        <label>Finca<select value={newFieldId} onChange={(event) => setNewFieldId(event.target.value)}><option value="">Selecciona finca…</option>{fields.map((field) => <option key={field.id} value={field.id}>{field.workspace_name} · {field.name}</option>)}</select></label>
        <label>Tipo<select value={newKind} onChange={(event) => setNewKind(event.target.value as TaskKind)}>{Object.entries(kindLabels).map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>Título<input value={newTitle} onChange={(event) => setNewTitle(event.target.value)} /></label>
        <label>Fecha y hora<input type="datetime-local" value={newWhen} onChange={(event) => setNewWhen(event.target.value)} /></label>
        <label className={styles.full}>Notas<textarea rows={2} value={newNotes} onChange={(event) => setNewNotes(event.target.value)} /></label>
      </div><button className={styles.primary} disabled={busy || !newFieldId || !newTitle.trim() || !newWhen} onClick={() => void createTask()}>Crear tarea</button>
    </section> : null}

    <section className={styles.layout}>
      <aside className={styles.listPanel}>
        <div className={styles.heading}><div><span className={styles.eyebrow}>Todas las tareas</span><h2>Agenda</h2></div><button disabled={busy} onClick={() => void load()}>Actualizar</button></div>
        <div className={styles.filters}><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar tarea, finca o workspace…" onKeyDown={(event) => { if (event.key === 'Enter') void load(); }} /><select value={statusFilter} onChange={(event) => void changeFilter(event.target.value as FilterStatus)}><option value="active">Activas</option><option value="all">Todas</option><option value="planned">Planificadas</option><option value="postponed">Pospuestas</option><option value="completed">Completadas</option><option value="cancelled">Canceladas</option></select></div>
        <div className={styles.list}>{tasks.map((task) => <button key={task.id} className={selectedTaskId === task.id ? styles.selected : ''} onClick={() => setSelectedTaskId(task.id)}><strong>{task.title}</strong><span>{task.workspace_name} · {task.field_name ?? 'sin finca'}</span><small>{statusLabels[task.status]} · {when(task.scheduled_at)}</small></button>)}{!tasks.length ? <p>Sin tareas para este filtro.</p> : null}</div>
      </aside>

      <section className={styles.detailPanel}>
        {selectedTask && draft ? <><div className={styles.heading}><div><span className={styles.eyebrow}>Detalle administrativo</span><h2>{selectedTask.title}</h2><p>{selectedTask.workspace_name} · {selectedTask.field_name ?? 'Sin finca'} · origen {selectedTask.source}</p></div><span className={styles.badge}>{statusLabels[selectedTask.status]}</span></div>
        {selectedTask.status === 'completed' ? <div className={styles.notice}>La tarea está completada y queda inmutable para preservar el vínculo con su registro ejecutado. Puedes consultarla, pero no reabrirla desde este panel.</div> : null}
        <div className={styles.formGrid}>
          <label>Título<input disabled={!canEdit || selectedTask.status === 'completed'} value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} /></label>
          <label>Tipo<select disabled={!canEdit || selectedTask.status === 'completed'} value={draft.task_kind} onChange={(event) => setDraft({ ...draft, task_kind: event.target.value as TaskKind })}>{Object.entries(kindLabels).map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <label>Fecha y hora<input disabled={!canEdit || selectedTask.status === 'completed'} type="datetime-local" value={draft.scheduled_at} onChange={(event) => setDraft({ ...draft, scheduled_at: event.target.value })} /></label>
          <label>Estado<select disabled={!canEdit || selectedTask.status === 'completed'} value={draft.status} onChange={(event) => setDraft({ ...draft, status: event.target.value as TaskDraft['status'] })}><option value="planned">Planificada</option><option value="postponed">Pospuesta</option><option value="cancelled">Cancelada</option></select></label>
          <label className={styles.full}>Notas<textarea disabled={!canEdit || selectedTask.status === 'completed'} rows={4} value={draft.notes} onChange={(event) => setDraft({ ...draft, notes: event.target.value })} /></label>
        </div>
        <div className={styles.facts}><span>Creada por: <strong>{selectedTask.created_by_name ?? 'sistema'}</strong></span><span>Dominio origen: <strong>{selectedTask.source_domain_type ?? 'manual'}</strong></span>{selectedTask.completed_domain_type ? <span>Completada con: <strong>{selectedTask.completed_domain_type}</strong></span> : null}</div>
        {canEdit && selectedTask.status !== 'completed' ? <button className={styles.primary} disabled={busy || !draft.title.trim() || !draft.scheduled_at} onClick={() => void saveTask()}>Guardar tarea</button> : null}</> : <p>Selecciona una tarea.</p>}
      </section>
    </section>
  </main>;
}
