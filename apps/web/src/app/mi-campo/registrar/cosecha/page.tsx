import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ArrowIcon } from '@/components/icons';

const fields = [
  ['Finca','Las Cenillas'],
  ['Cooperativa','SCA San Isidro'],
  ['Fecha','12/12/2026'],
  ['Nº albarán','008421'],
  ['Peso','1.842 kg'],
] as const;

export default function HarvestOcrPage(){
  return <main className="app-shell"><Topbar/><div className="page ocr-page">
    <header className="page-title"><span className="eyebrow dark">REGISTRO INTELIGENTE</span><h1>Registrar cosecha</h1><p>Haz una foto del albarán. Mágina propone los datos y tú confirmas.</p></header>

    <div className="stepper premium-stepper"><div className="step active"><b>✓</b><span>Foto</span></div><div className="step active"><b>2</b><span>OCR</span></div><div className="step"><b>3</b><span>Confirmar</span></div></div>

    <section className="card receipt-preview" aria-label="Vista del albarán fotografiado">
      <div className="receipt-status">✓ Foto leída</div>
    </section>

    <section className="section card detected-card">
      <div className="detected-head"><div><span className="eyebrow dark">IA + OCR</span><h2>Datos detectados</h2><p>Revisa antes de guardar.</p></div><span className="confidence-pill">Alta confianza</span></div>
      <div className="data-list clean-list">
        {fields.map(([label,value])=><div className="data-row" key={label}><span>{label}</span><b>{value}</b><button className="edit-field" aria-label={`Editar ${label}`}>✎</button></div>)}
      </div>
      <div className="validation-note"><span>✓</span><div><strong>Lectura completada</strong><small>Los campos críticos siguen requiriendo confirmación.</small></div></div>
    </section>

    <section className="ocr-actions"><button className="secondary-action">Editar campos</button><Link href="/mi-campo/fincas/las-cenillas" className="primary action-link">Confirmar y guardar <ArrowIcon/></Link></section>
  </div><BottomNav active="/mi-campo"/></main>;
}
