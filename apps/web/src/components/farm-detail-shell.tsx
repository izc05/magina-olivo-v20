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

function formatDate(value?: string) {
  if (!value) return 'Fecha pendiente';
  const date = new Date(`${value.slice(0, 10)}T00:00:00`);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('es-ES');
}

function readableLabel(value?: string) {
  if (!value) return undefined;
  return value
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
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
  if (source === 'own-boundary') return 'Límite dibujado';
  if (source === 'manual') return 'Añadida manualmente';
  return readableLabel(source) ?? 'Referencia';
}

function geometryStatusLabel(status?: string) {
  if (!status) return 'Pendientes de definir';
  if (['ready', 'available', 'active', 'linked', 'complete'].includes(status)) return 'Definidos';
  if (['pending', 'missing', 'none', 'unlinked'].includes(status)) return 'Pendientes de definir';
  return readableLabel(status) ?? 'Pendientes de definir';
}

function referenceStatusLabel(status?: string) {
  if (!status) return undefined;
  if (['active', 'linked', 'verified', 'confirmed'].includes(status)) return 'Vinculada';
  if (['pending', 'draft', 'unverified'].includes(status)) return 'Pendiente';
  return readableLabel(status);
}

function waterRegimeLabel(value?: string) {
  if (!value) return 'Pendiente';
  if (value === 'rainfed') return 'Secano';
  if (value === 'irrigated') return 'Regadío';
  return readableLabel(value) ?? value;
}

function documentKindLabel(kind: string) {
  const labels: Record<string, string> = {
    photo: 'Foto',
    image: 'Imagen',
    invoice: 'Factura',
    receipt: 'Justificante',
    delivery_note: 'Albarán',
    ticket: 'Ticket',
    phytosanitary: 'Fitosanitario',
    report: 'Informe',
  };
  return labels[kind] ?? readableLabel(kind) ?? kind;
}

function domainLabel(domain?: string) {
  if (!domain) return undefined;
  const labels: Record<string, string> = {
    field: 'Finca',
    work: 'Trabajo',
    harvest_delivery: 'Entrega de cosecha',
    harvest_result: 'Rendimiento',
    treatment: 'Tratamiento',
    irrigation: 'Riego',
    fertilization: 'Abonado',
  };
  return labels[domain] ?? readableLabel(domain);
}

