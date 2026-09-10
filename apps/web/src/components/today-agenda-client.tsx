'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { emptyAgenda, loadApiAgenda, type AgendaItem, type AgendaView } from '@/lib/agenda-data-source';
import {
  agendaDomainToAgronomyTask,
  loadAgronomyAdvisory,
  type AgronomyAdvisoryView,
} from '@/lib/agronomy-data-source';

type AdvisoryState = Record<string, AgronomyAdvisoryView | null | undefined>;

function advisoryLabel(advisory: AgronomyAdvisoryView) {
  if (advisory.suitability === 'avoid') return 'Evitar';
  if (advisory.suitability === 'caution') return 'Precaución';
  if (advisory.suitability === 'good') return 'Favorable';
  return 'Sin criterio';
}

function executionHref(item: AgendaItem) {
  if (!item.fieldId) return null;
  const routeByType: Record<string, string> = {
    treatment: 'tratamiento', irrigation: 'riego', fertilization: 'abono', pruning: 'poda',
    harvest: 'cosecha', harvest_delivery: 'cosecha', work: 'trabajo', observation: 'observacion', other: 'trabajo',
  };
  const slug = routeByType[item.sourceDomainType ?? ''] ?? 'trabajo';
  const params = new URLSearchParams({ fieldId: item.fieldId, source: 'api', plannedEventId: item.id });
  return `/mi-campo/registrar/${slug}?${params.toString()}`;
}

function AdvisoryEvidence({ advisory }: { advisory: AgronomyAdvisoryView }) {
  return <div className="today-advisory-evidence">
    <small>
      Previsión · {advisory.forecastEvidence.source}
      {advisory.forecastEvidence.precipitationProbabilityPercent !== undefined ? ` · lluvia ${advisory.forecastEvidence.precipitationProbabilityPercent}%` : ''}
      {advisory.forecastEvidence.windMaxKmh !== undefined ? ` · viento ${advisory.forecastEvidence.windMaxKmh} km/h` : ''}
      {advisory.stale ? ' · datos antiguos' : ''}
    </small>
    {advisory.radarEvidence ? <small>
      Radar observado · {advisory.radarEvidence.summary}
      {advisory.radarEvidence.ageMinutes !== undefined ? ` · hace ${advisory.radarEvidence.ageMinutes} min` : ''}
      {!advisory.radarEvidence.fresh ? ' · no modifica la recomendación' : ''}
      {advisory.radarElevated ? ' · elevó la severidad' : ''}
    </small> : null}
  </div>;
}

function AgendaSection({ title, items, advisories }: { title: string; items: AgendaItem[]; advisories: AdvisoryState }) {
  return <section className="section">
    <div className="section-head"><h2>{title}</h2><span className="subtle">{items.length}</span></div>
    {items.length ? <div className="card feed today-list">
      {items.map((item) => {
        const advisory = advisories[item.id];
        const executeHref = executionHref(item);
        return <div className="feed-row" key={item.id}>
          <div className="feed-copy">
            <strong>{item.title}</strong>
            <small>{item.fieldName ?? 'Sin finca'} · {item.scheduledAt.slice(0, 16).replace('T', ' ')}</small>
            {item.weatherSensitive ? <>
              <small>
                {advisory === undefined ? 'Consultando contexto meteorológico…' : advisory === null ? 'Contexto meteorológico no disponible.' : `${advisoryLabel(advisory)} · ${advisory.summary}`}
              </small>
              {advisory ? <AdvisoryEvidence advisory={advisory} /> : null}
            </> : null}
            {executeHref ? <small><Link href={executeHref}>Registrar realizado →</Link></small> : null}
          </div>
          <span className="pending-pill">{advisory ? advisoryLabel(advisory) : item.priority === 'high' ? 'Prioridad' : item.bucket === 'today' ? 'Hoy' : 'Próximo'}</span>
        </div>;
      })}
    </div> : <section className="card"><p>No hay tareas en este bloque.</p></section>}
  </section>;
}

export function TodayAgendaClient() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [agenda, setAgenda] = useState<AgendaView>(emptyAgenda());
  const [advisories, setAdvisories] = useState<AdvisoryState>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const weatherSensitiveItems = useMemo(
    () => [...agenda.overdue, ...agenda.today, ...agenda.upcoming].filter((item) => item.weatherSensitive && item.fieldId),
    [agenda],
  );

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        if (apiConfigured && status === 'authenticated' && selectedWorkspaceId) {
          const data = await loadApiAgenda(selectedWorkspaceId);
          if (!cancelled) setAgenda(data);
        } else if (!cancelled) {
          setAgenda(emptyAgenda());
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
  }, [apiConfigured, selectedWorkspaceId, status]);

  useEffect(() => {
    if (!selectedWorkspaceId || !weatherSensitiveItems.length) {
      setAdvisories({});
      return;
    }
    let cancelled = false;
    async function loadAdvisories() {
      const entries = await Promise.all(weatherSensitiveItems.map(async (item) => {
        try {
          const advisory = await loadAgronomyAdvisory({
            workspaceId: selectedWorkspaceId,
            fieldId: item.fieldId!,
            date: item.scheduledAt.slice(0, 10),
            task: agendaDomainToAgronomyTask(item.sourceDomainType),
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
  }, [selectedWorkspaceId, weatherSensitiveItems]);

  return <>
    <header className="page-title mi-campo-title"><div><span className="eyebrow dark">MI CAMPO · AGENDA</span><h1>Hoy</h1><p>Lo pendiente, lo de hoy y lo próximo, con previsión y radar cuando aportan contexto.</p></div></header>

    <section className="card field-summary campaign-summary"><div className="stats">
      <div className="stat"><b>{agenda.counts.overdue}</b><span>atrasadas</span></div>
      <div className="stat"><b>{agenda.counts.today}</b><span>para hoy</span></div>
      <div className="stat"><b>{agenda.counts.weatherSensitive}</b><span>sensibles al clima</span></div>
    </div></section>

    {loading ? <section className="card"><p>Cargando agenda…</p></section> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}
    {!loading ? <>
      <AgendaSection title="Atrasadas" items={agenda.overdue} advisories={advisories} />
      <AgendaSection title="Para hoy" items={agenda.today} advisories={advisories} />
      <AgendaSection title="Próximos 7 días" items={agenda.upcoming} advisories={advisories} />
      <section className="card"><strong>Regla de Mágina</strong><p>{agenda.rule}</p><small>La previsión estima condiciones futuras; el radar muestra reflectividad observada. Mágina no deduce una hora de llegada de lluvia a partir de una sola imagen radar.</small></section>
    </> : null}

    <section className="territory-banner compact-banner"><div><span className="eyebrow">UNA AGENDA, NO OTRA LIBRETA</span><h2>Planifica aquí, registra cuando realmente lo hagas.</h2></div><Link href="/mi-campo/registrar">Registrar</Link></section>
  </>;
}