'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { PlannedTaskActions } from '@/components/planned-task-actions';
import { loadWorkspaceFarms, type FarmListItem } from '@/lib/farm-data-source';
import {
  createPlannedTask,
  dateTimeLocalToIso,
  listPlannedTasks,
  localDateTimeValue,
  plannedTaskExecutionHref,
  type PlannedTask,
  type PlannedTaskKind,
  type PlannedTaskListStatus,
} from '@/lib/planned-task-data-source';
import styles from '@/app/mi-campo/planificar/planificar.module.css';

const kinds: Array<{ value: PlannedTaskKind; label: string; hint: string }> = [
  { value: 'work', label: 'Trabajo', hint: 'Poda, desbroce, laboreo, transporte o cualquier labor general' },
  { value: 'treatment', label: 'Tratamiento', hint: 'Tratamiento fitosanitario' },
  { value: 'irrigation', label: 'Riego', hint: 'Riego o revisión del sistema' },
  { value: 'fertilization', label: 'Abonado', hint: 'Fertilización o aporte de nutrientes' },
  { value: 'pruning', label: 'Poda', hint: 'Poda y trabajos asociados' },
  { value: 'harvest', label: 'Cosecha', hint: 'Recolección o entrega de aceituna' },
  { value: 'observation', label: 'Observación', hint: 'Revisar una incidencia o anotar una comprobación' },
  { value: 'other', label: 'Otra tarea', hint: 'Cualquier tarea que después se registre como trabajo' },
];

const statusOptions: Array<{ value: PlannedTaskListStatus; label: string }> = [
  { value: 'active', label: 'Pendientes' },
  { value: 'completed', label: 'Realizadas' },
  { value: 'cancelled', label: 'Canceladas' },
  { value: 'all', label: 'Todas' },
];

