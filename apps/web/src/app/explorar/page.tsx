import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, MapPinIcon, RainIcon } from '@/components/icons';

const areas = [
  ['📰','Noticias','Campo, pueblos y actualidad'],
  ['📅','Eventos','Agenda de Sierra Mágina'],
  ['🫒','Aceite','Mercado, cooperativas y AOVE'],
  ['🍴','Gastronomía','Recetas y sabores de la zona'],
  ['🗺','Rutas','Pueblos, AOVE y experiencias'],
  ['🏪','Cerca de ti','Empresas y profesionales'],
] as const;

export default function ExplorePage(){
  return <main className="app-shell"><Topbar/><div className="page explore-page">
    <section className="explore-hero">
      <div className="location-chip"><MapPinIcon/> Huelma <span>contexto actual</span></div>
      <div><span className="eyebrow">DESCUBRE EL TERRITORIO</span><h1>Sierra Mágina,<br/>en tu mano</h1><p>Información local, gastronomía, empresas, rutas y vida de nuestros pueblos.</p></div>
    </section>

    <section className="section"><div className="section-head"><h2>Explorar</h2><span/></div><div className="explore-grid">{areas.map(([icon,title,text])=><article className="card explore-card" key={title}><span className="explore-icon">{icon}</span><div><h3>{title}</h3><p>{text}</p></div><ArrowIcon/></article>)}</div></section>

    <section className="section"><div className="section-head"><h2>Ahora en Mágina</h2><Link href="/radar">Radar <ArrowIcon/></Link></div><Link href="/radar" className="card weather-feature"><span className="weather-feature-icon"><RainIcon/></span><div><strong>Radar y avisos de lluvia</strong><small>Consulta precipitación cerca de tus fincas.</small></div><ArrowIcon/></Link></section>

    <section className="territory-banner"><div><span className="eyebrow">RUTAS Y SABORES</span><h2>Conoce Mágina desde sus pueblos y su aceite</h2></div><span className="banner-mark">✦</span></section>
  </div><BottomNav active="/explorar"/></main>;
}
