'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { apiFetch } from '@/lib/api-client';

type Receivable = {
  id: string;
  title: string;
  occurred_on: string;
  customer_name: string | null;
  charge_eur: number | string | null;
  collected_from_movements_eur: number | string;
  pending_eur: number | string;
};

function money(value: number) {
  return value.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function ProfessionalCollectionEntryClient() {
  const params = useSearchParams();
  const requestedWorkId = params.get('workId') ?? '';
  const { selectedWorkspaceId, status } = useAuth();
  const [rows, setRows] = useState<Receivable[]>([]);
  const [workId, setWorkId] = useState(requestedWorkId);
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (status !== 'authenticated' || !selectedWorkspaceId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    apiFetch<{ receivables: Receivable[] }>('/api/v1/works/receivables', { workspaceId: selectedWorkspaceId })
      .then((response) => {
        if (cancelled) return;
        const pending = response.receivables.filter((item) => Number(item.pending_eur) > 0.009);
        setRows(pending);
        const selected = pending.find((item) => item.id === requestedWorkId) ?? pending[0];
        if (selected) {
          setWorkId(selected.id);
          setAmount(Number(selected.pending_eur).toFixed(2));
        }
      })
      .catch((cause) => {
        console.error('Unable to load professional receivables', cause);
        if (!cancelled) setError('No se han podido cargar los trabajos pendientes de cobro.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [requestedWorkId, selectedWorkspaceId, status]);

  const selected = useMemo(() => rows.find((item) => item.id === workId), [rows, workId]);
  const pending = selected ? Number(selected.pending_eur) : 0;

  function chooseWork(id: string) {
    setWorkId(id);
    const next = rows.find((item) => item.id === id);
    if (next) setAmount(Number(next.pending_eur).toFixed(2));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !workId || saving) return;
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
    if (amountEur > pending + 0.001) {
      setError(`El cobro supera el pendiente de ${money(pending)} €.`);
      return;
    }

    try {
      setSaving(true);
      setError(null);
      await apiFetch(`/api/v1/works/${encodeURIComponent(workId)}/collections`, {
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
      setSaved(true);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (cause) {
      console.error('Unable to save professional collection', cause);
      setError('No se ha podido guardar el cobro. Revisa que no supere el pendiente actual.');
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <section className="card"><p>Cargando trabajos pendientes…</p></section>;
  if (saved) return <section className="record-success card"><div className="success-mark">✓</div><h1>Cobro registrado</h1><p>El movimiento queda guardado con fecha, importe y referencia, y reduce el saldo pendiente del trabajo.</p><Link className="primary action-link" href="/mi-campo/profesional">Volver a Profesional</Link></section>;

  return <>
    <header className="page-title"><span className="eyebrow dark">MI CAMPO · PROFESIONAL</span><h1>Registrar cobro</h1><p>Registra un cobro parcial o completa todo lo pendiente de un trabajo para tercero.</p></header>
    {rows.length === 0 ? <section className="card"><h3>Sin cobros pendientes</h3><p>No hay trabajos profesionales con saldo pendiente.</p><Link className="secondary-action action-link" href="/mi-campo/profesional">Volver a Profesional</Link></section> : <form className="quick-record-form" onSubmit={submit}>
      <section className="card record-panel"><div className="record-fields">
        <label className="record-field wide"><span>Trabajo</span><select className="record-control" value={workId} onChange={(event) => chooseWork(event.target.value)} required><option value="" disabled>Seleccionar trabajo</option>{rows.map((item) => <option key={item.id} value={item.id}>{item.customer_name || 'Cliente'} · {item.title} · pendiente {money(Number(item.pending_eur))} €</option>)}</select></label>
        {selected ? <div className="record-field wide"><span>Estado</span><div className="card"><strong>{selected.title}</strong><small>{selected.customer_name || 'Cliente'} · facturado {money(Number(selected.charge_eur ?? 0))} € · cobrado {money(Number(selected.collected_from_movements_eur))} € · pendiente {money(pending)} €</small></div></div> : null}
        <label className="record-field"><span>Fecha</span><input className="record-control" name="date" type="date" defaultValue={new Date().toISOString().slice(0, 10)} required /></label>
        <label className="record-field"><span>Importe</span><div className="record-input-wrap"><input className="record-control" name="amount" type="number" min="0.01" max={pending || undefined} step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} required /><b className="record-suffix">€</b></div><button type="button" className="secondary-action" onClick={() => setAmount(pending.toFixed(2))}>Todo lo pendiente</button></label>
        <label className="record-field"><span>Método</span><select className="record-control" name="method" defaultValue="bank"><option value="bank">Banco / transferencia</option><option value="cash">Efectivo</option><option value="card">Tarjeta</option><option value="bizum">Bizum</option><option value="other">Otro</option></select></label>
        <label className="record-field"><span>Referencia</span><input className="record-control" name="reference" /></label>
        <label className="record-field wide"><span>Notas</span><textarea className="record-control" name="notes" rows={3} /></label>
      </div></section>
      <section className="card register-principle"><div><strong>Cobro auditable</strong><small>Cada pago queda como movimiento independiente. El servidor bloquea el trabajo y recalcula el pendiente antes de aceptar el importe.</small></div></section>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <section className="record-save-bar"><small>El importe facturado del trabajo no cambia al registrar un cobro.</small><button className="primary" type="submit" disabled={!workId || saving}>{saving ? 'Guardando…' : 'Guardar cobro →'}</button></section>
    </form>}
  </>;
}
