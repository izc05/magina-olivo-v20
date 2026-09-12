import type { ReactNode } from 'react';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { LocalRecordsPanel } from '@/components/local-records-panel';
import { ArrowIcon, MapPinIcon, PlusIcon } from '@/components/icons';
import { lasCenillas } from '@/lib/demo-data';
import {
  costBreakdown,
  farmCalendar,
  farmDocuments,
  farmHistory,
  fertilizationHistory,
  harvestHistory,
  irrigationHistory,
  moduleSlugs,
  pruningHistory,
  treatmentHistory,
  type FarmModuleSlug,
} from '@/lib/farm-module-data';

export const dynamicParams = false;

export function generateStaticParams() {
  return moduleSlugs.map((modulo) => ({ modulo }));
}

const meta: Record<FarmModuleSlug, { title: string; symbol: string; subtitle: string; registerHref?: string; registerLabel?: string }> = {
  cosechas: { title: 'Cosechas', symbol: '🫒', subtitle: 'Producción e histórico por campaña', registerHref: '/mi-campo/registrar/cosecha', registerLabel: 'Registrar cosecha' },
  riegos: { title: 'Riegos', symbol: '💧', subtitle: 'Próximo riego, histórico y costes', registerHref: '/mi-campo/registrar/riego', registerLabel: 'Registrar riego' },
  tratamientos: { title: 'Tratamientos', symbol: '🌿', subtitle: 'Curas, productos y seguimiento', registerHref: '/mi-campo/registrar/tratamiento', registerLabel: 'Registrar tratamiento' },
  abonos: { title: 'Abonos', symbol: '🧪', subtitle: 'Productos, cantidades y gasto', registerHref: '/mi-campo/registrar/abono', registerLabel: 'Registrar abono' },
  poda: { title: 'Poda', symbol: '✂', subtitle: 'Histórico de poda y próxima revisión', registerHref: '/mi-campo/registrar/poda', registerLabel: 'Registrar poda' },
  gastos: { title: 'Gastos', symbol: '€', subtitle: 'Costes de campaña sin duplicar datos', registerHref: '/mi-campo/registrar/gasto', registerLabel: 'Añadir gasto' },
  calendario: { title: 'Calendario', symbol: '📅', subtitle: 'Próximos trabajos y recordatorios' },
  historia: { title: 'Historia', symbol: '◷', subtitle: 'La memoria completa de la finca' },
  documentos: { title: 'Documentos', symbol: '▤', subtitle: 'Albaranes, facturas, fotos y archivos' },
  terreno: { title: 'Terreno', symbol: '▱', subtitle: 'Geometría y referencias oficiales' },
};

function SectionTitle({ children, aside }: { children: ReactNode; aside?: ReactNode }) {
  return <div className="section-head module-section-head"><h2>{children}</h2>{aside}</div>;
}

function HarvestView() {
  const maxKg = Math.max(...harvestHistory.map((item) => item.kg));
  const averageKg = Math.round(harvestHistory.slice(1).reduce((sum, item) => sum + item.kg, 0) / 3);
  return <>
    <section className="card module-highlight harvest-highlight"><span>CAMPAÑA {harvestHistory[0].campaign}</span><strong>{harvestHistory[0].kg.toLocaleString('es-ES')} kg</strong><div><b>{harvestHistory[0].kgPerTree.toLocaleString('es-ES')} kg/oliva</b><b>{harvestHistory[0].yieldPercent.toLocaleString('es-ES')} % rendimiento</b></div></section>
    <section className="section"><SectionTitle aside={<span className="subtle">Últimas 4 campañas</span>}>Evolución</SectionTitle><div className="card harvest-chart">{harvestHistory.slice().reverse().map((item) => <div className="harvest-bar-col" key={item.campaign}><div className="harvest-bar-space"><i style={{ height: `${Math.max(18, item.kg / maxKg * 100)}%` }}><span>{item.kg}</span></i></div><b>{item.campaign}</b></div>)}</div></section>
    <section className="section"><SectionTitle>Histórico</SectionTitle><div className="card module-list">{harvestHistory.map((item) => <article className="module-list-row" key={item.campaign}><div><strong>{item.campaign}</strong><small>{item.date}</small></div><div className="module-row-metrics"><b>{item.kg.toLocaleString('es-ES')} kg</b><span>{item.yieldPercent.toLocaleString('es-ES')} %</span></div></article>)}</div></section>
    <section className="card module-insight"><span>MEDIA 3 CAMPAÑAS ANTERIORES</span><strong>{averageKg.toLocaleString('es-ES')} kg</strong><small>Mejor campaña: 2024/25 · 1.580 kg</small></section>
  </>;
}

