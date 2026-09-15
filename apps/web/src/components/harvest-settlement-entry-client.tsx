'use client';

import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { apiFetch } from '@/lib/api-client';
import { useFieldContext, withFieldQuery } from '@/lib/use-field-context';

type Candidate = {
  id: string;
  campaign_id: string | null;
  delivery_at: string;
  cooperative_or_mill: string | null;
  ticket_number: string | null;
  total_kg: number;
  fields: Array<{ field_id: string; field_name: string; kg: number }>;
  settlement_id: string | null;
  settlement_number: string | null;
};

export function HarvestSettlementEntryClient() {
  const params = useSearchParams();
  const { context, ready, found } = useFieldContext();
  const { selectedWorkspaceId } = useAuth();
  const sourceDocumentId = params.get('sourceDocumentId');
  const [deliveries, setDeliveries] = useState<Candidate[]>([]);
  const [campaignId, setCampaignId] = useState<string | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [warning, setWarning] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const prefillDate = params.get('prefillDate') ?? new Date().toISOString().slice(0, 10);
  const prefillGross = params.get('prefillGross') ?? '';
  const prefillDeductions = params.get('prefillDeductions') ?? '0';
  const prefillNet = params.get('prefillNet') ?? '';
  const prefillNumber = params.get('prefillNumber') ?? '';
  const prefillCounterparty = params.get('prefillCounterparty') ?? '';

  useEffect(() => {
    if (!ready || !found || context.source !== 'api' || !selectedWorkspaceId) { setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    setError(null);
    setSelected([]);
    apiFetch<{ campaign_id: string | null; deliveries: Candidate[] }>('/api/v1/harvest-settlement-candidates', { workspaceId: selectedWorkspaceId })
      .then((response) => {
        if (cancelled) return;
        setCampaignId(response.campaign_id);
        const related = response.deliveries.filter((item) => item.fields.some((field) => field.field_id === context.id));
        setDeliveries(related);
        const available = related.filter((item) => !item.settlement_id);
        if (available.length === 1) setSelected([available[0].id]);
      })
      .catch((cause) => { console.error('Unable to load settlement candidates', cause); if (!cancelled) setError('No se han podido cargar las entregas disponibles de esta finca.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [context.id, context.source, found, ready, selectedWorkspaceId]);

  const availableDeliveries = useMemo(() => deliveries.filter((item) => !item.settlement_id), [deliveries]);
  const settledDeliveries = useMemo(() => deliveries.filter((item) => Boolean(item.settlement_id)), [deliveries]);
  const selectedRows = useMemo(() => availableDeliveries.filter((item) => selected.includes(item.id)), [availableDeliveries, selected]);
  const selectedKg = selectedRows.reduce((sum, item) => sum + Number(item.total_kg), 0);
  const deliveryHref = withFieldQuery('/mi-campo/registrar/cosecha', context.id, context.source);
  const collectionHref = withFieldQuery('/mi-campo/registrar/cobro', context.id, context.source);

  function toggle(id: string) {
    setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !selected.length || saving) return;
    const form = new FormData(event.currentTarget);
    const gross = Number(String(form.get('gross') ?? '').replace(',', '.'));
    const deductions = Number(String(form.get('deductions') ?? '0').replace(',', '.'));
    const net = Number(String(form.get('net') ?? '').replace(',', '.'));
    const date = String(form.get('date') ?? '');
    if (!date || !Number.isFinite(gross) || !Number.isFinite(deductions) || !Number.isFinite(net)) {
      setError('Revisa fecha e importes.'); return;
    }
    if (Math.abs((gross - deductions) - net) > 0.02) {
      setError('El neto debe coincidir con bruto menos deducciones.'); return;
    }
    try {
      setSaving(true); setError(null); setWarning(null);
      const response = await apiFetch<{ settlement: { id: string } }>('/api/v1/harvest-settlements', {
        method: 'POST', workspaceId: selectedWorkspaceId,
        body: JSON.stringify({
          client_operation_id: crypto.randomUUID(), campaign_id: campaignId ?? undefined,
          counterparty_name: String(form.get('counterparty') ?? '').trim() || undefined,
          settlement_number: String(form.get('number') ?? '').trim() || undefined,
          settled_on: date, basis: 'olive_kg', gross_eur: gross, deductions_eur: deductions, net_eur: net,
          delivery_ids: selected,
        }),
      });
      if (sourceDocumentId) {
        try {
          await apiFetch(`/api/v1/documents/${encodeURIComponent(sourceDocumentId)}/link-domain`, {
            method: 'POST', workspaceId: selectedWorkspaceId,
            body: JSON.stringify({ field_id: context.id, domain_type: 'harvest_settlement', domain_record_id: response.settlement.id }),
          });
        } catch (cause) {
          console.warn('Settlement saved but source document could not be linked', cause);
          setWarning('La liquidación se ha guardado, pero el documento sigue vinculado solo a la finca.');
        }
      }
      setSaved(true); window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (cause) {
      console.error('Unable to save harvest settlement', cause);
      setError('No se ha podido guardar la liquidación. Comprueba que las entregas sigan pendientes de liquidar.');
    } finally { setSaving(false); }
  }

  if (!ready || loading) return <section className="card"><p>Cargando entregas disponibles…</p></section>;
  if (!found || context.source !== 'api') return <section className="card"><h1>Finca no disponible</h1><Link href="/mi-campo">Volver</Link></section>;
  if (saved) return <section className="record-success card"><div className="success-mark">✓</div><h1>Liquidación guardada</h1><p>La liquidación queda separada de los cobros. Has asociado {selected.length} entrega(s), {selectedKg.toLocaleString('es-ES')} kg.</p>{warning ? <p className="form-error">{warning}</p> : null}<div className="record-actions"><Link className="primary action-link" href={collectionHref}>Registrar cobro →</Link><Link className="secondary-action action-link" href="/mi-campo/campana">Ver campaña</Link><Link className="secondary-action action-link" href={context.returnHref}>Volver a la finca</Link></div></section>;

  if (!availableDeliveries.length && !error) return <><header className="page-title"><span className="eyebrow dark">MI CAMPO · COSECHA · {context.name.toUpperCase()}</span><h1>Registrar liquidación</h1><p>Una liquidación solo puede vincular entregas reales de esta finca que todavía no estén liquidadas.</p></header><section className="card"><h2>No hay entregas pendientes de liquidar</h2><p>{settledDeliveries.length ? `Las ${settledDeliveries.length} entrega(s) relacionadas con ${context.name} ya están incluidas en una liquidación.` : `Todavía no hay entregas de cosecha registradas en ${context.name}.`}</p><div className="record-actions"><Link className="primary action-link" href={deliveryHref}>Registrar entrega →</Link><Link className="secondary-action action-link" href="/mi-campo/campana">Ver campaña</Link></div></section></>;

  return <>
    <header className="page-title"><span className="eyebrow dark">MI CAMPO · COSECHA · {context.name.toUpperCase()}</span><h1>Registrar liquidación</h1><p>Selecciona exactamente qué entregas de esta finca incluye el documento. Una entrega mixta puede incluir también kilos de otras fincas.</p></header>
    {sourceDocumentId ? <section className="card register-principle"><div><strong>Datos prellenados desde una liquidación revisada</strong><small>Comprueba importes y albaranes antes de guardar.</small></div></section> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}
    <form className="quick-record-form" onSubmit={submit}>
      <section className="card record-panel"><div className="record-fields">
        <label className="record-field"><span>Fecha</span><input className="record-control" name="date" type="date" defaultValue={prefillDate} required /></label>
        <label className="record-field"><span>Nº liquidación</span><input className="record-control" name="number" defaultValue={prefillNumber} /></label>
        <label className="record-field wide"><span>Cooperativa / almazara</span><input className="record-control" name="counterparty" defaultValue={prefillCounterparty} /></label>
        <label className="record-field"><span>Bruto</span><input className="record-control" name="gross" type="number" step="0.01" min="0" defaultValue={prefillGross} required /></label>
        <label className="record-field"><span>Deducciones</span><input className="record-control" name="deductions" type="number" step="0.01" min="0" defaultValue={prefillDeductions} required /></label>
        <label className="record-field"><span>Neto</span><input className="record-control" name="net" type="number" step="0.01" min="0" defaultValue={prefillNet} required /></label>
      </div></section>
      <section className="card record-panel"><div className="section-head"><h2>Entregas incluidas</h2><span>{selected.length} seleccionadas · {selectedKg.toLocaleString('es-ES')} kg</span></div>
        <div className="today-list">{availableDeliveries.map((item) => <label className="feed-row" key={item.id}><input type="checkbox" checked={selected.includes(item.id)} onChange={() => toggle(item.id)} /><div className="feed-copy"><strong>{item.delivery_at.slice(0,10)} · {item.total_kg.toLocaleString('es-ES')} kg{item.ticket_number ? ` · ${item.ticket_number}` : ''}</strong><small>{item.fields.map((field) => `${field.field_name} ${field.kg.toLocaleString('es-ES')} kg`).join(' · ')}</small></div></label>)}</div>
        {settledDeliveries.length ? <p className="subtle">{settledDeliveries.length} entrega(s) de esta finca ya están liquidadas y no se pueden volver a seleccionar.</p> : null}
      </section>
      <section className="card register-principle"><div><strong>Liquidación ≠ cobro</strong><small>Guardar esta liquidación reconoce el importe devengado. El dinero realmente recibido se registra después como cobro.</small></div></section>
      <section className="record-save-bar"><small>Guardar la liquidación NO registra ningún cobro.</small><button className="primary" type="submit" disabled={!selected.length || saving}>{saving ? 'Guardando…' : 'Guardar liquidación →'}</button></section>
    </form>
  </>;
}