export function FarmDetailShell() {
  const params = useSearchParams();
  const id = params.get('id');
  const source = params.get('source');
  const { apiConfigured, previewEnabled, status, selectedWorkspaceId } = useAuth();
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

      if (apiConfigured && status === 'loading') return;

      try {
        if (!id) {
          if (!cancelled) setFarm(null);
          return;
        }

        if (apiConfigured && status === 'authenticated' && selectedWorkspaceId && source !== 'local' && source !== 'demo') {
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
              totalCostEur: remoteDetail.economics.productionCostEur,
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
        } else if (previewEnabled) {
          const farms = getPreviewFarms();
          if (!cancelled) {
            setFarm(farms.find((item) => item.id === id && (!source || item.source === source)) ?? null);
            setDerived(getLocalFarmDerivedView(id));
            setDetail(loadPreviewFarmDetailData(id, source));
          }
        } else if (!cancelled) {
          setFarm(null);
          setDetail(emptyFarmDetailData());
          setDerived(emptyDerived());
          setDetailError(apiConfigured && status !== 'authenticated'
            ? 'Inicia sesión para abrir una finca privada.'
            : 'El servicio de datos de Mi Campo no está disponible en esta instalación.');
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
    const refresh = () => { if (previewEnabled && source !== 'api') void load(); };
    window.addEventListener('magina:prototype-data-changed', refresh);
    return () => {
      cancelled = true;
      window.removeEventListener('magina:prototype-data-changed', refresh);
    };
  }, [apiConfigured, id, previewEnabled, selectedWorkspaceId, source, status]);

  const registerHref = useMemo(() => {
    if (!id) return '/mi-campo/registrar';
    const query = new URLSearchParams({ fieldId: id });
    if (farm?.source) query.set('source', farm.source);
    else if (source) query.set('source', source);
    return `/mi-campo/registrar?${query.toString()}`;
  }, [farm?.source, id, source]);

  if (loading) return <section className="card"><p>Cargando finca…</p></section>;
  if (!farm) return <section className="card"><h1>Finca no encontrada</h1><p>{detailError ?? 'La finca no está disponible ahora mismo.'}</p><Link href="/mi-campo" className="secondary-action action-link">Volver a Mi Campo</Link></section>;

  const effectiveArea = detail.data.areaHa ?? farm.areaHa;
  const isApi = farm.source === 'api';
  const deliveredKg = isApi ? detail.economics.deliveredKg : detail.harvest.totalKg || derived.deliveredKg;
  const agriculturalCostEur = isApi ? detail.economics.productionCostEur : derived.totalCostEur;
  const accruedIncomeEur = isApi ? detail.economics.accruedIncomeEur : derived.totalIncomeEur;
  const collectedIncomeEur = isApi ? detail.economics.collectedIncomeEur : detail.harvest.collectedEur;
  const pendingCollectionEur = isApi ? detail.economics.pendingCollectionEur : detail.harvest.pendingEur;
  const accruedMarginEur = isApi ? detail.economics.accruedMarginEur : derived.marginEur;
  const costPerKg = isApi
    ? detail.economics.costPerDeliveredKgEur
    : deliveredKg > 0 ? agriculturalCostEur / deliveredKg : undefined;
  const workCosts = detail.economics.workCostBreakdown;
  const professional = detail.economics.professionalWork;
  const hasWorkCosts = isApi && workCosts.totalWorkEur > 0;
  const hasProfessionalWork = isApi && (professional.chargedEur > 0 || professional.collectedEur > 0 || professional.directCostEur > 0);

  return <>
    <header className="page-title mi-campo-title">
      <div className="title-mark"><SproutIcon /></div>
      <div><span className="eyebrow dark">MI CAMPO · FINCA</span><h1>{farm.name}</h1><p>{farm.municipality ?? 'Municipio pendiente'}{farm.oliveTrees ? ` · ${farm.oliveTrees} olivas` : ''}{effectiveArea ? ` · ${formatNumber(effectiveArea)} ha` : ''}</p></div>
    </header>

    <section className="card field-summary campaign-summary"><div className="stats">
      <div className="stat"><b>{farm.oliveTrees ?? '—'}</b><span>olivas</span></div>
      <div className="stat"><b>{effectiveArea !== undefined ? formatNumber(effectiveArea) : '—'}</b><span>hectáreas</span></div>
      <div className="stat"><b>{waterRegimeLabel(farm.waterRegime)}</b><span>régimen</span></div>
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

      <div className="section-head"><h3>Economía agrícola</h3><Link href="/mi-campo/campana" className="detail-link">Ver campaña</Link></div>
      <div className="quick-grid">
        <article className="card quick premium-quick"><div><strong>{formatMoney(agriculturalCostEur)}</strong><small>costes agrícolas registrados</small></div></article>
        <article className="card quick premium-quick"><div><strong>{costPerKg !== undefined ? `${formatNumber(costPerKg)} €/kg` : '—'}</strong><small>coste agrícola por kg</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(accruedIncomeEur)}</strong><small>liquidado atribuible</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(collectedIncomeEur)}</strong><small>cobrado de cosecha</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(pendingCollectionEur)}</strong><small>pendiente de cosecha</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(accruedMarginEur)}</strong><small>margen agrícola devengado</small></div></article>
      </div>

      {hasWorkCosts ? <>
        <div className="section-head"><h3>Costes de trabajos</h3><span className="subtle">Desglose informativo</span></div>
        <div className="quick-grid">
          <article className="card quick premium-quick"><div><strong>{formatMoney(workCosts.laborEur)}</strong><small>mano de obra / jornales</small></div></article>
          <article className="card quick premium-quick"><div><strong>{formatMoney(workCosts.machineryEur)}</strong><small>maquinaria</small></div></article>
          <article className="card quick premium-quick"><div><strong>{formatMoney(workCosts.materialsEur)}</strong><small>materiales</small></div></article>
          <article className="card quick premium-quick"><div><strong>{formatMoney(workCosts.servicesEur)}</strong><small>servicios externos</small></div></article>
          <article className="card quick premium-quick"><div><strong>{formatMoney(workCosts.totalWorkEur)}</strong><small>total trabajos</small></div></article>
        </div>
        <p className="subtle">Estos trabajos ya están incluidos en los costes registrados de la finca, por lo que no se suman una segunda vez.</p>
      </> : null}

      {hasProfessionalWork ? <>
        <div className="section-head"><h3>Actividad profesional</h3><Link href="/mi-campo/profesional" className="detail-link">Ver actividad profesional</Link></div>
        <div className="quick-grid">
          <article className="card quick premium-quick"><div><strong>{formatMoney(professional.chargedEur)}</strong><small>facturado / devengado</small></div></article>
          <article className="card quick premium-quick"><div><strong>{formatMoney(professional.collectedEur)}</strong><small>cobrado</small></div></article>
          <article className="card quick premium-quick"><div><strong>{formatMoney(professional.pendingEur)}</strong><small>pendiente de cobro</small></div></article>
          <article className="card quick premium-quick"><div><strong>{formatMoney(professional.directCostEur)}</strong><small>coste profesional directo</small></div></article>
          <article className="card quick premium-quick"><div><strong>{formatMoney(professional.accruedMarginEur)}</strong><small>margen profesional devengado</small></div></article>
          <article className="card quick premium-quick"><div><strong>{formatMoney(detail.economics.combinedAccruedMarginEur)}</strong><small>margen combinado finca + servicios</small></div></article>
        </div>
        <p className="subtle">Los costes profesionales se separan del coste agrícola: no encarecen el €/kg de aceituna ni reducen artificialmente el margen de cosecha.</p>
      </> : null}

      {isApi ? <p className="subtle">El margen agrícola compara lo liquidado con los costes registrados. No equivale al dinero disponible en caja, porque cobros y pagos efectivos son movimientos distintos.</p> : <p className="subtle">Vista de demostración: estos importes son datos de ejemplo y no corresponden a una explotación real.</p>}
    </section> : null}

    {activeSection === 'Actividad' ? <section className="section">
      <div className="section-head"><h2>Actividad</h2><Link href={registerHref} className="detail-link"><PlusIcon /> Registrar trabajo</Link></div>
      {derived.recentActivity.length ? <div className="card feed today-list">
        {derived.recentActivity.map((item) => <div className="feed-row" key={item.id}><div className="feed-copy"><strong>{item.title}</strong><small>{formatDate(item.date)}{item.summary ? ` · ${item.summary}` : ''}</small></div>{item.amountEur !== undefined ? <span className="pending-pill">{formatMoney(item.amountEur)}</span> : null}</div>)}
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
        {detail.harvest.deliveries.map((delivery) => <div className="feed-row" key={delivery.id}><div className="feed-copy"><strong>{Math.round(delivery.kg).toLocaleString('es-ES')} kg{delivery.destination ? ` · ${delivery.destination}` : ''}</strong><small>{formatDate(delivery.date)}{delivery.ticketNumber ? ` · Albarán ${delivery.ticketNumber}` : ''}{delivery.yieldPercent !== undefined ? ` · Rend. ${formatNumber(delivery.yieldPercent)} %` : ' · Rendimiento pendiente'}</small></div></div>)}
      </div> : <section className="card"><h3>Sin entregas todavía</h3><p>Las entregas pueden repartir kilos entre una o varias fincas y recibir el rendimiento días después.</p></section>}

      {detail.harvest.settlements.length ? <>
        <div className="section-head"><h3>Liquidaciones</h3><span className="subtle">{detail.harvest.settlements.length}</span></div>
        <div className="card feed today-list">
          {detail.harvest.settlements.map((settlement) => <div className="feed-row" key={settlement.id}><div className="feed-copy"><strong>{formatMoney(settlement.netEur)}{settlement.counterparty ? ` · ${settlement.counterparty}` : ''}</strong><small>{formatDate(settlement.date)}{settlement.settlementNumber ? ` · Liquidación ${settlement.settlementNumber}` : ''} · {Math.round(settlement.fieldKg).toLocaleString('es-ES')} kg atribuidos · cobrado {formatMoney(settlement.collectedEur)} · pendiente {formatMoney(settlement.pendingEur)}</small></div></div>)}
        </div>
        {detail.harvest.allocationNotice ? <p className="subtle">{detail.harvest.allocationNotice}</p> : null}
      </> : null}
    </section> : null}

    {activeSection === 'Datos' ? <section className="section">
      <div className="section-head"><h2>Datos</h2><Link href="/mi-campo/mapa" className="detail-link">Abrir mapa</Link></div>
      <div className="card">
        <p><strong>Municipio:</strong> {farm.municipality ?? 'Pendiente'}</p>
        <p><strong>Olivas:</strong> {farm.oliveTrees ?? 'Pendiente'}</p>
        <p><strong>Superficie:</strong> {effectiveArea !== undefined ? `${formatNumber(effectiveArea)} ha` : 'Pendiente'}</p>
        <p><strong>Régimen:</strong> {waterRegimeLabel(farm.waterRegime)}</p>
        <p><strong>Límites en el mapa:</strong> {geometryStatusLabel(detail.data.geometryStatus)}{detail.data.geometrySource ? ` · ${sourceLabel(detail.data.geometrySource)}` : ''}</p>
        <p><strong>Parcelas y referencias:</strong> {detail.data.parcelCount}</p>
      </div>
      {detail.data.references.length ? <div className="card feed today-list">
        {detail.data.references.map((reference) => {
          const statusLabel = referenceStatusLabel(reference.status);
          return <div className="feed-row" key={reference.id}><div className="feed-copy"><strong>{sourceLabel(reference.source)}</strong><small>{reference.reference ?? 'Sin referencia indicada'}{reference.areaHa !== undefined ? ` · ${formatNumber(reference.areaHa)} ha` : ''}{statusLabel ? ` · ${statusLabel}` : ''}</small></div></div>;
        })}
      </div> : <section className="card"><h3>Sin parcelas o referencias vinculadas</h3><p>Puedes usar la finca desde ahora y añadir Catastro, SIGPAC o sus límites en el mapa cuando los necesites.</p></section>}
    </section> : null}

    {activeSection === 'Documentos' ? <section className="section">
      <div className="section-head"><h2>Documentos</h2><span className="subtle">{detail.documents.length} vinculados</span></div>
      {detail.documents.length ? <div className="card feed today-list">
        {detail.documents.map((document) => {
          const linkedTo = domainLabel(document.domainType);
          return <div className="feed-row" key={document.id}><div className="feed-copy"><strong>{document.title}</strong><small>{documentKindLabel(document.kind)} · {formatDate(document.createdAt)}{linkedTo ? ` · ${linkedTo}` : ''}</small></div></div>;
        })}
      </div> : <section className="card"><h3>Sin documentos todavía</h3><p>Fotos, albaranes, facturas, fitosanitarios y resultados aparecerán aquí junto al trabajo o registro al que pertenecen.</p></section>}
    </section> : null}

    <section className="territory-banner compact-banner"><div><span className="eyebrow">TODO TU CAMPO EN UN SITIO</span><h2>Trabajos, cosecha, documentos y datos de esta finca, siempre juntos.</h2></div><Link href={registerHref}>Registrar</Link></section>
  </>;
}