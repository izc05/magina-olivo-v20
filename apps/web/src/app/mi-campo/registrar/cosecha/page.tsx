import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';

export default function HarvestOcrPage(){
  return <main className="app-shell"><Topbar/><div className="page">
    <header className="page-title"><h1>Registrar cosecha</h1><p>Digitaliza tu albarán y revisa los datos.</p></header>
    <div className="stepper"><div className="step active"><b>✓</b>Foto</div><div className="step active"><b>2</b>OCR</div><div className="step"><b>3</b>Confirmar</div></div>
    <section className="card ocr-preview"><div className="ocr-sheet"><strong>ALBARÁN DE ENTREGA</strong><p>SCA San Isidro · Huelma</p><hr/><p>Finca: Las Cenillas<br/>Fecha: 12/12/2026<br/>Nº: 008421<br/>Peso: 1.842 kg</p></div></section>
    <section className="section card data-list">
      {[['Finca','Las Cenillas'],['Cooperativa','SCA San Isidro'],['Fecha','12/12/2026'],['Nº albarán','008421'],['Peso','1.842 kg']].map(([a,b])=><div className="data-row" key={a}><span>{a}</span><b>{b}</b><i>✓</i></div>)}
    </section>
    <section className="section"><button className="primary">Confirmar y guardar →</button></section>
  </div><BottomNav active="/mi-campo"/></main>;
}
