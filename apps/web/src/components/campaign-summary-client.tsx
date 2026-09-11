'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import {
  getPreviewCampaigns,
  getPreviewCampaignSummary,
  loadApiCampaigns,
  loadApiCampaignSummary,
  type CampaignListItem,
  type CampaignSummaryView,
} from '@/lib/campaign-data-source';

function money(value: number) {
  return `${value.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €`;
}

function number(value: number) {
  return value.toLocaleString('es-ES', { maximumFractionDigits: 2 });
}

export function CampaignSummaryClient() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [campaigns, setCampaigns] = useState<CampaignListItem[]>([]);
  const [campaignId, setCampaignId] = useState<string>('');
  const [summary, setSummary] = useState<CampaignSummaryView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function loadList() {
      setLoading(true);
      setError(null);
      try {
        const items = apiConfigured && status === 'authenticated' && selectedWorkspaceId
          ? await loadApiCampaigns(selectedWorkspaceId)
          : getPreviewCampaigns();
        if (cancelled) return;
        setCampaigns(items);
        const active = items.find((item) => item.status === 'active') ?? items[0];
        setCampaignId(active?.id ?? '');
      } catch (err) {
        console.error(err);
        if (!cancelled) setError('No se han podido cargar las campañas.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void loadList();
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  useEffect(() => {
    let cancelled = false;
    async function loadSummary() {
      if (!campaignId) {
        setSummary(null);
        return;
      }
      setLoading(true);
      try {
        const next = apiConfigured && status === 'authenticated' && selectedWorkspaceId
          ? await loadApiCampaignSummary(campaignId, selectedWorkspaceId)
          : getPreviewCampaignSummary(campaignId);
        if (!cancelled) setSummary(next);
      } catch (err) {
        console.error(err);
        if (!cancelled) setError('No se ha podido calcular el resumen de campaña.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void loadSummary();
    return () => { cancelled = true; };
  }, [apiConfigured, campaignId, selectedWorkspaceId, status]);

  const selected = useMemo(() => campaigns.find((item) => item.id === campaignId), [campaignId, campaigns]);

  return <>
    <header className="page-title mi-campo-title">
      <div><span className="eyebrow dark">MI CAMPO · CAMPAÑA</span><h1>Campaña</h1><p>Producción, costes, liquidaciones y cobros de todas tus fincas.</p></div>
    </header>

    <section className="card">
      <label><strong>Campaña</strong><select value={campaignId} onChange={(event) => setCampaignId(event.target.value)}>
        {campaigns.map((campaign) => <option key={campaign.id} value={campaign.id}>{campaign.name} · {campaign.status}</option>)}
      </select></label>
      {selected ? <p className="subtle">{selected.startDate}{selected.endDate ? ` → ${selected.endDate}` : ''}</p> : null}
    </section>

    {error ? <p className="form-error" role="alert">{error}</p> : null}
    {loading ? <section className="card"><p>Calculando campaña…</p></section> : null}

    {!loading && !summary ? <section className="card"><h3>Sin campaña disponible</h3><p>Cuando exista una campaña activa aparecerá aquí su resumen.</p></section> : null}

    {summary ? <>
      <section className="section"><div className="quick-grid">
        <article className="card quick premium-quick"><div><strong>{Math.round(summary.deliveredKg).toLocaleString('es-ES')} kg</strong><small>aceituna entregada</small></div></article>
        <article className="card quick premium-quick"><div><strong>{summary.weightedYieldPercent !== undefined ? `${number(summary.weightedYieldPercent)} %` : '—'}</strong><small>rendimiento ponderado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{money(summary.totalCostEur)}</strong><small>costes registrados</small></div></article>
        <article className="card quick premium-quick"><div><strong>{money(summary.accruedIncomeEur)}</strong><small>liquidado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{money(summary.collectedIncomeEur)}</strong><small>cobrado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{money(summary.pendingCollectionEur)}</strong><small>pendiente de cobro</small></div></article>
        <article className="card quick premium-quick"><div><strong>{money(summary.accruedMarginEur)}</strong><small>margen devengado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{money(summary.cashMarginEur)}</strong><small>cobrado − costes registrados</small></div></article>
        <article className="card quick premium-quick"><div><strong>{summary.costPerDeliveredKgEur !== undefined ? `${number(summary.costPerDeliveredKgEur)} €/kg` : '—'}</strong><small>coste registrado por kg</small></div></article>
      </div></section>

      <section className="card register-principle"><div><strong>Liquidado, cobrado y caja no son lo mismo</strong><small>Margen devengado = liquidado atribuible − costes registrados. “Cobrado − costes registrados” todavía no es flujo de caja real porque los pagos efectivos de gastos se modelarán por separado.</small></div></section>

      <section className="section">
        <div className="section-head"><h2>Por finca</h2><span className="subtle">{summary.fieldCount} con actividad</span></div>
        {summary.fields.length ? <div className="card feed today-list">
          {summary.fields.map((field) => <div className="feed-row" key={field.fieldId}><div className="feed-copy"><strong>{field.fieldName}</strong><small>{Math.round(field.deliveredKg).toLocaleString('es-ES')} kg · coste {money(field.totalCostEur)} · liquidado {money(field.accruedIncomeEur)} · cobrado {money(field.collectedIncomeEur)} · pendiente {money(field.pendingCollectionEur)} · margen devengado {money(field.accruedMarginEur)}</small></div></div>)}
        </div> : <section className="card"><p>Aún no hay datos económicos o de cosecha en esta campaña.</p></section>}
      </section>

      <section className="card"><strong>Lectura de campaña</strong><p>{summary.deliveryCount} entregas · {summary.settlementCount} liquidaciones · {Math.round(summary.pendingResultKg).toLocaleString('es-ES')} kg todavía sin resultado de rendimiento.</p><small>{summary.attributionStatus}</small></section>
    </> : null}

    <section className="territory-banner compact-banner"><div><span className="eyebrow">CAMPAÑA COMPLETA</span><h2>Ver cada finca y el conjunto sin duplicar cálculos.</h2></div><Link href="/mi-campo">Mi Campo</Link></section>
  </>;
}