function IrrigationView() {
  return <>
    <section className="card next-action-card"><span className="eyebrow dark">PRÓXIMO RIEGO</span><div className="next-action-main"><span className="next-action-symbol">💧</span><div><strong>14 septiembre · 08:00</strong><small>Dentro de 5 días</small></div></div><div className="next-action-buttons"><button>Cambiar fecha</button><Link href="/mi-campo/registrar/riego">Marcar realizado</Link></div></section>
    <div className="kpi-grid module-kpis"><div className="card kpi"><b>8</b><span>riegos campaña</span></div><div className="card kpi"><b>164 €</b><span>coste acumulado</span></div><div className="card kpi"><b>20,50 €</b><span>media / riego</span></div></div>
    <section className="section"><SectionTitle>Últimos riegos</SectionTitle><div className="card module-list">{irrigationHistory.map((item) => <article className="module-list-row" key={item.date}><div><strong>{item.date}</strong><small>Riego realizado</small></div><div className="module-row-metrics"><b>{item.cost} €</b></div></article>)}</div></section>
  </>;
}

function TreatmentsView() {
  return <>
    <section className="card module-highlight treatment-highlight"><span>ÚLTIMO TRATAMIENTO · 18 AGOSTO</span><strong>Mosca del olivo</strong><p>Producto y dosis registrados · seguimiento recomendado.</p><div className="module-highlight-note">📅 Revisar evolución en calendario</div></section>
    <section className="section"><SectionTitle>Histórico</SectionTitle><div className="card module-list">{treatmentHistory.map((item) => <article className="module-list-row detailed" key={item.date}><span className="row-symbol">🌿</span><div><strong>{item.reason}</strong><small>{item.date} · {item.product}<br/>{item.detail}</small></div><ArrowIcon/></article>)}</div></section>
  </>;
}

function FertilizerView() {
  const totalKg = fertilizationHistory.reduce((sum, item) => sum + item.quantityKg, 0);
  const totalCost = fertilizationHistory.reduce((sum, item) => sum + item.cost, 0);
  return <>
    <div className="kpi-grid module-kpis"><div className="card kpi"><b>{totalKg} kg</b><span>abono utilizado</span></div><div className="card kpi"><b>{totalCost} €</b><span>gasto campaña</span></div><div className="card kpi"><b>{(totalKg / lasCenillas.oliveTrees).toFixed(1)} kg</b><span>por oliva</span></div></div>
    <section className="section"><SectionTitle>Abonados</SectionTitle><div className="card module-list">{fertilizationHistory.map((item) => <article className="module-list-row detailed" key={item.date}><span className="row-symbol">🧪</span><div><strong>{item.product}</strong><small>{item.date} · {item.quantityKg} kg</small></div><div className="module-row-metrics"><b>{item.cost} €</b></div></article>)}</div></section>
  </>;
}

