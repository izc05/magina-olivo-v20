'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { ArrowIcon, PlusIcon } from '@/components/icons';
import { useAuth } from '@/components/auth-provider';
import { loadApiProfessionalSummary, loadPreviewProfessionalSummary, type ProfessionalSummaryView } from '@/lib/professional-data-source';

function money(value: number) {
  return value.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
}

const empty: ProfessionalSummaryView = {
  workCount: 0,
  customerCount: 0,
  directCostEur: 0,
  chargedEur: 0,
  collectedEur: 0,
  pendingEur: 0,
  accruedMarginEur: 0,
  collectedLessDirectCostsEur: 0,
  customers: [],
  recentWork: [],
};

export function ProfessionalDashboard() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [data, setData] = useState<ProfessionalSummaryView>(empty);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const next = apiConfigured && status === 'authenticated' && selectedWorkspaceId
          ? await loadApiProfessionalSummary(selectedWorkspaceId)
          : !apiConfigured
            ? loadPreviewProfessionalSummary()
            : empty;
        if (!cancelled) setData(next);
      } catch (err) {
        console.error('Unable to load professional summary', err);
        if (!cancelled) setError('No se ha podido cargar la actividad profesional.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    const refresh = () => { if (!apiConfigured) void load(); };
    window.addEventListener('magina:prototype-data-changed', refresh);
    return () => {
      cancelled = true;
      window.removeEventListener('magina:prototype-data-changed', refresh);
    };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const pendingCustomers = useMemo(
    () => data.customers.filter((customer) => customer.pendingEur > 0).sort((a, b) => b.pendingEur - a.pendingEur),
    [data.customers],
  );

  const customerHref = (id: string) => `/mi-campo/profesional/cliente?id=${encodeURIComponent(id)}`;

  return <>
    <header className="page-title">
      <div>
        <span className="eyebrow dark">MI CAMPO · ACTIVIDAD PROFESIONAL</span>
        <h1>Trabajos para terceros</h1>
        <p>Clientes, trabajos, facturas, costes, márgenes y cobros pendientes, separados de tu explotación agrícola.</p>
      </div>
    </header>

    <section className="card field-summary campaign-summary"><div className="stats">
      <div className="stat"><b>{loading ? '…' : data.workCount}</b><span>trabajos</span></div>
      <div className="stat"><b>{loading ? '…' : data.customerCount}</b><span>clientes</span></div>
      <div className="stat"><b>{loading ? '…' : money(data.pendingEur)}</b><span>pendiente de cobrar</span></div>
    </div></section>

    <section className="section">
      <div className="section-head"><h2>Resumen profesional</h2><div className="action-row"><Link href="/mi-campo/profesional/facturas/nueva" className="detail-link">Nueva factura</Link><Link href="/mi-campo/profesional/cobrar" className="detail-link">Registrar cobro</Link><Link href="/mi-campo/registrar/trabajo" className="detail-link"><PlusIcon /> Registrar trabajo</Link></div></div>
      <div className="quick-grid">
        <div className="card quick"><div><strong>{money(data.chargedEur)}</strong><small>facturado / devengado</small></div></div>
        <div className="card quick"><div><strong>{money(data.collectedEur)}</strong><small>cobrado</small></div></div>
        <div className="card quick"><div><strong>{money(data.pendingEur)}</strong><small>pendiente</small></div></div>
        <div className="card quick"><div><strong>{money(data.directCostEur)}</strong><small>coste directo</small></div></div>
        <div className="card quick"><div><strong>{money(data.accruedMarginEur)}</strong><small>margen devengado</small></div></div>
      </div>
      <p className="form-help">Margen devengado = importe facturado − coste directo. El cobro se registra aparte y mantiene su propio historial.</p>
    </section>

    {pendingCustomers.length ? <section className="section">
      <div className="section-head"><h2>Clientes con cobros pendientes</h2><span className="subtle">{pendingCustomers.length}</span></div>
      <div className="activity-list">{pendingCustomers.map((customer) => <article className="card activity-item" key={customer.id}><div><h3>{customer.name}</h3><p>{customer.workCount} trabajo{customer.workCount === 1 ? '' : 's'} · facturado {money(customer.chargedEur)}</p><small>Margen devengado {money(customer.accruedMarginEur)}</small></div><div><strong>{money(customer.pendingEur)}</strong><small>pendiente</small><Link className="detail-link" href={customerHref(customer.id)}>Ver cliente</Link></div></article>)}</div>
    </section> : null}

    <section className="section">
      <div className="section-head"><h2>Rentabilidad por cliente</h2><span className="subtle">{data.customers.length}</span></div>
      {!loading && data.customers.length === 0 ? <section className="card"><h3>Sin clientes todavía</h3><p>Los clientes aparecerán aquí cuando registres trabajos para terceros.</p></section> : null}
      <div className="activity-list">{data.customers.map((customer) => <article className="card activity-item" key={customer.id}><div><h3>{customer.name}</h3><p>{customer.workCount} trabajo{customer.workCount === 1 ? '' : 's'} · coste {money(customer.directCostEur)}</p></div><div><strong>{money(customer.accruedMarginEur)}</strong><small>margen · {money(customer.collectedEur)} cobrado</small><Link className="detail-link" href={customerHref(customer.id)}>Ficha</Link></div></article>)}</div>
    </section>

    <section className="section">
      <div className="section-head"><h2>Trabajos recientes</h2><Link href="/mi-campo/registrar/trabajo" className="detail-link"><PlusIcon /> Registrar trabajo</Link></div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {!loading && data.recentWork.length === 0 ? <section className="card"><h3>Aún no hay trabajos para terceros</h3><p>Cuando registres un trabajo para un cliente aparecerá aquí con su coste, margen y estado de cobro.</p></section> : null}
      <div className="activity-list">{data.recentWork.map((work) => <article className="card activity-item" key={work.id}><div><small>{work.date}</small><h3>{work.title}</h3><p>{[work.customerName, work.siteName].filter(Boolean).join(' · ') || 'Cliente'}</p><small>Coste {money(work.directCostEur)} · margen {money(work.accruedMarginEur)}</small></div><div><strong>{money(work.chargeEur)}</strong><small>{work.pendingEur > 0 ? `${money(work.pendingEur)} pendiente` : 'Cobrado'}</small>{work.pendingEur > 0 ? <Link className="detail-link" href={`/mi-campo/profesional/cobrar?workId=${encodeURIComponent(work.id)}`}>Registrar cobro</Link> : null}</div></article>)}</div>
    </section>

    <section className="territory-banner compact-banner"><div><span className="eyebrow">TRABAJO PROFESIONAL</span><h2>Cliente → trabajo → factura → cobro, sin mezclarlo con la cosecha.</h2></div><Link href="/mi-campo/profesional/facturas/nueva">Facturar <ArrowIcon /></Link></section>
  </>;
}