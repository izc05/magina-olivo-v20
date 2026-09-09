import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, MapPinIcon, PlusIcon, SproutIcon } from '@/components/icons';

const modules = [
  ['🫒','Cosecha','Campaña 26/27 · 4.000 kg'],
  ['💧','Riegos','Próximo · 14 septiembre'],
  ['◉','Tratamientos','Último · 18 agosto'],
  ['🌿','Abonado','Último · 12 marzo'],
  ['✂','Poda','Última · febrero 2026'],
  ['€','Costes','535 € · campaña'],
  ['▤','Documentos','5 archivos'],
  ['▱','Terreno','Catastro + SIGPAC'],
] as const;

export default function FarmPage(){
  return <main className="app-shell"><Topbar/><div className="page farm-detail-page">
    <section className="farm-detail-hero">
      <div className="farm-detail-overlay">
        <span className="eyebrow">MI CAMPO · MÁGINA OLIVO</span>
        <h1>Las Cenillas</h1>
        <div className="farm-location"><MapPinIcon/> Huelma, Jaén</div>
      </div>
    </section>

    <section className="card farm-identity-card">
      <div className="farm-avatar"><SproutIcon/></div>
      <div className="farm-identity-copy"><h2>Las Cenillas</h2><p>Una finca con historia, parte de un territorio único.</p></div>
      <div className="farm-identity-facts"><span><b>23</b> olivas</span><span><b>Secano</b> régimen</span></div>
    </section>

    <div className="kpi-grid living-kpis"><Link href="#cosecha" className="card kpi kpi-green"><b>4.000 kg</b><span>campaña 2026/27</span><small>+12 % vs. anterior</small></Link><Link href="#cosecha" className="card kpi kpi-gold"><b>21,6 %</b><span>rendimiento medio</span><small>Dentro de objetivo</small></Link><Link href="#riegos" className="card kpi kpi-blue"><b>14 sep</b><span>próximo riego</span><small>En 5 días</small></Link></div>

    <section className="section"><div className="module-grid living-modules">{modules.map(([icon,label,summary])=><Link href={label==='Cosecha'?'/mi-campo/registrar/cosecha':'#'} className="card module living-module" key={label}><span className="module-symbol">{icon}</span><div><strong>{label}</strong><small>{summary}</small></div><ArrowIcon/></Link>)}</div></section>

    <section className="section"><div className="section-head"><h2>Últimos movimientos</h2><span className="subtle">Ver todos</span></div><div className="farm-timeline">
      <article className="timeline-row"><span className="timeline-node harvest">🫒</span><time>12 dic 2026</time><div><strong>Entrega de cosecha</strong><small>1.842 kg · SCA San Isidro</small></div><ArrowIcon/></article>
      <article className="timeline-row"><span className="timeline-node treatment">◉</span><time>18 ago 2026</time><div><strong>Tratamiento fitosanitario</strong><small>Mosca del olivo · documento guardado</small></div><ArrowIcon/></article>
      <article className="timeline-row"><span className="timeline-node pruning">✂</span><time>14 feb 2026</time><div><strong>Poda de mantenimiento</strong><small>23 olivas · trabajo finalizado</small></div><ArrowIcon/></article>
    </div></section>

    <section className="sticky-register-wrap"><Link href="/mi-campo/registrar/cosecha" className="primary action-link register-cta"><PlusIcon/> Registrar <ArrowIcon/></Link></section>
  </div><BottomNav active="/mi-campo"/></main>;
}
