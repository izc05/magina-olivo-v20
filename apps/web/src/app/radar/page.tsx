import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';

export default function RadarPage(){
  return <main className="app-shell"><Topbar/><div className="page">
    <header className="page-title"><h1>Radar de lluvia</h1><p>Las Cenillas · Huelma</p></header>
    <section className="radar-map"><div className="radar-label">Radar actualizado 18:20</div><div className="radar-pin">📍 Las Cenillas</div></section>
    <section className="section card alert rose" style={{minHeight:'auto'}}><span className="icon">🌧</span><strong>Precipitación detectada a 8 km</strong><small>Concepto visual: posible llegada 35–50 min solo cuando exista nowcast fiable.</small></section>
    <section className="section"><div className="section-head"><h2>Próximas horas</h2><span/></div><div className="kpi-grid"><div className="card kpi"><b>19 h</b><span>🌧 70 %</span></div><div className="card kpi"><b>20 h</b><span>🌧 80 %</span></div><div className="card kpi"><b>21 h</b><span>☁️ 60 %</span></div></div></section>
    <section className="section card field-summary"><h2>Alertas de lluvia</h2><p>Push activado · radio 10 km · antelación 1 h</p></section>
  </div><BottomNav active="/radar"/></main>;
}