function PruningView() {
  return <>
    <section className="card module-highlight pruning-highlight"><span>ÚLTIMA PODA</span><strong>14 febrero 2026</strong><p>Poda completa · 2 personas · 6 horas · 120 €.</p></section>
    <section className="section"><SectionTitle>Histórico</SectionTitle><div className="card module-list">{pruningHistory.map((item) => <article className="module-list-row detailed" key={item.date}><span className="row-symbol">✂</span><div><strong>{item.type}</strong><small>{item.date} · {item.workers} personas · {item.hours} h</small></div><div className="module-row-metrics"><b>{item.cost} €</b></div></article>)}</div></section>
    <section className="card module-insight"><span>PRÓXIMA REVISIÓN</span><strong>Enero 2027</strong><small>Estimación de demostración. El agricultor decide cuándo programarla.</small></section>
  </>;
}

function CostsView() {
  const total = costBreakdown.reduce((sum, item) => sum + item.amount, 0);
  const max = Math.max(...costBreakdown.map((item) => item.amount));
  return <>
    <section className="card module-highlight cost-highlight"><span>CAMPAÑA {lasCenillas.campaign}</span><strong>{total} €</strong><div><b>{(total / lasCenillas.campaignKg).toFixed(2)} €/kg</b><b>{(total / lasCenillas.oliveTrees).toFixed(2)} €/oliva</b></div></section>
    <section className="section"><SectionTitle>¿En qué se ha gastado?</SectionTitle><div className="card cost-breakdown">{costBreakdown.map((item) => <article key={item.label}><span className="cost-symbol">{item.symbol}</span><div><div className="cost-label"><strong>{item.label}</strong><b>{item.amount} €</b></div><div className="cost-track"><i style={{ width: `${item.amount / max * 100}%` }} /></div></div></article>)}</div></section>
    <section className="card no-duplicate-note"><strong>Un gasto no se escribe dos veces</strong><p>Si un riego cuesta 22 €, ese importe entra aquí desde el propio registro de riego.</p></section>
  </>;
}

function CalendarView() {
  return <>
    <section className="card module-highlight calendar-highlight"><span>PRÓXIMO TRABAJO</span><strong>14 septiembre</strong><div><b>💧 Riego · 08:00</b><b>2 avisos programados</b></div></section>
    <section className="section"><SectionTitle aside={<span className="subtle">Septiembre–noviembre</span>}>Próximos</SectionTitle><div className="card calendar-list">{farmCalendar.map((item) => <article key={`${item.day}-${item.month}-${item.title}`} className={`calendar-row ${item.tone}`}><div className="calendar-date"><strong>{item.day}</strong><span>{item.month}</span></div><span className="calendar-symbol">{item.symbol}</span><div><strong>{item.title}</strong><small>{item.time} · {item.detail}</small></div><ArrowIcon/></article>)}</div></section>
    <section className="card no-duplicate-note"><strong>El calendario se alimenta solo</strong><p>Cuando registras una tarea y programas su seguimiento, Mágina crea el evento y sus avisos sin volver a escribir los datos.</p></section>
  </>;
}

function HistoryView() {
  return <>
    <div className="history-filters"><button className="active">Todo</button><button>Cosecha</button><button>Riego</button><button>Tratamiento</button><button>Abono</button><button>Poda</button></div>
    <section className="card full-history">{farmHistory.map((item) => <article key={`${item.date}-${item.title}`}><span className="history-symbol">{item.symbol}</span><time>{item.date}</time><div><strong>{item.title}</strong><small>{item.detail}</small></div></article>)}</section>
  </>;
}

function DocumentsView() {
  return <>
    <section className="card module-insight"><span>ARCHIVOS DE LA FINCA</span><strong>{farmDocuments.length}</strong><small>Albaranes, facturas, fotos y documentos ligados a sus registros.</small></section>
    <section className="section"><SectionTitle>Documentos</SectionTitle><div className="card module-list">{farmDocuments.map((item) => <article className="module-list-row detailed document-row" key={`${item.type}-${item.title}`}><span className="document-type">{item.type}</span><div><strong>{item.title}</strong><small>{item.date} · {item.meta}</small></div><ArrowIcon/></article>)}</div></section>
  </>;
}

