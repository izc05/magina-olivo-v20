import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';

const modules = [
  ['🫒','Cosecha','4.000 kg'],['💧','Riegos','Próximo 14 sep'],['🪰','Tratamientos','Sin incidencias'],['🌿','Abonado','Último 12 mar'],
  ['✂️','Poda','Próxima ene 2027'],['€','Costes','2.480 €'],['📄','Documentos','5 archivos'],['🗺','Terreno','Catastro + SIGPAC'],
] as const;

export default function FarmPage(){
  return <main className="app-shell"><Topbar/><div className="page">
    <section className="hero"><div className="hero-kicker">📍 Huelma, Jaén</div><h1>Las Cenillas</h1><p>23 olivas · Secano · Finca de demostración</p></section>
    <div className="kpi-grid"><div className="card kpi"><b>4.000 kg</b><span>campaña 2026/27</span></div><div className="card kpi"><b>21,6 %</b><span>rendimiento</span></div><div className="card kpi"><b>14 sep</b><span>próximo riego</span></div></div>
    <section className="section"><div className="module-grid">{modules.map(([icon,label,summary])=><div className="card module" key={label}><span className="icon">{icon}</span><strong>{label}</strong><small>{summary}</small></div>)}</div></section>
    <section className="section"><div className="section-head"><h2>Últimos movimientos</h2><span/></div><div className="card feed"><div className="feed-row"><div className="dot">🫒</div><div><strong>Entrega de cosecha</strong><small>12 dic · 1.842 kg</small></div></div><div className="feed-row"><div className="dot">🌿</div><div><strong>Tratamiento fitosanitario</strong><small>18 ago · prevención</small></div></div><div className="feed-row"><div className="dot">✂️</div><div><strong>Poda de mantenimiento</strong><small>15 feb · 23 olivas</small></div></div></div></section>
    <section className="section"><Link href="/mi-campo/registrar/cosecha"><button className="primary">＋ Registrar</button></Link></section>
  </div><BottomNav active="/mi-campo"/></main>;
}
