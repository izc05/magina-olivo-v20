'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { PlannedTaskActions } from '@/components/planned-task-actions';
import { emptyAgenda, loadApiAgenda, type AgendaItem, type AgendaView } from '@/lib/agenda-data-source';
import {
  agendaDomainToAgronomyTask,
  loadAgronomyAdvisory,
  type AgronomyAdvisoryView,
} from '@/lib/agronomy-data-source';

type AdvisoryState = Record<string, AgronomyAdvisoryView | null | undefined>;
type AgendaFilter = 'all' | 'overdue' | 'today' | 'upcoming';

function advisoryLabel(advisory: AgronomyAdvisoryView) {
  if (advisory.suitability === 'avoid') return 'Evitar';
  if (advisory.suitability === 'caution') return 'Precaución';
  if (advisory.suitability === 'good') return 'Favorable';
  return 'Sin criterio';
}

function formatAgendaDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatAgendaDay(value: string) {
  const [year, month, day] = value.split('-');
  return year && month && day ? `${day}/${month}/${year}` : value;
}

function forecastSourceLabel(source: string) {
  if (source.toLowerCase().includes('aemet')) return 'AEMET';
  return source.replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function executionHref(item: AgendaItem) {
  if (!item.fieldId) return null;
  const routeByType: Record<string, string> = {
    treatment: 'tratamiento', irrigation: 'riego', fertilization: 'abono', pruning: 'poda',
    harvest: 'cosecha', harvest_delivery: 'cosecha', work: 'trabajo', observation: 'observacion', other: 'trabajo',
  };
  const slug = routeByType[item.taskKind ?? item.sourceDomainType ?? ''] ?? 'trabajo';
  const params = new URLSearchParams({ fieldId: item.fieldId, source: 'api', plannedEventId: item.id });
  return `/mi-campo/registrar/${slug}?${params.toString()}`;
}

function AdvisoryEvidence({ advisory }: { advisory: AgronomyAdvisoryView }) {
  return <div className="today-advisory-evidence">
    <small>
      Previsión {forecastSourceLabel(advisory.forecastEvidence.source)}
      {advisory.forecastEvidence.precipitationProbabilityPercent !== undefined ? ` · lluvia ${advisory.forecastEvidence.precipitationProbabilityPercent}%` : ''}
      {advisory.forecastEvidence.windMaxKmh !== undefined ? ` · viento ${advisory.forecastEvidence.windMaxKmh} km/h` : ''}
      {advisory.stale ? ' · previsión desactualizada' : ''}
    </small>
    {advisory.radarEvidence ? <small>
      Radar · {advisory.radarEvidence.summary}
      {advisory.radarEvidence.ageMinutes !== undefined ? ` · hace ${advisory.radarEvidence.ageMinutes} min` : ''}
      {!advisory.radarEvidence.fresh ? ' · observación antigua, solo orientativa' : ''}
      {advisory.radarElevated ? ' · aumenta la precaución' : ''}
    </small> : null}
  </div>;
}

function AgendaSection({
  title,
  items,
  advisories,
  workspaceId,
  onChanged,
}: {
  title: string;
  items: AgendaItem[];
  advisories: AdvisoryState;
  workspaceId?: string;
  onChanged: () => void;
}) {
  return <section className="section">
    <div className="section-head"><h2>{title}</h2><span className="subtle">{items.length}</span></div>
    {items.length ? <div className="card feed today-list">
      {items.map((item) => {
        const advisory = advisories[item.id];
        const executeHref = executionHref(item);
        return <div className="feed-row" key={item.id}>
          <div className="feed-copy">
            <strong>{item.title}</strong>
            <small>{item.fieldName ?? 'Sin finca'} · {formatAgendaDate(item.scheduledAt)}{item.status === 'postponed' ? ' · aplazada' : ''}</small>
            {item.notes ? <small>{item.notes}</small> : null}
            {item.weatherSensitive ? <>
              <small>
                {advisory === undefined ? 'Consultando el tiempo para esta tarea…' : advisory === null ? 'No hay información meteorológica disponible para esta tarea.' : `${advisoryLabel(advisory)} · ${advisory.summary}`}
              </small>
              {advisory ? <AdvisoryEvidence advisory={advisory} /> : null}
            </> : null}
            <div className="record-actions">
              {executeHref ? <Link className="primary action-link" href={executeHref}>Registrar realizado →</Link> : null}
              {workspaceId ? <PlannedTaskActions item={item} workspaceId={workspaceId} onChanged={onChanged} /> : null}
            </div>
          </div>
          <span className="pending-pill">{advisory ? advisoryLabel(advisory) : item.priority === 'high' ? 'Prioridad alta' : item.bucket === 'today' ? 'Hoy' : 'Próximo'}</span>
        </div>;
      })}
    </div> : <div className="card"><p>No hay tareas en este bloque.</p></div>}
  </section>;
}

export function TodayAgendaClient() {
  const { apiConfigured, previewEnabled, status, selectedWorkspaceId } = useAuth();
  const [agenda, setAgenda] = useState<AgendaView>(emptyAgenda());
  const [advisories, setAdvisories] = useState<AdvisoryState>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [filter, setFilter] = useState<AgendaFilter>('all');

  const weatherSensitiveItems = useMemo(
    () => [...agenda.overdue, ...agenda.today, ...agenda.upcoming].filter((item) => item.weatherSensitive && item.fieldId),
    [agenda],
  );
  const totalItems = agenda.counts.overdue + agenda.counts.today + agenda.counts.upcoming;
  const visibleSections = useMemo(() => {
    const sections = [
      { key: 'overdue' as const, title: 'Atrasadas', items: agenda.overdue },
      { key: 'today' as const, title: 'Para hoy', items: agenda.today },
      { key: 'upcoming' as const, title: 'Próximos 7 días', items: agenda.upcoming },
    ];
    if (filter === 'all') return sections.filter((section) => section.items.length > 0);
    return sections.filter((section) => section.key === filter);
  }, [agenda, filter]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        if (apiConfigured && status === 'authenticated' && selectedWorkspaceId) {
          const data = await loadApiAgenda(selectedWorkspaceId);
          if (!cancelled) setAgenda(data);
        } else if (previewEnabled) {
          if (!cancelled) setAgenda(emptyAgenda());
        } else if (apiConfigured && status === 'loading') {
          return;
        } else if (!cancelled) {
          setAgenda(emptyAgenda());
          setError(apiConfigured ? 'Inicia sesión para consultar tu agenda.' : 'El servicio de agenda no está disponible en esta instalación.');
        }
      } catch (err) {
        console.error(err);
        if (!cancelled) setError('No se ha podido cargar la agenda.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [apiConfigured, previewEnabled, revision, selectedWorkspaceId, status]);

  useEffect(() => {
    if (!selectedWorkspaceId || status !== 'authenticated' || !weatherSensitiveItems.length) {
      setAdvisories({});
      return;
    }
    const workspaceId = selectedWorkspaceId;
    let cancelled = false;
    async function loadAdvisories() {
      const entries = await Promise.all(weatherSensitiveItems.map(async (item) => {
        if (!item.fieldId) return [item.id, null] as const;
        try {
          const advisory = await loadAgronomyAdvisory({
            workspaceId,
            fieldId: item.fieldId,
            date: item.scheduledAt.slice(0, 10),
            task: agendaDomainToAgronomyTask(item.taskKind ?? item.sourceDomainType),
          });
          return [item.id, advisory] as const;
        } catch (err) {
          console.warn('Agronomic advisory unavailable', item.id, err);
          return [item.id, null] as const;
        }
      }));
      if (!cancelled) setAdvisories(Object.fromEntries(entries));
    }
    void loadAdvisories();
    return () => { cancelled = true; };
  }, [selectedWorkspaceId, status, weatherSensitiveItems]);

  const refresh = () => setRevision((value) => value + 1);

  return <>
    <header className="page-title mi-campo-title"><div><span className="eyebrow dark">MI CAMPO · AGENDA</span><h1>Hoy</h1><p>Lo pendiente, lo de hoy y lo próximo, con el tiempo como ayuda para decidir.</p></div></header>

    <section className="card field-summary campaign-summary"><div className="stats">
      <div className="stat"><b>{agenda.counts.overdue}</b><span>atrasadas</span></div>
      <div className="stat"><b>{agenda.counts.today}</b><span>para hoy</span></div>
      <div className="stat"><b>{agenda.counts.upcoming}</b><span>próximos 7 días</span></div>
      <div className="stat"><b>{agenda.counts.weatherSensitive}</b><span>sensibles al clima</span></div>
    </div></section>

    {!loading && !error ? <section className="section card">
      <div className="section-head"><div><h2>Agenda del {formatAgendaDay(agenda.date)}</h2><small>{totalItems ? `${totalItems} tarea${totalItems === 1 ? '' : 's'} pendiente${totalItems === 1 ? '' : 's'}` : 'Sin tareas pendientes próximas'}</small></div><Link href="/mi-campo/planificar">Planificar nueva</Link></div>
      {totalItems ? <div className="record-actions" aria-label="Filtrar agenda">
        {([
          ['all', 'Todas'],
          ['overdue', `Atrasadas (${agenda.counts.overdue})`],
          ['today', `Hoy (${agenda.counts.today})`],
          ['upcoming', `Próximas (${agenda.counts.upcoming})`],
        ] as Array<[AgendaFilter, string]>).map(([value, label]) => <button key={value} type="button" className={filter === value ? 'primary' : 'secondary-action'} aria-pressed={filter === value} onClick={() => setFilter(value)}>{label}</button>)}
      </div> : null}
    </section> : null}

    {previewEnabled && !apiConfigured ? <section className="card"><strong>Modo demostración</strong><p>Esta vista no carga tu agenda privada. Las tareas reales aparecerán cuando uses Mágina con tu cuenta conectada.</p></section> : null}
    {loading ? <section className="card" aria-busy="true"><p>Cargando agenda…</p></section> : null}
    {error ? <section className="card" role="alert"><strong>Agenda no disponible</strong><p>{error}</p>{status === 'authenticated' ? <button className="secondary-action" type="button" onClick={refresh}>Reintentar</button> : null}</section> : null}
    {!loading && !error && totalItems === 0 ? <section className="section card"><h2>Agenda al día</h2><p>No tienes tareas atrasadas, para hoy ni para los próximos siete días.</p><div className="record-actions"><Link className="primary action-link" href="/mi-campo/planificar">Planificar tarea</Link><Link className="secondary-action action-link" href="/mi-campo/registrar">Registrar trabajo</Link></div></section> : null}
    {!loading && !error && totalItems > 0 ? <>
      {visibleSections.map((section) => <AgendaSection key={section.key} title={section.title} items={section.items} advisories={advisories} workspaceId={status === 'authenticated' ? selectedWorkspaceId ?? undefined : undefined} onChanged={refresh} />)}
      <section className="card"><strong>El tiempo ayuda; tú decides</strong><p>{agenda.rule}</p><small>La previsión mira hacia delante y el radar aporta observaciones recientes. No mostramos una hora exacta de llegada de la lluvia cuando los datos no permiten calcularla con fiabilidad.</small></section>
    </> : null}

    <section className="territory-banner compact-banner"><div><span className="eyebrow">TU TRABAJO, BIEN ORGANIZADO</span><h2>Planifica primero y registra la tarea cuando realmente la hayas hecho.</h2></div><Link href="/mi-campo/planificar">Planificar tarea</Link></section>
  </>;
}
