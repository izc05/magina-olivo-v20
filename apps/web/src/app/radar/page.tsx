import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, MapPinIcon, RainIcon } from '@/components/icons';

export default function RadarPage(){
  return <main className="app-shell"><Topbar/><div className="page radar-page">
    <header className="page-title radar-title"><span className="eyebrow dark">CLIMA DE TU FINCA</span><h1>Radar de lluvia</h1><p>Seguimiento visual alrededor de Las Cenillas.</p></header>

    <section className="card radar-context"><div className="radar-farm-thumb"/><div><strong>Las Cenillas</strong><small><MapPinIcon/> Huelma · finca de olivar</small></div><span className="active-alert">Aviso activo</span></section>

    <section className="radar-map premium-radar"><div className="radar-label">↻ Radar actualizado · 18:20</div><div className="radar-legend"><span><i className="light"/>Ligera</span><span><i className="moderate"/>Moderada</span><span><i className="strong"/>Fuerte</span></div><div className="radar-pin"><MapPinIcon/> Las Cenillas</div><div className="storm-arrow">······➤</div></section>

    <section className="section rain-warning"><span className="rain-warning-icon"><RainIcon/></span><div><strong>Precipitación detectada a 8 km</strong><small>La llegada estimada solo se mostrará cuando exista un nowcast fiable.</small></div><ArrowIcon/></section>

    <section className="section"><div className="section-head"><h2>Próximas horas</h2><span className="subtle">Previsión</span></div><div className="hour-grid">{[['19 h','70 %','18°'],['20 h','80 %','17°'],['21 h','60 %','16°'],['22 h','30 %','15°']].map(([hour,rain,temp])=><div className="card hour-card" key={hour}><b>{hour}</b><span className="hour-rain">☂</span><strong>{temp}</strong><small>{rain}</small></div>)}</div></section>

    <section className="section card rain-settings"><div><span className="eyebrow dark">ALERTAS</span><h2>Protege tus fincas</h2><p>Configura cuándo quieres recibir avisos.</p></div><div className="settings-grid"><span><b>Push</b> Activado</span><span><b>Radio</b> 10 km</span><span><b>Antelación</b> 1 h</span></div><Link href="/perfil" className="detail-link">Configurar <ArrowIcon/></Link></section>
  </div><BottomNav active="/explorar"/></main>;
}
