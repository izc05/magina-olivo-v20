'use client';

import { useState } from 'react';
import Link from 'next/link';
import { demoDelivery } from '@/lib/demo-data';
import { saveLocalActivity } from '@/lib/local-prototype-store';
import { useFieldContext } from '@/lib/use-field-context';
import { ArrowIcon } from '@/components/icons';

export function HarvestOcrClient() {
  const { context, ready } = useFieldContext();
  const [saved, setSaved] = useState(false);

  const fields = [
    ['Finca', context.name],
    ['Cooperativa', demoDelivery.cooperative],
    ['Fecha', demoDelivery.date],
    ['Nº albarán', demoDelivery.ticketNumber],
    ['Peso', `${demoDelivery.kilograms.toLocaleString('es-ES')} kg`],
  ] as const;

  function confirm() {
    if (!ready) return;
    const id = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `harvest-${Date.now()}`;
    saveLocalActivity({
      id,
      fieldId: context.id,
      campaign: context.campaign,
      type: 'harvest',
      occurredOn: '2026-12-12',
      title: 'Cosecha',
      summary: `${demoDelivery.kilograms.toLocaleString('es-ES')} kg · ${demoDelivery.cooperative}`,
      data: {
        cooperative: demoDelivery.cooperative,
        ticketNumber: demoDelivery.ticketNumber,
        kilograms: String(demoDelivery.kilograms),
        detectedBy: 'ocr-demo',
      },
      source: 'ocr',
      createdAt: new Date().toISOString(),
    });
    setSaved(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  const historyHref = context.local
    ? `/mi-campo/fincas/local/modulo?fieldId=${encodeURIComponent(context.id)}&view=historia`
    : '/mi-campo/fincas/las-cenillas/cosechas';

  if (saved) {
    return <section className="record-success card ocr-success">
      <div className="success-mark">✓</div>
      <span className="eyebrow dark">COSECHA GUARDADA · LOCAL</span>
      <h1>{demoDelivery.kilograms.toLocaleString('es-ES')} kg en {context.name}</h1>
      <p>El albarán de demostración se ha convertido en un registro de cosecha asociado a la finca activa.</p>
      <div className="success-effects"><span>✓ Historia de {context.name}</span><span>✓ Campaña {context.campaign}</span><span>✓ Origen: OCR de demostración</span></div>
      <div className="record-actions"><button type="button" className="secondary-action" onClick={() => setSaved(false)}>Escanear otro</button><Link href={historyHref} className="primary action-link">Ver cosecha <ArrowIcon/></Link></div>
    </section>;
  }

  return <>
    <header className="page-title"><span className="eyebrow dark">REGISTRO INTELIGENTE · {context.name.toUpperCase()}</span><h1>Registrar cosecha</h1><p>Haz una foto del albarán. Mágina propone los datos y tú confirmas.</p></header>

    <div className="stepper premium-stepper"><div className="step active"><b>✓</b><span>Foto</span></div><div className="step active"><b>2</b><span>OCR</span></div><div className="step"><b>3</b><span>Confirmar</span></div></div>

    <section className="card receipt-preview" aria-label="Vista del albarán fotografiado"><div className="receipt-status">✓ Foto leída</div></section>

    <section className="section card detected-card">
      <div className="detected-head"><div><span className="eyebrow dark">IA + OCR</span><h2>Datos detectados</h2><p>Revisa antes de guardar en {context.name}.</p></div><span className="confidence-pill">Alta confianza</span></div>
      <div className="data-list clean-list">{fields.map(([label,value])=><div className="data-row" key={label}><span>{label}</span><b>{value}</b><button type="button" className="edit-field" aria-label={`Editar ${label}`}>✎</button></div>)}</div>
      <div className="validation-note"><span>✓</span><div><strong>Lectura completada</strong><small>Los campos críticos siguen requiriendo confirmación.</small></div></div>
    </section>

    <section className="ocr-actions"><button className="secondary-action" type="button">Editar campos</button><button className="primary" type="button" disabled={!ready} onClick={confirm}>Confirmar y guardar →</button></section>
  </>;
}
