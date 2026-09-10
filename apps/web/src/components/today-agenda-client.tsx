'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { emptyAgenda, loadApiAgenda, type AgendaItem, type AgendaView } from '@/lib/agenda-data-source';

function AgendaSection({ title, items }: { title: string; items: AgendaItem[] }) {
  return <section className="section">
    <div className="section-head"><h2>{title}</h2><span className="subtle">{items.length}</span></div>
    {items.length ? <div className="card feed today-list">
      {items.map((item) => <div className="feed-row" key={item.id}><div className="feed-copy"><strong>{item.title}</strong><small>{item.fieldName ?? 'Sin finca'} · {item.scheduledAt.slice(0, 16).replace('T', ' ')}{item.weatherSensitive ? ' · requiere contexto meteorológico' : ''}</small></div><span className="pending-pill">{item.priority === 'high' ? 'Prioridad' : item.bucket === 'today' ? 'Hoy' : 'Próximo'}</span></div>)}
    </div> : <section className="card"><p>No hay tareas en este bloque.</p></section>}
  </section>;
}

export function TodayAgendaClient() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [agenda, setAgenda] = useState<AgendaView>(emptyAgenda());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  return <>
    <header className="page-title mi-campo-title"><div><span className="eyebrow dark">MI CAMPO · AGENDA</span><h1>Hoy</h1><p>Lo pendiente, lo de hoy y lo próximo, con contexto de finca.</p></div></header>

    <section className="card field-summary campaign-summary"><div className="stats">
      <div className="stat"><b>{agenda.counts.overdue}</b><span>atrasadas</span></div>
      <div className="stat"><b>{agenda.counts.today}</b><span>para hoy</span></div>
      <div className="stat"><b>{agenda.counts.weatherSensitive}</b><span>sensibles al clima</span></div>
    </div></section>

    {loading ? <section className="card"><p>Cargando agenda…</p></section> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}
    {!loading ? <>
      <AgendaSection title="Atrasadas" items={agenda.overdue} />
      <AgendaSection title="Para hoy" items={agenda.today} />
      <AgendaSection title="Próximos 7 días" items={agenda.upcoming} />
      <section className="card"><strong>Regla de Mágina</strong><p>{agenda.rule}</p></section>
    </> : null}

    <section className="territory-banner compact-banner"><div><span className="eyebrow">UNA AGENDA, NO OTRA LIBRETA</span><h2>Los seguimientos nacen de los trabajos y registros existentes.</h2></div><Link href="/mi-campo/registrar">Registrar</Link></section>
  </>;
}
