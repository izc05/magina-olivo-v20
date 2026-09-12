'use client';

import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { apiFetch } from '@/lib/api-client';
import { linkDocumentToDomain } from '@/lib/document-data-source';
import { useFieldContext } from '@/lib/use-field-context';

type SettlementRow = {
  id: string;
  settlement_number: string | null;
  counterparty_name: string | null;
  settled_on: string;
  net_eur: number | string;
  collected_eur: number | string;
  pending_eur: number | string;
  delivery_count: number;
};

type SettlementsResponse = { settlements: SettlementRow[] };

function money(value: number) {
  return value.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function HarvestCollectionEntryClient() {
  const params = useSearchParams();
  const { context, ready, found } = useFieldContext();
  const { selectedWorkspaceId } = useAuth();
  const sourceDocumentId = params.get('sourceDocumentId');
  const prefillDate = params.get('prefillDate') ?? new Date().toISOString().slice(0, 10);
  const prefillAmount = params.get('prefillAmount') ?? '';
  const prefillReference = params.get('prefillReference') ?? '';

  const [settlements, setSettlements] = useState<SettlementRow[]>([]);
  const [settlementId, setSettlementId] = useState('');
  const [amount, setAmount] = useState(prefillAmount);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [linkWarning, setLinkWarning] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready || !found || context.source !== 'api' || !selectedWorkspaceId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    apiFetch<SettlementsResponse>(`/api/v1/harvest-settlements?fieldId=${encodeURIComponent(context.id)}`, { workspaceId: selectedWorkspaceId })
      .then((response) => {
        if (cancelled) return;
        const pending = response.settlements.filter((item) => Number(item.pending_eur) > 0.009);
        setSettlements(pending);
        const first = pending[0];
        if (first) {
          setSettlementId(first.id);
          if (!prefillAmount) setAmount(String(Number(first.pending_eur).toFixed(2)));
        }
      })
      .catch((cause) => {
        console.error('Unable to load settlements for collection', cause);
        if (!cancelled) setError('No se han podido cargar las liquidaciones pendientes de esta finca.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [context.id, context.source, found, prefillAmount, ready, selectedWorkspaceId]);

  const selected = useMemo(() => settlements.find((item) => item.id === settlementId), [settlementId, settlements]);
  const pending = selected ? Number(selected.pending_eur) : 0;

  function chooseSettlement(id: string) {
    setSettlementId(id);
    const next = settlements.find((item) => item.id === id);
    if (next && !prefillAmount) setAmount(String(Number(next.pending_eur).toFixed(2)));
  }

  function fillPending() {
    if (selected) setAmount(String(Number(selected.pending_eur).toFixed(2)));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !settlementId || saving) return;
    const form = new FormData(event.currentTarget);
    const collectedOn = String(form.get('date') ?? '');
    const amountEur = Number(String(form.get('amount') ?? '').replace(',', '.'));
    const method = String(form.get('method') ?? '').trim();
    const reference = String(form.get('reference') ?? '').trim();
    const notes = String(form.get('notes') ?? '').trim();
    if (!collectedOn || !Number.isFinite(amountEur) || amountEur <= 0) {
      setError('Indica fecha e importe válidos.');
      return;
    }
    if (amountEur > pending + 0.01) {
      setError(`El cobro supera el pendiente de ${money(pending)} €.`);
      return;
    }

    try {
      setSaving(true);
      setError(null);
      setLinkWarning(null);
      const response = await apiFetch<{ collection: { id: string } }>(`/api/v1/harvest-settlements/${encodeURIComponent(settlementId)}/collections`, {
        method: 'POST',
        workspaceId: selectedWorkspaceId,
        body: JSON.stringify({
          client_operation_id: crypto.randomUUID(),
          collected_on: collectedOn,
          amount_eur: amountEur,
          method: method || undefined,
          reference: reference || undefined,
          notes: notes || undefined,
        }),
      });

      if (sourceDocumentId) {
        try {
          await linkDocumentToDomain({
            workspaceId: selectedWorkspaceId,
            documentId: sourceDocumentId,
            fieldId: context.id,
            domainType: 'harvest_collection',
            domainRecordId: response.collection.id,
          });
        } catch (cause) {
          console.warn('Collection saved but source document could not be linked', cause);
          setLinkWarning('El cobro está guardado, pero el justificante no pudo enlazarse automáticamente. El documento sigue conservado en la finca.');
        }
      }

      setSaved(true);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (cause) {
      console.error('Unable to save harvest collection', cause);
      setError('No se ha podido guardar el cobro. Revisa que no supere el pendiente de la liquidación.');
    } finally {
      setSaving(false);
    }
  }

  if (!ready || loading) return <section className="card"><p>Cargando liquidaciones pendientes…</p></section>;
  if (!found || context.source !== 'api') return <section className="card"><h1>Finca no disponible</h1><Link href="/mi-campo">Volver a Mi Campo</Link></section>;
  if (saved) return <section className="record-success card"><div className="success-mark">✓</div><h1>Cobro registrado</h1><p>El cobro queda separado de la liquidación y reduce únicamente su saldo pendiente.</p>{sourceDocumentId ? <p>✓ Partía de un justificante revisado y solo se guardó después de tu confirmación.</p> : null}{linkWarning ? <p className="form-error" role="status">{linkWarning}</p> : null}<Link className="primary action-link" href={context.returnHref}>Volver a la finca</Link></section>;

  return <>
    <header className="page-title"><span className="eyebrow dark">MI CAMPO · COSECHA · {context.name.toUpperCase()}</span><h1>Registrar cobro</h1><p>Elige la liquidación de esta finca que realmente has cobrado. Puedes registrar un pago parcial o completar todo lo pendiente.</p></header>
    {sourceDocumentId ? <section className="card register-principle"><div><strong>Datos prellenados desde un justificante revisado</strong><small>Comprueba fecha, importe, referencia y liquidación antes de guardar.</small></div></section> : null}
    {settlements.length === 0 ? <section className="card"><p>No hay liquidaciones confirmadas de esta finca con saldo pendiente.</p><Link className="secondary-action action-link" href={context.returnHref}>Volver a la finca</Link></section> : <form className="quick-record-form" onSubmit={submit}>
      <section className="card record-panel"><div className="record-fields">
        <label className="record-field wide"><span>Liquidación</span><select className="record-control" value={settlementId} onChange={(event) => chooseSettlement(event.target.value)} required><option value="" disabled>Seleccionar liquidación</option>{settlements.map((item) => <option key={item.id} value={item.id}>{item.settlement_number || 'Sin nº'} · {item.counterparty_name || 'Sin contraparte'} · pendiente {money(Number(item.pending_eur))} €</option>)}</select></label>
        {selected ? <div className="record-field wide"><span>Estado</span><div className="card"><strong>Neto {money(Number(selected.net_eur))} €</strong><small>Cobrado {money(Number(selected.collected_eur))} € · pendiente {money(Number(selected.pending_eur))} € · {selected.delivery_count} entregas</small></div></div> : null}
        <label className="record-field"><span>Fecha cobro</span><input className="record-control" name="date" type="date" defaultValue={prefillDate} required /></label>
        <label className="record-field"><span>Importe</span><div className="record-input-wrap"><input className="record-control" name="amount" type="number" step="0.01" min="0.01" max={pending || undefined} value={amount} onChange={(event) => setAmount(event.target.value)} required /><b className="record-suffix">€</b></div><button type="button" className="secondary-action" onClick={fillPending}>Todo lo pendiente</button></label>
        <label className="record-field"><span>Método</span><select className="record-control" name="method" defaultValue="bank"><option value="bank">Banco / transferencia</option><option value="cash">Efectivo</option><option value="card">Tarjeta</option><option value="bizum">Bizum</option><option value="other">Otro</option></select></label>
        <label className="record-field"><span>Referencia</span><input className="record-control" name="reference" defaultValue={prefillReference} /></label>
        <label className="record-field wide"><span>Notas</span><textarea className="record-control" name="notes" rows={3} /></label>
      </div></section>
      <section className="card register-principle"><div><strong>Cobro ≠ liquidación</strong><small>Registrar este movimiento solo reduce el pendiente. No modifica entregas, rendimientos ni el neto de la liquidación.</small></div></section>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <section className="record-save-bar"><small>El servidor volverá a comprobar el saldo pendiente dentro de una transacción.</small><button className="primary" type="submit" disabled={!settlementId || saving}>{saving ? 'Guardando…' : 'Guardar cobro →'}</button></section>
    </form>}
  </>;
}
