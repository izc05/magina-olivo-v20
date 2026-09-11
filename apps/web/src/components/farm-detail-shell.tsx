'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { PlusIcon, SproutIcon } from '@/components/icons';
import { useAuth } from '@/components/auth-provider';
import { getPreviewFarms, loadWorkspaceFarms, type FarmListItem } from '@/lib/farm-data-source';
import { getLocalFarmDerivedView, type FarmDerivedView } from '@/lib/farm-local-view-data';
import {
  emptyFarmDetailData,
  loadApiFarmDetailData,
  loadPreviewFarmDetailData,
  type FarmDetailData,
} from '@/lib/farm-detail-data-source';

const sections = ['Resumen', 'Actividad', 'Cosecha', 'Datos', 'Documentos'] as const;
type Section = (typeof sections)[number];

function formatMoney(value: number) {
  return `${value.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €`;
}

function formatNumber(value: number) {
  return value.toLocaleString('es-ES', { maximumFractionDigits: 2 });
}

function emptyDerived(): FarmDerivedView {
  return {
    workCount: 0,
    deliveryCount: 0,
    deliveredKg: 0,
    totalCostEur: 0,
    totalIncomeEur: 0,
    marginEur: 0,
    recentActivity: [],
  };
}

function sourceLabel(source?: string) {
  if (source === 'catastro') return 'Catastro';
  if (source === 'sigpac') return 'SIGPAC';
  if (source === 'own-boundary') return 'Geometría propia';
  if (source === 'manual') return 'Manual';
  return source ?? 'Referencia';
}

