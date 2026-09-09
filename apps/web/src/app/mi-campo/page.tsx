import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';

const farms = [
  ['Las Cenillas','23 olivas · Huelma','En buen estado'],
  ['El Cerrillo','88 olivas · Huelma','Atención'],
  ['La Loma','64 olivas · Huelma','En producción'],
] as const;

export default function MiCampoPage() {
  return <main className="app-shell"><Topbar/><div className="page">
    <header className="page-title"><h1>Mi Campo</h1><p>Tus fincas y trabajos.</p></header>
    <section className="card field-summary"><div className="stats"><div className="stat"><b>6</b><span>fincas</span></div><div className="stat"><b>248</b><span>olivas</span></div><div className="stat"><b>4.000 kg</b><span>campaña</span></div></div></section>

    <section className="section"><div className="section-head"><h2>Mis fincas</h2><span/></div><div className="farm-row">
      {farms.map(([name,meta,status],i)=><Link key={name} href={i===0?'/mi-campo/fincas/las-cenillas':'#'} className="card farm-card"><div className="farm-image"/><div className="farm-body"><h3>{name}</h3><div className="farm-meta">{meta}</div><span className="status-pill">{status}</span></div></Link>)}
    </div></section>

    <section className="section"><div className="section-head"><h2>Accesos rápidos</h2><span/></div><div className="quick-grid">
      <Link href="/mi-campo/registrar/cosecha" className="card quick"><span className="icon">➕</span><div><strong>Registrar</strong><small>Peso, riego, trabajo...</small></div></Link>
      <Link href="/mi-campo/fincas/las-cenillas" className="card quick"><span className="icon">🫒</span><div><strong>Cosecha</strong><small>Producción e histórico</small></div></Link>
      <div className="card quick"><span className="icon">💧</span><div><strong>Riegos</strong><small>Planificar y registrar</small></div></div>
      <div className="card quick"><span className="icon">🌿</span><div><strong>Tratamientos</strong><small>Productos y aplicaciones</small></div></div>
      <div className="card quick"><span className="icon">👷</span><div><strong>Jornales</strong><small>Personal y trabajos</small></div></div>
      <div className="card quick"><span className="icon">📖</span><div><strong>Historia</strong><small>Todo lo realizado</small></div></div>
    </div></section>

    <section className="section"><div className="section-head"><h2>Hoy</h2><span/></div><div className="card feed"><div className="feed-row"><div className="dot">💧</div><div><strong>Riego programado · Las Cenillas</strong><small>Hoy · 10:00</small></div></div><div className="feed-row"><div className="dot">🌿</div><div><strong>Revisar tratamiento · El Cerrillo</strong><small>Hoy · Pendiente</small></div></div></div></section>
  </div><BottomNav active="/mi-campo"/></main>;
}