function TerrainView() {
  return <>
    <section className="card terrain-map-card"><div className="terrain-outline"/><span><MapPinIcon/> {lasCenillas.name}</span></section>
    <div className="kpi-grid module-kpis"><div className="card kpi"><b>0,48 ha</b><span>superficie demo</span></div><div className="card kpi"><b>23</b><span>olivas</span></div><div className="card kpi"><b>Manual</b><span>geometría actual</span></div></div>
    <section className="section"><SectionTitle>Referencias del terreno</SectionTitle><div className="card terrain-sources"><article><div><strong>Catastro</strong><small>Referencia y geometría catastral</small></div><span className="pending-source">Por confirmar</span></article><article><div><strong>SIGPAC</strong><small>Polígono, parcela y recintos</small></div><span className="pending-source">Por confirmar</span></article></div></section>
    <section className="card no-duplicate-note"><strong>La finca manda en Mágina</strong><p>{lasCenillas.name} puede corresponder a una parte, una o varias parcelas oficiales. Catastro y SIGPAC se vinculan por debajo sin cambiar el nombre que usa la familia.</p><Link href="/mi-campo/fincas/nueva">Ver flujo de vinculación <ArrowIcon/></Link></section>
  </>;
}

function ModuleContent({ modulo }: { modulo: FarmModuleSlug }) {
  switch (modulo) {
    case 'cosechas': return <HarvestView/>;
    case 'riegos': return <IrrigationView/>;
    case 'tratamientos': return <TreatmentsView/>;
    case 'abonos': return <FertilizerView/>;
    case 'poda': return <PruningView/>;
    case 'gastos': return <CostsView/>;
    case 'calendario': return <CalendarView/>;
    case 'historia': return <HistoryView/>;
    case 'documentos': return <DocumentsView/>;
    case 'terreno': return <TerrainView/>;
  }
}

function LocalProjection({ modulo }: { modulo: FarmModuleSlug }) {
  switch (modulo) {
    case 'cosechas': return <LocalRecordsPanel fieldId={lasCenillas.id} mode="type" activityType="harvest"/>;
    case 'riegos': return <LocalRecordsPanel fieldId={lasCenillas.id} mode="type" activityType="irrigation"/>;
    case 'tratamientos': return <LocalRecordsPanel fieldId={lasCenillas.id} mode="type" activityType="treatment"/>;
    case 'abonos': return <LocalRecordsPanel fieldId={lasCenillas.id} mode="type" activityType="fertilization"/>;
    case 'poda': return <LocalRecordsPanel fieldId={lasCenillas.id} mode="type" activityType="pruning"/>;
    case 'gastos': return <LocalRecordsPanel fieldId={lasCenillas.id} mode="costs"/>;
    case 'calendario': return <LocalRecordsPanel fieldId={lasCenillas.id} mode="calendar"/>;
    case 'historia': return <LocalRecordsPanel fieldId={lasCenillas.id} mode="history"/>;
    default: return null;
  }
}

export default async function FarmModulePage({ params }: { params: Promise<{ modulo: string }> }) {
  const { modulo: raw } = await params;
  if (!moduleSlugs.includes(raw as FarmModuleSlug)) notFound();
  const modulo = raw as FarmModuleSlug;
  const current = meta[modulo];

  return <main className="app-shell"><Topbar/><div className="page farm-module-page">
    <header className="module-page-title"><Link href="/mi-campo/fincas/las-cenillas">‹ {lasCenillas.name}</Link><span className="module-page-symbol">{current.symbol}</span><div><span className="eyebrow dark">FICHA VIVA · {lasCenillas.campaign}</span><h1>{current.title}</h1><p>{current.subtitle}</p></div></header>
    <ModuleContent modulo={modulo}/>
    <LocalProjection modulo={modulo}/>
    {current.registerHref && <section className="sticky-register-wrap module-register"><Link href={current.registerHref} className="primary action-link register-cta"><PlusIcon/> {current.registerLabel} <ArrowIcon/></Link></section>}
  </div><BottomNav active="/mi-campo"/></main>;
}