export function FarmDetailShell() {
  const params = useSearchParams();
  const id = params.get('id');
  const source = params.get('source');
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [farm, setFarm] = useState<FarmListItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeSection, setActiveSection] = useState<Section>('Resumen');
  const [derived, setDerived] = useState<FarmDerivedView>(emptyDerived());
  const [detail, setDetail] = useState<FarmDetailData>(emptyFarmDetailData());
  const [detailError, setDetailError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setDetailError(null);
      try {
        if (!id) {
          if (!cancelled) setFarm(null);
          return;
        }

        if (source === 'api' && apiConfigured && status === 'authenticated' && selectedWorkspaceId) {
          const [farms, remoteDetail] = await Promise.all([
            loadWorkspaceFarms(selectedWorkspaceId),
            loadApiFarmDetailData(id, selectedWorkspaceId),
          ]);
          if (!cancelled) {
            const matched = farms.find((item) => item.id === id) ?? null;
            const workItems = remoteDetail.activity.filter((item) => item.domainType === 'work');
            setFarm(matched);
            setDetail(remoteDetail);
            setDerived({
              ...emptyDerived(),
              workCount: workItems.length,
              deliveryCount: remoteDetail.harvest.deliveries.length,
              deliveredKg: remoteDetail.economics.deliveredKg,
              weightedYieldPercent: remoteDetail.harvest.weightedYieldPercent,
              estimatedOilKg: remoteDetail.harvest.weightedYieldPercent !== undefined
                ? remoteDetail.economics.deliveredKg * (remoteDetail.harvest.weightedYieldPercent / 100)
                : undefined,
              totalCostEur: remoteDetail.economics.totalCostEur,
              totalIncomeEur: remoteDetail.economics.accruedIncomeEur,
              marginEur: remoteDetail.economics.accruedMarginEur,
              recentActivity: remoteDetail.activity.map((item) => ({
                id: item.id,
                date: item.date,
                title: item.title,
                summary: item.summary,
                kind: item.domainType === 'harvest_delivery' || item.domainType === 'harvest_result' ? 'harvest' : 'work',
              })),
            });
          }
        } else {
          const farms = getPreviewFarms();
          if (!cancelled) {
            setFarm(farms.find((item) => item.id === id && (!source || item.source === source)) ?? null);
            setDerived(getLocalFarmDerivedView(id));
            setDetail(loadPreviewFarmDetailData(id, source));
          }
        }
      } catch (error) {
        console.error('Unable to load finca detail', error);
        if (!cancelled) {
          setDetailError('No se han podido cargar todos los datos de la finca.');
          setFarm(null);
          setDetail(emptyFarmDetailData());
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void load();
    const refresh = () => { if (source !== 'api') void load(); };
    window.addEventListener('magina:prototype-data-changed', refresh);
    return () => {
      cancelled = true;
      window.removeEventListener('magina:prototype-data-changed', refresh);
    };
  }, [apiConfigured, id, selectedWorkspaceId, source, status]);

  const registerHref = useMemo(() => {
    if (!id) return '/mi-campo/registrar';
    const query = new URLSearchParams({ fieldId: id });
    if (source) query.set('source', source);
    return `/mi-campo/registrar?${query.toString()}`;
  }, [id, source]);

  if (loading) return <section className="card"><p>Cargando finca…</p></section>;
  if (!farm) return <section className="card"><h1>Finca no encontrada</h1><p>{detailError ?? 'La finca no está disponible en esta fuente de datos.'}</p><Link href="/mi-campo" className="secondary-action action-link">Volver a Mi Campo</Link></section>;

  const effectiveArea = detail.data.areaHa ?? farm.areaHa;
  const isApi = source === 'api';
  const deliveredKg = isApi ? detail.economics.deliveredKg : detail.harvest.totalKg || derived.deliveredKg;
  const totalCostEur = isApi ? detail.economics.totalCostEur : derived.totalCostEur;
  const accruedIncomeEur = isApi ? detail.economics.accruedIncomeEur : derived.totalIncomeEur;
  const collectedIncomeEur = isApi ? detail.economics.collectedIncomeEur : detail.harvest.collectedEur;
  const pendingCollectionEur = isApi ? detail.economics.pendingCollectionEur : detail.harvest.pendingEur;
  const accruedMarginEur = isApi ? detail.economics.accruedMarginEur : derived.marginEur;
  const costPerKg = isApi
    ? detail.economics.costPerDeliveredKgEur
    : deliveredKg > 0 ? totalCostEur / deliveredKg : undefined;

  return <>
    <header className="page-title mi-campo-title">
      <div className="title-mark"><SproutIcon /></div>
      <div><span className="eyebrow dark">MI CAMPO · FINCA</span><h1>{farm.name}</h1><p>{farm.municipality ?? 'Municipio pendiente'}{farm.oliveTrees ? ` · ${farm.oliveTrees} olivas` : ''}{effectiveArea ? ` · ${formatNumber(effectiveArea)} ha` : ''}</p></div>
    </header>

    <section className="card field-summary campaign-summary"><div className="stats">
      <div className="stat"><b>{farm.oliveTrees ?? '—'}</b><span>olivas</span></div>
      <div className="stat"><b>{effectiveArea !== undefined ? formatNumber(effectiveArea) : '—'}</b><span>hectáreas</span></div>
      <div className="stat"><b>{farm.waterRegime ?? '—'}</b><span>régimen</span></div>
    </div></section>

    <nav className="section farm-detail-tabs" aria-label="Secciones de la finca">
      <div className="quick-grid">
        {sections.map((section) => <button type="button" key={section} onClick={() => setActiveSection(section)} className={`card quick premium-quick${activeSection === section ? ' active' : ''}`} aria-pressed={activeSection === section}><strong>{section}</strong></button>)}
      </div>
    </nav>

    {activeSection === 'Resumen' ? <section className="section">
      <div className="section-head"><h2>Resumen</h2><Link href={registerHref} className="detail-link"><PlusIcon /> Registrar</Link></div>

      <h3>Actividad y cosecha</h3>
      <div className="quick-grid">
        <article className="card quick premium-quick"><div><strong>{derived.workCount}</strong><small>trabajos registrados</small></div></article>
        <article className="card quick premium-quick"><div><strong>{Math.round(deliveredKg).toLocaleString('es-ES')} kg</strong><small>{detail.harvest.deliveries.length || derived.deliveryCount} entregas de cosecha</small></div></article>
        <article className="card quick premium-quick"><div><strong>{(detail.harvest.weightedYieldPercent ?? derived.weightedYieldPercent) !== undefined ? `${formatNumber(detail.harvest.weightedYieldPercent ?? derived.weightedYieldPercent!)} %` : '—'}</strong><small>rendimiento ponderado</small></div></article>
      </div>

      <div className="section-head"><h3>Economía de la campaña</h3><Link href="/mi-campo/campana" className="detail-link">Ver campaña</Link></div>
      <div className="quick-grid">
        <article className="card quick premium-quick"><div><strong>{formatMoney(totalCostEur)}</strong><small>costes registrados</small></div></article>
        <article className="card quick premium-quick"><div><strong>{costPerKg !== undefined ? `${formatNumber(costPerKg)} €/kg` : '—'}</strong><small>coste por kg entregado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(accruedIncomeEur)}</strong><small>liquidado atribuible</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(collectedIncomeEur)}</strong><small>cobrado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(pendingCollectionEur)}</strong><small>pendiente de cobro</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(accruedMarginEur)}</strong><small>margen devengado</small></div></article>
      </div>
      {isApi ? <p className="subtle">Margen devengado = liquidado atribuible − costes registrados. El flujo de caja real se mostrará cuando también modelemos pagos efectivos de gastos.</p> : <p className="subtle">Vista de demostración: los importes proceden del almacenamiento local de la preview.</p>}
    </section> : null}

    {activeSection === 'Actividad' ? <section className="section">
      <div className="section-head"><h2>Actividad</h2><Link href={registerHref} className="detail-link"><PlusIcon /> Registrar trabajo</Link></div>
      {derived.recentActivity.length ? <div className="card feed today-list">
        {derived.recentActivity.map((item) => <div className="feed-row" key={item.id}><div className="feed-copy"><strong>{item.title}</strong><small>{item.date}{item.summary ? ` · ${item.summary}` : ''}</small></div>{item.amountEur !== undefined ? <span className="pending-pill">{formatMoney(item.amountEur)}</span> : null}</div>)}
      </div> : <section className="card"><h3>Sin actividad todavía</h3><p>Los trabajos, cosechas y movimientos económicos aparecerán aquí ordenados por fecha.</p></section>}
    </section> : null}

    {activeSection === 'Cosecha' ? <section className="section">
      <div className="section-head"><h2>Cosecha</h2><Link href={registerHref} className="detail-link"><PlusIcon /> Registrar entrega</Link></div>
      <div className="quick-grid">
        <article className="card quick premium-quick"><div><strong>{Math.round(detail.harvest.totalKg).toLocaleString('es-ES')} kg</strong><small>aceituna entregada</small></div></article>
        <article className="card quick premium-quick"><div><strong>{detail.harvest.weightedYieldPercent !== undefined ? `${formatNumber(detail.harvest.weightedYieldPercent)} %` : '—'}</strong><small>rendimiento ponderado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(detail.harvest.accruedEur)}</strong><small>liquidado atribuible</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(detail.harvest.collectedEur)}</strong><small>cobrado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(detail.harvest.pendingEur)}</strong><small>pendiente de cobro</small></div></article>
        <article className="card quick premium-quick"><div><strong>{detail.harvest.pendingResults}</strong><small>resultados pendientes</small></div></article>
      </div>
      {detail.harvest.deliveries.length ? <div className="card feed today-list">
        {detail.harvest.deliveries.map((delivery) => <div className="feed-row" key={delivery.id}><div className="feed-copy"><strong>{Math.round(delivery.kg).toLocaleString('es-ES')} kg{delivery.destination ? ` · ${delivery.destination}` : ''}</strong><small>{delivery.date}{delivery.ticketNumber ? ` · Albarán ${delivery.ticketNumber}` : ''}{delivery.yieldPercent !== undefined ? ` · Rend. ${formatNumber(delivery.yieldPercent)} %` : ' · Rendimiento pendiente'}</small></div></div>)}
      </div> : <section className="card"><h3>Sin entregas todavía</h3><p>Las entregas pueden repartir kilos entre una o varias fincas y recibir el rendimiento días después.</p></section>}

      {detail.harvest.settlements.length ? <>
        <div className="section-head"><h3>Liquidaciones</h3><span className="subtle">{detail.harvest.settlements.length}</span></div>
        <div className="card feed today-list">
          {detail.harvest.settlements.map((settlement) => <div className="feed-row" key={settlement.id}><div className="feed-copy"><strong>{formatMoney(settlement.netEur)}{settlement.counterparty ? ` · ${settlement.counterparty}` : ''}</strong><small>{settlement.date}{settlement.settlementNumber ? ` · Liquidación ${settlement.settlementNumber}` : ''} · {Math.round(settlement.fieldKg).toLocaleString('es-ES')} kg atribuidos · cobrado {formatMoney(settlement.collectedEur)} · pendiente {formatMoney(settlement.pendingEur)}</small></div></div>)}
        </div>
        {detail.harvest.allocationNotice ? <p className="subtle">{detail.harvest.allocationNotice}</p> : null}
      </> : null}
    </section> : null}

    {activeSection === 'Datos' ? <section className="section">
      <div className="section-head"><h2>Datos</h2><Link href="/mi-campo/mapa" className="detail-link">Abrir mapa</Link></div>
      <div className="card">
        <p><strong>Municipio:</strong> {farm.municipality ?? 'Pendiente'}</p>
        <p><strong>Olivos:</strong> {farm.oliveTrees ?? 'Pendiente'}</p>
        <p><strong>Superficie:</strong> {effectiveArea !== undefined ? `${formatNumber(effectiveArea)} ha` : 'Pendiente'}</p>
        <p><strong>Régimen:</strong> {farm.waterRegime ?? 'Pendiente'}</p>
        <p><strong>Geometría:</strong> {detail.data.geometryStatus ?? 'Sin geometría canónica todavía'}{detail.data.geometrySource ? ` · ${sourceLabel(detail.data.geometrySource)}` : ''}</p>
        <p><strong>Referencias/parcelas:</strong> {detail.data.parcelCount}</p>
      </div>
      {detail.data.references.length ? <div className="card feed today-list">
        {detail.data.references.map((reference) => <div className="feed-row" key={reference.id}><div className="feed-copy"><strong>{sourceLabel(reference.source)}</strong><small>{reference.reference ?? 'Sin referencia textual'}{reference.areaHa !== undefined ? ` · ${formatNumber(reference.areaHa)} ha` : ''}{reference.status ? ` · ${reference.status}` : ''}</small></div></div>)}
      </div> : <section className="card"><h3>Sin referencias territoriales</h3><p>La finca puede existir perfectamente así. Catastro, SIGPAC o una geometría propia se vinculan cuando hagan falta.</p></section>}
    </section> : null}

    {activeSection === 'Documentos' ? <section className="section">
      <div className="section-head"><h2>Documentos</h2><span className="subtle">{detail.documents.length} vinculados</span></div>
      {detail.documents.length ? <div className="card feed today-list">
        {detail.documents.map((document) => <div className="feed-row" key={document.id}><div className="feed-copy"><strong>{document.title}</strong><small>{document.kind} · {document.createdAt.slice(0, 10)}{document.domainType ? ` · ${document.domainType}` : ''}</small></div></div>)}
      </div> : <section className="card"><h3>Sin documentos todavía</h3><p>Fotos, albaranes, facturas, fitosanitarios y resultados aparecerán aquí sin duplicar el dato estructurado al que estén vinculados.</p></section>}
    </section> : null}

    <section className="territory-banner compact-banner"><div><span className="eyebrow">FINCA COMO UNIDAD PRINCIPAL</span><h2>Todo lo demás cuelga de esta ficha.</h2></div><Link href={registerHref}>Registrar</Link></section>
  </>;
}
