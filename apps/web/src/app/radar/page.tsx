import { BottomNav } from '@/components/bottom-nav';
import { RadarObservationPanel } from '@/components/radar-observation-panel';
import { Topbar } from '@/components/topbar';

export default function RadarPage() {
  return <main className="app-shell">
    <Topbar />
    <div className="page radar-page">
      <header className="page-title radar-title">
        <span className="eyebrow dark">CLIMA DE TU FINCA</span>
        <h1>Radar de lluvia</h1>
        <p>Observación de reflectividad alrededor de tus fincas, separada de la previsión meteorológica.</p>
      </header>
      <RadarObservationPanel />
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
