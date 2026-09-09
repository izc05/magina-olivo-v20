import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, PlusIcon, SproutIcon } from '@/components/icons';
import { demoFarms, demoFarmSummary } from '@/lib/demo-data';

const quick = [
  ['＋','Registrar','Nueva actividad','/mi-campo/registrar'],
  ['🫒','Cosecha','Producción e histórico','/mi-campo/fincas/las-cenillas'],
  ['💧','Riegos','Planificar y registrar','#'],
  ['🌿','Tratamientos','Productos y aplicaciones','#'],
  ['👷','Jornales','Personal y trabajos','#'],
  ['◷','Historia','Todo lo realizado','#'],
] as const;

export default function MiCampoPage() {
  return <main className="app-shell"><Topbar/><div className="page mi-campo-page">
    <header className="page-title mi-campo-title"><div className="title-mark"><SproutIcon/></div><div><span className="eyebrow dark">GESTIÓN DEL OLIVAR</span><h1>Mi Campo</h1><p>Tus fincas y trabajos, ordenados y fáciles de consultar.</p></div></header>

    <section className="card field-summary campaign-summary"><div className="stats"><div className="stat"><b>{demoFarmSummary.farms}</b><span>fincas</span></div><div className="stat"><b>{demoFarmSummary.oliveTrees}</b><span>olivas</span></div><div className="stat"><b>{demoFarmSummary.campaignKg.toLocaleString('es-ES')} kg</b><span>campaña {demoFarmSummary.campaign}</span></div></div></section>

    <section className="section"><div className="section-head"><h2>Mis fincas</h2><Link href="/mi-campo/fincas/nueva" className="detail-link"><PlusIcon/> Añadir finca</Link></div><div className="farm-row">
      {demoFarms.map((farm)=><Link key={farm.name} href={farm.href} className="card farm-card"><div className="farm-image"><span className={`farm-status ${farm.tone}`}>{farm.status}</span></div><div className="farm-body"><div className="farm-card-head"><div><h3>{farm.name}</h3><div className="farm-meta">{farm.oliveTrees} olivas · {farm.municipality}</div></div><ArrowIcon/></div></div></Link>)}
    </div></section>

    <section className="section"><div className="section-head"><h2>Accesos rápidos</h2><span/></div><div className="quick-grid">
      {quick.map(([icon,title,text,href])=><Link href={href} className="card quick premium-quick" key={title}><span className="icon">{title==='Registrar'?<PlusIcon/>:icon}</span><div><strong>{title}</strong><small>{text}</small></div><ArrowIcon className="quick-arrow"/></Link>)}
    </div></section>

    <section className="section"><div className="section-head"><h2>Hoy</h2><span className="subtle">2 pendientes</span></div><div className="card feed today-list"><div className="feed-row"><div className="dot water">💧</div><div className="feed-copy"><strong>Riego programado</strong><small>Las Cenillas · 10:00–12:00</small></div><button className="outline-action">Marcar hecho</button></div><div className="feed-row"><div className="dot">🌿</div><div className="feed-copy"><strong>Revisar tratamiento</strong><small>El Cerrillo · esta tarde</small></div><span className="pending-pill">Pendiente</span></div></div></section>

    <section className="territory-banner compact-banner"><div><span className="eyebrow">TU HISTORIA AGRÍCOLA</span><h2>Cada registro da memoria a tu finca</h2></div><Link href="/mi-campo/fincas/las-cenillas">Ver ficha <ArrowIcon/></Link></section>
  </div><BottomNav active="/mi-campo"/></main>;
}
