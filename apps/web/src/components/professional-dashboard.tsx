'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
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
  marginEur: 0,
  cashMarginEur: 0,
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

  return <>
    <header className="page-title">
      <div>
        <span className="eyebrow dark">MI CAMPO · ACTIVIDAD PROFESIONAL</span>
        <h1>Trabajos para terceros</h1>
        <p>Qué has hecho, cuánto te ha costado, cuánto has cobrado y qué queda pendiente.</p>
      </div>
    </header>

    <section className="card field-summary campaign-summary">
      <div className="stats">
        <div className="stat"><b>{loading ? '…' : data.workCount}</b><span>trabajos</span></div>
        <div className="stat"><b>{loading ? '…' : data.customerCount}</b><span>clientes</span></div>
        <div className="stat"><b>{loading ? '…' : money(data.pendingEur)}</b><span>pendiente de cobrar</span></div>
      </div>
    </section>

    <section className="section">
      <div className="section-head"><h2>Resumen económico</h2><span /></div>
      <div className="quick-grid">
        <div className="card quick"><div><strong>{money(data.directCostEur)}</strong><small>Coste directo</small></div></div>
        <div className="card quick"><div><strong>{money(data.chargedEur)}</strong><small>Importe a cobrar</small></div></div>
        <div className="card quick"><div><strong>{money(data.collectedEur)}</strong><small>Cobrado</small></div></div>
        <div className="card quick"><div><strong>{money(data.marginEur)}</strong><small>Margen devengado</small></div></div>
      </div>
      <p className="form-help">Margen devengado = importe a cobrar − coste directo. No es lo mismo que caja: la caja depende de lo realmente cobrado.</p>
    </section>

    <section className="section">
      <div className="section-head"><h2>Trabajos recientes</h2><Link href="/mi-campo/registrar/trabajo" className="detail-link"><PlusIcon /> Registrar trabajo</Link></div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {!loading && data.recentWork.length === 0 ? <section className="card"><h3>Aún no hay trabajos para terceros</h3><p>Cuando registres un trabajo para un cliente aparecerá aquí con su coste y estado de cobro.</p></section> : null}
      <div className="activity-list">
        {data.recentWork.map((work) => <article className="card activity-item" key={work.id}>
          <div>
            <small>{work.date}</small>
            <h3>{work.title}</h3>
            <p>{[work.customerName, work.siteName].filter(Boolean).join(' · ') || 'Cliente'}</p>
          </div>
          <div>
            <strong>{money(work.chargeEur)}</strong>
            <small>{work.pendingEur > 0 ? `${money(work.pendingEur)} pendiente` : 'Cobrado'}</small>
          </div>
        </article>)}
      </div>
    </section>

    <section className="territory-banner compact-banner">
      <div><span className="eyebrow">TRABAJO PROFESIONAL</span><h2>Una sola ficha para trabajo, coste y cobro.</h2></div>
      <Link href="/mi-campo/registrar/trabajo">Registrar <ArrowIcon /></Link>
    </section>
  </>;
}
