'use client';

import { FormEvent, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { demoDelivery } from '@/lib/demo-data';
import { saveLocalActivity } from '@/lib/local-prototype-store';
import { useFieldContext } from '@/lib/use-field-context';
import { completePlannedTask } from '@/lib/planned-task-data-source';
import { apiFetch } from '@/lib/api-client';
import { useAuth } from '@/components/auth-provider';
import { ArrowIcon } from '@/components/icons';

export function HarvestOcrClient() {
  const { context, ready } = useFieldContext();
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const params = useSearchParams();
  const plannedEventId = params.get('plannedEventId');
  const apiMode = context.source === 'api' && apiConfigured && status === 'authenticated' && Boolean(selectedWorkspaceId);
  const [saved, setSaved] = useState(false);
  const [savedKg, setSavedKg] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [completionWarning, setCompletionWarning] = useState<string | null>(null);

  async function saveRemote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ready || !selectedWorkspaceId || saving) return;
    setSaving(true);
    setError(null);
    setCompletionWarning(null);
    const form = new FormData(event.currentTarget);
    const kg = Number(String(form.get('kg') ?? '').replace(',', '.'));
    const date = String(form.get('date') ?? '');
    if (!Number.isFinite(kg) || kg <= 0 || !date) {
      setError('Indica fecha y kilos válidos.');
      setSaving(false);
      return;
    }
    try {
      const response = await apiFetch<{ delivery: { id: string } }>('/api/v1/deliveries', {
        method: 'POST',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify({
          client_operation_id: crypto.randomUUID(),
          cooperative_or_mill: String(form.get('cooperative') || '').trim() || undefined,
          delivery_at: `${date}T12:00:00.000Z`,
          ticket_number: String(form.get('ticket') || '').trim() || undefined,
          total_kg: kg,
          source: 'manual',
          fields: [{ field_id: context.id, kg }],
        }),
      });

      if (plannedEventId) {
        try {
          await completePlannedTask({
            workspaceId: selectedWorkspaceId,
            taskId: plannedEventId,
            domainType: 'harvest_delivery',
            domainRecordId: response.delivery.id,
          });
        } catch (completionError) {
          console.warn('Harvest saved but planned task could not be linked', completionError);
          setCompletionWarning('La entrega se ha guardado, pero la tarea prevista sigue pendiente. Puedes revisarla desde Hoy.');
        }
      }

      setSavedKg(kg);
      setSaved(true);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (cause) {
      console.error(cause);
      setError('No se ha podido guardar la entrega de cosecha.');
    } finally {
      setSaving(false);
    }
  }

  function confirmPreview() {
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
    setSavedKg(demoDelivery.kilograms);
    setSaved(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  if (saved) {
    return <section className="record-success card ocr-success">
      <div className="success-mark">✓</div>
      <span className="eyebrow dark">{apiMode ? 'COSECHA GUARDADA EN MÁGINA' : 'COSECHA GUARDADA · PREVIEW'}</span>
      <h1>{savedKg?.toLocaleString('es-ES')} kg en {context.name}</h1>
      <p>{apiMode ? 'La entrega estructurada ya forma parte de la finca y de la campaña. El rendimiento podrá llegar después.' : 'El albarán de demostración se ha convertido en un registro local de preview.'}</p>
      {completionWarning ? <p className="form-error" role="status">{completionWarning}</p> : plannedEventId && apiMode ? <p>✓ La tarea prevista ha quedado enlazada a esta entrega.</p> : null}
      <div className="record-actions"><button type="button" className="secondary-action" onClick={() => setSaved(false)}>Registrar otra</button><Link href={context.returnHref} className="primary action-link">Volver a la finca <ArrowIcon/></Link></div>
    </section>;
  }

  if (apiMode) {
    return <>
      <header className="page-title"><span className="eyebrow dark">MI CAMPO · COSECHA · {context.name.toUpperCase()}</span><h1>Registrar entrega</h1><p>Primero guardamos peso y albarán. El rendimiento se incorpora cuando lo comunique la cooperativa o almazara.</p></header>
      <form className="quick-record-form" onSubmit={saveRemote}>
        <section className="card record-panel"><div className="record-fields">
          <label className="record-field"><span>Fecha</span><input className="record-control" name="date" type="date" required /></label>
          <label className="record-field"><span>Peso</span><div className="record-input-wrap"><input className="record-control" name="kg" type="number" step="any" min="0.01" required /><b className="record-suffix">kg</b></div></label>
          <label className="record-field"><span>Cooperativa / almazara</span><input className="record-control" name="cooperative" /></label>
          <label className="record-field"><span>Nº albarán</span><input className="record-control" name="ticket" /></label>
        </div></section>
        <section className="card register-principle"><div><strong>Entrega ≠ rendimiento ≠ liquidación ≠ cobro</strong><small>Mágina conserva cada momento por separado para no inventar datos económicos o productivos.</small></div></section>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        <section className="record-save-bar"><small>La entrega se guardará en el servidor real.</small><button className="primary" type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Guardar entrega →'}</button></section>
      </form>
    </>;
  }

  const fields = [
    ['Finca', context.name],
    ['Cooperativa', demoDelivery.cooperative],
    ['Fecha', demoDelivery.date],
    ['Nº albarán', demoDelivery.ticketNumber],
    ['Peso', `${demoDelivery.kilograms.toLocaleString('es-ES')} kg`],
  ] as const;

  return <>
    <header className="page-title"><span className="eyebrow dark">REGISTRO INTELIGENTE · PREVIEW</span><h1>Registrar cosecha</h1><p>Demostración del flujo OCR. Los campos críticos siempre requieren confirmación.</p></header>
    <div className="stepper premium-stepper"><div className="step active"><b>✓</b><span>Foto</span></div><div className="step active"><b>2</b><span>OCR</span></div><div className="step"><b>3</b><span>Confirmar</span></div></div>
    <section className="card receipt-preview" aria-label="Vista del albarán fotografiado"><div className="receipt-status">✓ Foto leída</div></section>
    <section className="section card detected-card">
      <div className="detected-head"><div><span className="eyebrow dark">OCR DE DEMOSTRACIÓN</span><h2>Datos detectados</h2><p>Revisa antes de guardar en {context.name}.</p></div><span className="confidence-pill">Demo</span></div>
      <div className="data-list clean-list">{fields.map(([label,value])=><div className="data-row" key={label}><span>{label}</span><b>{value}</b></div>)}</div>
      <div className="validation-note"><span>✓</span><div><strong>Lectura propuesta</strong><small>La preview nunca se utiliza como dato remoto real.</small></div></div>
    </section>
    <section className="ocr-actions"><button className="primary" type="button" disabled={!ready} onClick={confirmPreview}>Confirmar demo →</button></section>
  </>;
}