function defaultLocalDateTime(daysAhead = 1) {
  const date = new Date();
  date.setDate(date.getDate() + daysAhead);
  date.setHours(8, 0, 0, 0);
  return localDateTimeValue(date.toISOString());
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('es-ES', {
    weekday: 'short',
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function kindLabel(kind?: PlannedTaskKind) {
  return kinds.find((item) => item.value === kind)?.label ?? 'Otra tarea';
}

function statusLabel(status: PlannedTask['status']) {
  if (status === 'completed') return 'Realizada';
  if (status === 'cancelled') return 'Cancelada';
  if (status === 'postponed') return 'Aplazada';
  return 'Pendiente';
}

function taskDayKey(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function monthTitle(date: Date) {
  const text = date.toLocaleDateString('es-ES', { month: 'long', year: 'numeric' });
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function isActive(task: PlannedTask) {
  return task.status === 'planned' || task.status === 'postponed';
}

function CalendarView({ tasks, month, onMonthChange }: {
  tasks: PlannedTask[];
  month: Date;
  onMonthChange: (date: Date) => void;
}) {
  const year = month.getFullYear();
  const monthIndex = month.getMonth();
  const first = new Date(year, monthIndex, 1);
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
  const leading = (first.getDay() + 6) % 7;
  const cells = Array.from({ length: leading + daysInMonth }, (_, index) => index < leading ? null : index - leading + 1);
  while (cells.length % 7 !== 0) cells.push(null);

  const byDay = new Map<string, PlannedTask[]>();
  for (const task of tasks) {
    const key = taskDayKey(task.scheduledAt);
    if (!key) continue;
    const bucket = byDay.get(key) ?? [];
    bucket.push(task);
    byDay.set(key, bucket);
  }

  function move(delta: number) {
    onMonthChange(new Date(year, monthIndex + delta, 1));
  }

  return <section className={styles.calendarPanel} aria-label={`Calendario ${monthTitle(month)}`}>
    <div className={styles.calendarHead}>
      <button type="button" className="secondary-action" onClick={() => move(-1)} aria-label="Mes anterior">←</button>
      <h2>{monthTitle(month)}</h2>
      <button type="button" className="secondary-action" onClick={() => move(1)} aria-label="Mes siguiente">→</button>
    </div>
    <div className={styles.weekdays} aria-hidden="true">
      {['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'].map((day) => <span key={day}>{day}</span>)}
    </div>
    <div className={styles.calendarGrid}>
      {cells.map((day, index) => {
        if (day === null) return <div className={styles.emptyDay} key={`empty-${index}`} />;
        const key = `${year}-${String(monthIndex + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
        const dayTasks = byDay.get(key) ?? [];
        const today = key === taskDayKey(new Date().toISOString());
        return <div className={`${styles.dayCell} ${today ? styles.todayCell : ''}`} key={key}>
          <strong>{day}</strong>
          <div className={styles.dayTasks}>
            {dayTasks.slice(0, 3).map((task) => <span className={`${styles.calendarTask} ${styles[task.status] ?? ''}`} key={task.id} title={`${task.fieldName}: ${task.title}`}>{task.title}</span>)}
            {dayTasks.length > 3 ? <small>+{dayTasks.length - 3} más</small> : null}
          </div>
        </div>;
      })}
    </div>
  </section>;
}

function TaskRow({ task, workspaceId, onChanged }: { task: PlannedTask; workspaceId: string; onChanged: () => void }) {
  const active = isActive(task);
  return <article className={styles.taskCard}>
    <div className={styles.taskMain}>
      <div className={styles.taskMeta}>
        <span className={`${styles.statusPill} ${styles[task.status] ?? ''}`}>{statusLabel(task.status)}</span>
        <span>{kindLabel(task.taskKind)}</span>
      </div>
      <h3>{task.title}</h3>
      <p><strong>{task.fieldName}</strong> · {formatDate(task.scheduledAt)}</p>
      {task.notes ? <p className={styles.notes}>{task.notes}</p> : null}
      {task.completion ? <small className={styles.completion}>✓ Enlazada con el registro realizado{task.completion.completedAt ? ` · ${formatDate(task.completion.completedAt)}` : ''}</small> : null}
    </div>
    <div className={styles.taskActions}>
      {active ? <Link className="primary action-link" href={plannedTaskExecutionHref(task)}>Registrar realizado →</Link> : null}
      <PlannedTaskActions item={task} workspaceId={workspaceId} onChanged={onChanged} />
    </div>
  </article>;
}

export function PlanTaskClient() {
  const { apiConfigured, previewEnabled, status, selectedWorkspaceId } = useAuth();
  const [farms, setFarms] = useState<FarmListItem[]>([]);
  const [tasks, setTasks] = useState<PlannedTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [creationFieldId, setCreationFieldId] = useState('');
  const [filterFieldId, setFilterFieldId] = useState('');
  const [filterStatus, setFilterStatus] = useState<PlannedTaskListStatus>('active');
  const [view, setView] = useState<'agenda' | 'calendar'>('agenda');
  const [calendarMonth, setCalendarMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [kind, setKind] = useState<PlannedTaskKind>('work');
  const [title, setTitle] = useState('');
  const [scheduledAt, setScheduledAt] = useState(() => defaultLocalDateTime(1));
  const [notes, setNotes] = useState('');
  const [saving, setSaving] = useState(false);

  const connected = apiConfigured && status === 'authenticated' && Boolean(selectedWorkspaceId);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    const queryFieldId = params.get('fieldId');
    if (queryFieldId) {
      setCreationFieldId(queryFieldId);
      setFilterFieldId(queryFieldId);
      setShowCreate(true);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (apiConfigured && status === 'loading') return;
      if (!connected || !selectedWorkspaceId) {
        setFarms([]);
        setTasks([]);
        setLoading(false);
        setError(apiConfigured
          ? 'Inicia sesión para consultar y modificar tu planificación.'
          : previewEnabled
            ? null
            : 'Planificar necesita conexión con el servicio de datos.');
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const [nextFarms, nextTasks] = await Promise.all([
          loadWorkspaceFarms(selectedWorkspaceId),
          listPlannedTasks({ workspaceId: selectedWorkspaceId, status: 'all' }),
        ]);
        if (cancelled) return;
        const activeFarms = nextFarms.filter((farm) => farm.status !== 'archived');
        setFarms(activeFarms);
        setTasks(nextTasks);
        setCreationFieldId((current) => current || (activeFarms.length === 1 ? activeFarms[0]!.id : ''));
      } catch (cause) {
        console.error('Unable to load planning workspace', cause);
        if (!cancelled) setError('No se ha podido cargar la planificación. Comprueba la conexión e inténtalo de nuevo.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [apiConfigured, connected, previewEnabled, revision, selectedWorkspaceId, status]);

  const filteredTasks = useMemo(() => {
    const filtered = tasks.filter((task) => {
      if (filterFieldId && task.fieldId !== filterFieldId) return false;
      if (filterStatus === 'active') return isActive(task);
      if (filterStatus === 'all') return true;
      return task.status === filterStatus;
    });
    return filtered.sort((a, b) => {
      const direction = filterStatus === 'completed' || filterStatus === 'cancelled' ? -1 : 1;
      return direction * (new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime());
    });
  }, [filterFieldId, filterStatus, tasks]);

  const metrics = useMemo(() => {
    const now = new Date();
    const today = taskDayKey(now.toISOString());
    const weekEnd = new Date(now);
    weekEnd.setDate(weekEnd.getDate() + 7);
    return {
      pending: tasks.filter(isActive).length,
      overdue: tasks.filter((task) => isActive(task) && new Date(task.scheduledAt).getTime() < now.getTime()).length,
      today: tasks.filter((task) => isActive(task) && taskDayKey(task.scheduledAt) === today).length,
      week: tasks.filter((task) => isActive(task) && new Date(task.scheduledAt) >= now && new Date(task.scheduledAt) <= weekEnd).length,
    };
  }, [tasks]);

  const grouped = useMemo(() => {
    if (filterStatus !== 'active') return [{ title: filterStatus === 'completed' ? 'Realizadas' : filterStatus === 'cancelled' ? 'Canceladas' : 'Historial', tasks: filteredTasks }];
    const now = new Date();
    const todayKey = taskDayKey(now.toISOString());
    const weekEnd = new Date(now); weekEnd.setDate(weekEnd.getDate() + 7);
    const buckets = { overdue: [] as PlannedTask[], today: [] as PlannedTask[], week: [] as PlannedTask[], later: [] as PlannedTask[] };
    for (const task of filteredTasks) {
      const date = new Date(task.scheduledAt);
      if (date.getTime() < now.getTime() && taskDayKey(task.scheduledAt) !== todayKey) buckets.overdue.push(task);
      else if (taskDayKey(task.scheduledAt) === todayKey) buckets.today.push(task);
      else if (date <= weekEnd) buckets.week.push(task);
      else buckets.later.push(task);
    }
    return [
      { title: 'Atrasadas', tasks: buckets.overdue },
      { title: 'Para hoy', tasks: buckets.today },
      { title: 'Próximos 7 días', tasks: buckets.week },
      { title: 'Más adelante', tasks: buckets.later },
    ];
  }, [filterStatus, filteredTasks]);

  function refresh(message?: string) {
    if (message) setSuccess(message);
    setRevision((value) => value + 1);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !creationFieldId || !title.trim() || !scheduledAt) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await createPlannedTask({
        workspaceId: selectedWorkspaceId,
        fieldId: creationFieldId,
        title: title.trim(),
        scheduledAt: dateTimeLocalToIso(scheduledAt),
        taskKind: kind,
        notes: notes.trim() || undefined,
      });
      setTitle('');
      setNotes('');
      setScheduledAt(defaultLocalDateTime(1));
      setFilterStatus('active');
      setFilterFieldId(creationFieldId);
      setShowCreate(false);
      refresh('Tarea guardada. Ya forma parte de tu planificación.');
    } catch (cause) {
      console.error('Unable to create planned task', cause);
      setError('No se ha podido guardar la tarea. Revisa la finca, la fecha y los datos introducidos.');
    } finally {
      setSaving(false);
    }
  }

  if (apiConfigured && status === 'loading') return <section className="card"><p>Comprobando sesión…</p></section>;

  if (!apiConfigured) return <>
    <header className="page-title mi-campo-title"><div><span className="eyebrow dark">MI CAMPO · PLANIFICAR</span><h1>Planificar</h1><p>Organiza los próximos trabajos y conviértelos en registros reales cuando los hagas.</p></div></header>
    <section className="card"><strong>{previewEnabled ? 'Vista de demostración' : 'Planificación no disponible'}</strong><p>Las tareas son datos privados y no se simulan en este modo. Conecta Mágina al servicio de datos e inicia sesión para crear, reprogramar y ejecutar tareas reales.</p></section>
  </>;

  if (!connected) return <>
    <header className="page-title mi-campo-title"><div><span className="eyebrow dark">MI CAMPO · PLANIFICAR</span><h1>Planificar</h1><p>Organiza los próximos trabajos y conviértelos en registros reales cuando los hagas.</p></div></header>
    <section className="card"><h2>Inicia sesión</h2><p>La planificación pertenece a tu explotación y necesita una sesión privada.</p><Link href="/perfil" className="primary action-link">Ir a mi cuenta</Link></section>
  </>;

  return <>
    <header className={`page-title mi-campo-title ${styles.header}`}>
      <div><span className="eyebrow dark">MI CAMPO · PLANIFICAR</span><h1>Planificar</h1><p>Decide qué toca hacer, en qué finca y cuándo. Al realizarlo, registra el trabajo y la tarea quedará enlazada.</p></div>
      <button type="button" className="primary" onClick={() => setShowCreate((value) => !value)}>{showCreate ? 'Cerrar' : '+ Nueva tarea'}</button>
    </header>

    <section className={`card ${styles.metrics}`} aria-label="Resumen de planificación">
      <div><b>{metrics.pending}</b><span>pendientes</span></div>
      <div><b>{metrics.overdue}</b><span>atrasadas</span></div>
      <div><b>{metrics.today}</b><span>para hoy</span></div>
      <div><b>{metrics.week}</b><span>próximos 7 días</span></div>
    </section>

    {showCreate ? <form className={`card ${styles.createCard}`} onSubmit={submit}>
      <div className={styles.sectionHead}><div><span className="eyebrow dark">NUEVA TAREA</span><h2>¿Qué quieres dejar preparado?</h2></div></div>
      <div className="record-fields">
        <label className="record-field"><span>Finca</span><select className="record-control" value={creationFieldId} required onChange={(event) => setCreationFieldId(event.target.value)}><option value="">Selecciona una finca</option>{farms.map((farm) => <option key={farm.id} value={farm.id}>{farm.name}{farm.municipality ? ` · ${farm.municipality}` : ''}</option>)}</select></label>
        <label className="record-field"><span>Tipo</span><select className="record-control" value={kind} onChange={(event) => setKind(event.target.value as PlannedTaskKind)}>{kinds.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select><small>{kinds.find((item) => item.value === kind)?.hint}</small></label>
        <label className="record-field wide"><span>Tarea</span><input className="record-control" value={title} maxLength={180} required placeholder="Ej. Desbrozar la linde norte" onChange={(event) => setTitle(event.target.value)} /></label>
        <label className="record-field"><span>Fecha y hora</span><input className="record-control" type="datetime-local" value={scheduledAt} required onChange={(event) => setScheduledAt(event.target.value)} /></label>
        <label className="record-field wide"><span>Notas</span><textarea className="record-control" value={notes} rows={3} maxLength={3000} placeholder="Material, zona de la finca o cualquier detalle útil" onChange={(event) => setNotes(event.target.value)} /></label>
      </div>
      <div className={styles.quickDates} aria-label="Fechas rápidas">
        <span>Fecha rápida</span>
        <button type="button" className="secondary-action" onClick={() => setScheduledAt(defaultLocalDateTime(1))}>Mañana</button>
        <button type="button" className="secondary-action" onClick={() => setScheduledAt(defaultLocalDateTime(7))}>En 7 días</button>
      </div>
      <button type="submit" className="primary" disabled={saving || !creationFieldId || !title.trim()}>{saving ? 'Guardando…' : 'Guardar tarea'}</button>
    </form> : null}

    {success ? <p className={styles.success} role="status">✓ {success}</p> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}

    <section className={`card ${styles.toolbar}`} aria-label="Filtros de planificación">
      <div className={styles.statusFilters}>{statusOptions.map((option) => <button type="button" key={option.value} className={filterStatus === option.value ? styles.activeFilter : styles.filterButton} onClick={() => setFilterStatus(option.value)}>{option.label}</button>)}</div>
      <label className={styles.farmFilter}><span>Finca</span><select value={filterFieldId} onChange={(event) => setFilterFieldId(event.target.value)}><option value="">Todas las fincas</option>{farms.map((farm) => <option key={farm.id} value={farm.id}>{farm.name}</option>)}</select></label>
      <div className={styles.viewSwitch} aria-label="Vista"><button type="button" className={view === 'agenda' ? styles.activeFilter : styles.filterButton} onClick={() => setView('agenda')}>Agenda</button><button type="button" className={view === 'calendar' ? styles.activeFilter : styles.filterButton} onClick={() => setView('calendar')}>Calendario</button></div>
    </section>

    {loading ? <section className="card"><p>Cargando planificación…</p></section> : null}

    {!loading && !error && !farms.length ? <section className="card"><h2>Primero crea una finca</h2><p>Una tarea siempre pertenece a una finca para que después pueda enlazarse con el trabajo realmente realizado.</p><Link href="/mi-campo/fincas/nueva" className="primary action-link">Crear finca</Link></section> : null}

    {!loading && !error && farms.length && !filteredTasks.length ? <section className={`card ${styles.empty}`}><h2>No hay tareas en esta vista</h2><p>{filterStatus === 'active' ? 'No tienes trabajos pendientes con estos filtros.' : 'Todavía no hay tareas que coincidan con este historial.'}</p><button type="button" className="primary" onClick={() => setShowCreate(true)}>Planificar una tarea</button></section> : null}

    {!loading && !error && filteredTasks.length && view === 'calendar' ? <CalendarView tasks={filteredTasks} month={calendarMonth} onMonthChange={setCalendarMonth} /> : null}

    {!loading && !error && filteredTasks.length && view === 'agenda' ? <div className={styles.agenda}>
      {grouped.map((group) => group.tasks.length ? <section key={group.title} className={styles.group}>
        <div className={styles.sectionHead}><h2>{group.title}</h2><span>{group.tasks.length}</span></div>
        <div className={styles.taskList}>{group.tasks.map((task) => <TaskRow key={task.id} task={task} workspaceId={selectedWorkspaceId!} onChanged={() => refresh()} />)}</div>
      </section> : null)}
    </div> : null}

    <section className={`card ${styles.rule}`}><strong>Planificar no es dar por hecho</strong><p>Una tarea solo pasa a “Realizada” cuando registras el trabajo, riego, tratamiento, abonado, poda, cosecha u observación correspondiente en la misma finca. Cancelarla o moverla de fecha no crea ningún trabajo ficticio.</p></section>
  </>;
}
