import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, MapPinIcon, PlusIcon, SproutIcon } from '@/components/icons';
import { lasCenillas } from '@/lib/demo-data';

const modules = [
  ['🫒','Cosecha','Campaña 26/27 · 4.000 kg','#cosecha'],
  ['💧','Riegos','Próximo · 14 septiembre','#riegos'],
  ['◉','Tratamientos','Último · 18 agosto','#tratamientos'],
  ['🌿','Abonado','Último · 12 marzo','#abonado'],
  ['✂','Poda','Última · febrero 2026','#poda'],
  ['€','Costes','535 € · campaña','#costes'],
  ['▤','Documentos','5 archivos','#documentos'],
  ['▱','Terreno','Catastro + SIGPAC','#terreno'],
] as const;

export default function FarmPage(){
  return <main className="app-shell"><Topbar/><div className="page farm-detail-page">
    <section className="farm-detail-hero">
      <div className="farm-detail-overlay">
        <span className="eyebrow">MI CAMPO · MÁGINA OLIVO</span>
        <h1>{lasCenillas.name}</h1>
        <div className="farm-location"><MapPinIcon/> {lasCenillas.municipality}, {lasCenillas.province}</div>
      </div>
    </section>

    <section className="card farm-identity-card">
      <div className="farm-avatar"><SproutIcon/></div>
      <div className="farm-identity-copy"><h2>{lasCenillas.name}</h2><p>Una finca con historia, parte de un territorio único.</p></div>
      <div className="farm-identity-facts"><span><b>{lasCenillas.oliveTrees}</b> olivas</span><span><b>{lasCenillas.waterRegime}</b> régimen</span></div>
    </section>

    <div className="kpi-grid living-kpis"><Link href="#cosecha" className="card kpi kpi-green"><b>{lasCenillas.campaignKg.toLocaleString('es-ES')} kg</b><span>campaña {lasCenillas.campaign}</span><small>+12 % vs. anterior</small></Link><Link href="#cosecha" className="card kpi kpi-gold"><b>{lasCenillas.averageYieldPercent.toLocaleString('es-ES')} %</b><span>rendimiento medio</span><small>Dentro de objetivo</small></Link><Link href="#riegos" className="card kpi kpi-blue"><b>{lasCenillas.nextIrrigationLabel}</b><span>próximo riego</span><small>En 5 días</small></Link></div>

    <section className="section"><div className="module-grid living-modules">{modules.map(([icon,label,summary,href])=><Link href={href} className="card module living-module" key={label}><span className="module-symbol">{icon}</span><div><strong>{label}</strong><small>{summary}</small></div><ArrowIcon/></Link>)}</div></section>

    <section className="section"><div className="section-head"><h2>Últimos movimientos</h2><span className="subtle">Ver todos</span></div><div className="farm-timeline">
      <article className="timeline-row"><span className="timeline-node harvest">🫒</span><time>12 dic 2026</time><div><strong>Entrega de cosecha</strong><small>1.842 kg · SCA San Isidro</small></div><ArrowIcon/></article>
      <article className="timeline-row"><span className="timeline-node treatment">◉</span><time>18 ago 2026</time><div><strong>Tratamiento fitosanitario</strong><small>Mosca del olivo · documento guardado</small></div><ArrowIcon/></article>
      <article className="timeline-row"><span className="timeline-node pruning">✂</span><time>14 feb 2026</time><div><strong>Poda de mantenimiento</strong><small>23 olivas · trabajo finalizado</small></div><ArrowIcon/></article>
    </div></section>

    <section className="sticky-register-wrap"><Link href="/mi-campo/registrar" className="primary action-link register-cta"><PlusIcon/> Registrar <ArrowIcon/></Link></section>
  </div><BottomNav active="/mi-campo"/></main>;
}
