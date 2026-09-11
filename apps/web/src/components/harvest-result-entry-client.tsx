'use client';

import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { apiFetch } from '@/lib/api-client';
import { linkDocumentToDomain } from '@/lib/document-data-source';
import { useFieldContext } from '@/lib/use-field-context';

type DeliveryOption = {
  delivery_id: string;
  delivery_at: string;
  cooperative_or_mill: string | null;
  ticket_number: string | null;
  kg: number;
  yield_percent: number | null;
  result_date: string | null;
};

type HarvestSummaryResponse = { deliveries: DeliveryOption[] };

export function HarvestResultEntryClient() {
  const params = useSearchParams();
  const { context, ready, found } = useFieldContext();
  const { selectedWorkspaceId } = useAuth();
  const sourceDocumentId = params.get('sourceDocumentId');
  const prefillDate = params.get('prefillDate') ?? new Date().toISOString().slice(0, 10);
  const prefillYield = params.get('prefillYield') ?? '';
  const prefillMoisture = params.get('prefillMoisture') ?? '';
  const prefillAcidity = params.get('prefillAcidity') ?? '';
  const prefillTicket = params.get('prefillTicket') ?? '';
  const [deliveries, setDeliveries] = useState<DeliveryOption[]>([]);
  const [deliveryId, setDeliveryId] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sourceLinkWarning, setSourceLinkWarning] = useState<string | null>(null);
  const [sourceLinked, setSourceLinked] = useState(false);

  useEffect(() => {
    if (!ready || !found || context.source !== 'api' || !selectedWorkspaceId) { setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    apiFetch<HarvestSummaryResponse>(`/api/v1/fields/${encodeURIComponent(context.id)}/harvest-summary`, { workspaceId: selectedWorkspaceId })
      .then((response) => {
        if (cancelled) return;
        setDeliveries(response.deliveries);
        const ticketMatch = prefillTicket ? response.deliveries.find((item) => item.ticket_number === prefillTicket) : undefined;
        const pending = response.deliveries.find((item) => item.yield_percent == null);
        setDeliveryId(ticketMatch?.delivery_id ?? pending?.delivery_id ?? response.deliveries[0]?.delivery_id ?? '');
      })
      .catch((cause) => { console.error('Unable to load harvest deliveries', cause); if (!cancelled) setError('No se han podido cargar las entregas de esta finca.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [context.id, context.source, found, prefillTicket, ready, selectedWorkspaceId]);

  const selectedDelivery = useMemo(() => deliveries.find((item) => item.delivery_id === deliveryId), [deliveries, deliveryId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !deliveryId || saving) return;
    const form = new FormData(event.currentTarget);
    const resultDate = String(form.get('date') ?? '');
    const yieldPercent = Number(String(form.get('yield') ?? '').replace(',', '.'));
    const moistureRaw = String(form.get('moisture') ?? '').trim();
    const acidityRaw = String(form.get('acidity') ?? '').trim();
    const moisture = moistureRaw ? Number(moistureRaw.replace(',', '.')) : undefined;
    const acidity = acidityRaw ? Number(acidityRaw.replace(',', '.')) : undefined;
    if (!resultDate || !Number.isFinite(yieldPercent) || yieldPercent <= 0) { setError('Indica una entrega, fecha y rendimiento válidos.'); return; }
    try {
      setSaving(true);
      setError(null);
      setSourceLinkWarning(null);
      setSourceLinked(false);
      const response = await apiFetch<{ result: { id: string } }>(`/api/v1/deliveries/${encodeURIComponent(deliveryId)}/results`, {
        method: 'POST',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify({
          client_operation_id: crypto.randomUUID(),
          result_date: resultDate,
          yield_percent: yieldPercent,
          moisture_percent: moisture !== undefined && Number.isFinite(moisture) ? moisture : undefined,
          acidity_percent: acidity !== undefined && Number.isFinite(acidity) ? acidity : undefined,
        }),
      });
      if (sourceDocumentId) {
        try {
          await linkDocumentToDomain({ workspaceId: selectedWorkspaceId, documentId: sourceDocumentId, fieldId: context.id, domainType: 'harvest_result', domainRecordId: response.result.id });
          setSourceLinked(true);
        } catch (linkError) {
          console.warn('Harvest result saved but source document link failed', linkError);
          setSourceLinkWarning('El rendimiento se ha guardado, pero el documento de resultado no pudo enlazarse automáticamente. Sigue conservado en la finca.');
        }
      }
      setSaved(true);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (cause) {
      console.error('Unable to save harvest result', cause);
      setError('No se ha podido guardar el rendimiento.');
    } finally { setSaving(false); }
  }

  if (!ready || loading) return <section className="card"><p>Cargando entregas…</p></section>;
  if (!found || context.source !== 'api') return <section className="card"><h1>Finca no disponible</h1><Link href="/mi-campo">Volver a Mi Campo</Link></section>;
  if (saved) return <section className="record-success card"><div className="success-mark">✓</div><h1>Rendimiento guardado</h1><p>El resultado ha quedado asociado a la entrega seleccionada de {context.name}.</p>{sourceDocumentId ? <p>✓ Partía de un documento revisado y solo se guardó después de tu confirmación.</p> : null}{sourceLinked ? <p>✓ El documento de origen ha quedado enlazado al resultado.</p> : null}{sourceLinkWarning ? <p className="form-error" role="status">{sourceLinkWarning}</p> : null}<Link className="primary action-link" href={context.returnHref}>Volver a la finca</Link></section>;

  return <><header className="page-title"><span className="eyebrow dark">MI CAMPO · COSECHA · {context.name.toUpperCase()}</span><h1>Registrar rendimiento</h1><p>Selecciona expresamente la entrega a la que pertenece el resultado.</p></header>
    {sourceDocumentId ? <section className="card register-principle"><div><strong>Datos prellenados desde un resultado revisado</strong><small>El OCR no elige la entrega por ti. Comprueba el albarán y confirma antes de guardar.</small></div></section> : null}
    <form className="quick-record-form" onSubmit={submit}><section className="card record-panel"><div className="record-fields">
      <label className="record-field wide"><span>Entrega</span><select className="record-control" value={deliveryId} onChange={(event) => setDeliveryId(event.target.value)} required><option value="" disabled>Seleccionar entrega</option>{deliveries.map((item) => <option key={item.delivery_id} value={item.delivery_id}>{item.delivery_at.slice(0, 10)} · {item.kg.toLocaleString('es-ES')} kg{item.ticket_number ? ` · ${item.ticket_number}` : ''}{item.yield_percent != null ? ` · ya ${item.yield_percent}%` : ''}</option>)}</select><small>{selectedDelivery?.yield_percent != null ? 'Esta entrega ya tiene resultado: al guardar se conservará el anterior como superseded.' : 'Prioriza una entrega todavía pendiente de resultado.'}</small></label>
      <label className="record-field"><span>Fecha resultado</span><input className="record-control" name="date" type="date" defaultValue={prefillDate} required /></label>
      <label className="record-field"><span>Rendimiento</span><div className="record-input-wrap"><input className="record-control" name="yield" type="number" step="any" min="0.01" defaultValue={prefillYield} required /><b className="record-suffix">%</b></div></label>
      <label className="record-field"><span>Humedad</span><div className="record-input-wrap"><input className="record-control" name="moisture" type="number" step="any" min="0" defaultValue={prefillMoisture} /><b className="record-suffix">%</b></div></label>
      <label className="record-field"><span>Acidez</span><div className="record-input-wrap"><input className="record-control" name="acidity" type="number" step="any" min="0" defaultValue={prefillAcidity} /><b className="record-suffix">%</b></div></label>
    </div></section>{error ? <p className="form-error" role="alert">{error}</p> : null}<section className="record-save-bar"><small>El resultado quedará separado de la entrega, liquidación y cobro.</small><button className="primary" type="submit" disabled={!deliveryId || saving}>{saving ? 'Guardando…' : 'Guardar rendimiento →'}</button></section></form></>;
}
