'use client';

import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { createProfessionalInvoice, loadInvoiceCandidates, type InvoiceCandidateWork } from '@/lib/professional-invoice-source';
import { loadWorkDirectory, type WorkPartyOption } from '@/lib/work-api-source';

function money(value: number) {
  return value.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

type SavedInvoice = { id: string; customerId: string; status: 'draft' | 'issued'; firstWorkId: string | null };

export function ProfessionalInvoiceEntryClient() {
  const params = useSearchParams();
  const preselectedCustomerId = params.get('customerId') ?? '';
  const preselectedWorkId = params.get('workId') ?? '';
  const { selectedWorkspaceId } = useAuth();
  const [customers, setCustomers] = useState<WorkPartyOption[]>([]);
  const [customerId, setCustomerId] = useState(preselectedCustomerId);
  const [works, setWorks] = useState<InvoiceCandidateWork[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [taxPercent, setTaxPercent] = useState('21');
  const [status, setStatus] = useState<'draft' | 'issued'>('draft');
  const [loading, setLoading] = useState(true);
  const [worksLoading, setWorksLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedInvoice, setSavedInvoice] = useState<SavedInvoice | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedWorkspaceId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    loadWorkDirectory(selectedWorkspaceId)
      .then((directory) => {
        if (cancelled) return;
        const next = directory.parties.filter((party) => party.roles?.includes('customer'));
        setCustomers(next);
        const preferred = next.find((item) => item.id === preselectedCustomerId)?.id ?? next[0]?.id ?? '';
        setCustomerId(preferred);
      })
      .catch((cause) => {
        console.error('Unable to load customers', cause);
        if (!cancelled) setError('No se han podido cargar los clientes.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [preselectedCustomerId, selectedWorkspaceId]);

  useEffect(() => {
    if (!selectedWorkspaceId || !customerId) {
      setWorks([]);
      setSelectedIds([]);
      setWorksLoading(false);
      return;
    }
    let cancelled = false;
    setWorksLoading(true);
    setError(null);
    loadInvoiceCandidates(selectedWorkspaceId, customerId)
      .then((items) => {
        if (cancelled) return;
        setWorks(items);
        if (preselectedWorkId && items.some((item) => item.id === preselectedWorkId)) {
          setSelectedIds([preselectedWorkId]);
        } else {
          setSelectedIds(items.filter((item) => Number(item.charge_eur ?? 0) > 0).map((item) => item.id));
        }
      })
      .catch((cause) => {
        console.error('Unable to load invoice candidates', cause);
        if (!cancelled) {
          setWorks([]);
          setSelectedIds([]);
          setError('No se han podido cargar los trabajos pendientes de facturar.');
        }
      })
      .finally(() => { if (!cancelled) setWorksLoading(false); });
    return () => { cancelled = true; };
  }, [customerId, preselectedWorkId, selectedWorkspaceId]);

  const selectedWorks = useMemo(() => works.filter((item) => selectedIds.includes(item.id)), [selectedIds, works]);
  const total = selectedWorks.reduce((sum, item) => sum + Number(item.charge_eur ?? 0), 0);
  const taxRate = Math.max(Number(taxPercent.replace(',', '.')) || 0, 0);
  const subtotal = taxRate > 0 ? total / (1 + taxRate / 100) : total;
  const tax = total - subtotal;

  function toggleWork(id: string) {
    setSelectedIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !customerId || selectedWorks.length === 0 || saving) return;
    const form = new FormData(event.currentTarget);
    const invoiceNumber = String(form.get('invoice_number') ?? '').trim();
    const issuedOn = String(form.get('issued_on') ?? '').trim();
    const dueOn = String(form.get('due_on') ?? '').trim();
    const notes = String(form.get('notes') ?? '').trim();

    if (status === 'issued' && (!invoiceNumber || !issuedOn)) {
      setError('Para emitir la factura indica número y fecha de emisión.');
      return;
    }

    try {
      setSaving(true);
      setError(null);
      const invoice = await createProfessionalInvoice(selectedWorkspaceId, {
        customer_party_id: customerId,
        invoice_number: invoiceNumber || undefined,
        issued_on: issuedOn || undefined,
        due_on: dueOn || undefined,
        status,
        subtotal_eur: Number(subtotal.toFixed(2)),
        tax_eur: Number(tax.toFixed(2)),
        total_eur: Number(total.toFixed(2)),
        notes: notes || undefined,
        works: selectedWorks.map((item) => ({ work_id: item.id, amount_eur: Number(Number(item.charge_eur ?? 0).toFixed(2)) })),
      });
      setSavedInvoice({ id: invoice.id, customerId, status, firstWorkId: selectedWorks[0]?.id ?? null });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (cause) {
      console.error('Unable to create invoice', cause);
      setError('No se ha podido guardar la factura. Comprueba que los trabajos sigan sin facturar y que el número no esté repetido.');
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <section className="card"><p>Cargando clientes…</p></section>;
  if (savedInvoice) return <section className="record-success card"><div className="success-mark">✓</div><h1>{savedInvoice.status === 'issued' ? 'Factura emitida' : 'Borrador de factura guardado'}</h1><p>Los trabajos quedan vinculados a esta factura y ya no podrán incluirse en otra factura activa. Registrar la factura no registra ningún cobro.</p><div className="record-actions"><Link className="primary action-link" href={`/mi-campo/profesional/documento?type=invoice&id=${encodeURIComponent(savedInvoice.id)}`}>Ver / compartir factura →</Link>{savedInvoice.status === 'issued' && savedInvoice.firstWorkId ? <Link className="secondary-action action-link" href={`/mi-campo/profesional/cobrar?workId=${encodeURIComponent(savedInvoice.firstWorkId)}`}>Registrar cobro</Link> : null}<Link className="secondary-action action-link" href={`/mi-campo/profesional/cliente?id=${encodeURIComponent(savedInvoice.customerId)}`}>Ver cliente</Link><Link className="secondary-action action-link" href="/mi-campo/profesional">Profesional</Link></div></section>;

  return <>
    <header className="page-title"><span className="eyebrow dark">MI CAMPO · PROFESIONAL</span><h1>Nueva factura</h1><p>Agrupa trabajos del mismo cliente. El importe a cobrar de cada trabajo se considera total final; el IVA se desglosa dentro de ese total.</p></header>
    {preselectedWorkId ? <section className="card register-principle"><div><strong>Trabajo preseleccionado desde seguimiento comercial</strong><small>Comprueba cliente, trabajo, impuestos y estado antes de guardar.</small></div></section> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}

    {!customers.length ? <section className="card"><h2>Aún no hay clientes profesionales</h2><p>Registra un trabajo para un cliente para poder facturarlo después.</p><Link className="primary action-link" href="/mi-campo/registrar/trabajo">Registrar trabajo →</Link></section> : <form className="quick-record-form" onSubmit={submit}>
      <section className="card record-panel"><div className="record-fields">
        <label className="record-field wide"><span>Cliente</span><select className="record-control" value={customerId} onChange={(event) => setCustomerId(event.target.value)} required><option value="" disabled>Seleccionar cliente</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.display_name}</option>)}</select></label>
        <label className="record-field"><span>Estado</span><select className="record-control" value={status} onChange={(event) => setStatus(event.target.value as 'draft' | 'issued')}><option value="draft">Borrador</option><option value="issued">Emitida</option></select></label>
        <label className="record-field"><span>Nº factura</span><input className="record-control" name="invoice_number" placeholder="2026-001" required={status === 'issued'} /></label>
        <label className="record-field"><span>Fecha emisión</span><input className="record-control" name="issued_on" type="date" required={status === 'issued'} /></label>
        <label className="record-field"><span>Vencimiento</span><input className="record-control" name="due_on" type="date" /></label>
        <label className="record-field"><span>IVA incluido %</span><input className="record-control" type="number" min="0" max="100" step="0.01" value={taxPercent} onChange={(event) => setTaxPercent(event.target.value)} /></label>
        <label className="record-field wide"><span>Notas</span><textarea className="record-control" name="notes" rows={3} /></label>
      </div></section>

      <section className="section"><div className="section-head"><h2>Trabajos a facturar</h2><span className="subtle">{selectedWorks.length} seleccionados</span></div>
        {worksLoading ? <section className="card"><p>Cargando trabajos pendientes…</p></section> : works.length === 0 ? <section className="card"><h3>Sin trabajos pendientes de facturar</h3><p>Este cliente no tiene trabajos disponibles para una nueva factura.</p><div className="record-actions"><Link className="primary action-link" href={`/mi-campo/registrar/trabajo?customerId=${encodeURIComponent(customerId)}`}>Registrar trabajo →</Link><Link className="secondary-action action-link" href={`/mi-campo/profesional/presupuestos?customerId=${encodeURIComponent(customerId)}`}>Crear presupuesto</Link><Link className="secondary-action action-link" href={`/mi-campo/profesional/cliente?id=${encodeURIComponent(customerId)}`}>Ver cliente</Link></div></section> : <div className="activity-list">{works.map((work) => <label className="card activity-item" key={work.id}><div><input type="checkbox" checked={selectedIds.includes(work.id)} onChange={() => toggleWork(work.id)} /> <strong>{work.title}</strong><p>{work.occurred_on}{work.site_name ? ` · ${work.site_name}` : ''}</p></div><div><strong>{money(Number(work.charge_eur ?? 0))} €</strong><small>{Number(work.collected_eur ?? 0) > 0 ? `${money(Number(work.collected_eur))} € ya cobrados` : 'sin cobros'}</small></div></label>)}</div>}
      </section>

      <section className="card register-principle"><div><strong>Resumen</strong><small>Base {money(subtotal)} € · IVA incluido {money(tax)} € · total {money(total)} €</small></div></section>
      <section className="card register-principle"><div><strong>Factura ≠ cobro</strong><small>Emitir una factura no registra dinero recibido. Los cobros siguen siendo movimientos independientes por trabajo.</small></div></section>
      <section className="record-save-bar"><small>{status === 'draft' ? 'Podrás emitirla después.' : 'El número debe ser único y queda reservado incluso si después anulas la factura.'}</small><button className="primary" type="submit" disabled={saving || worksLoading || selectedWorks.length === 0}>{saving ? 'Guardando…' : status === 'draft' ? 'Guardar borrador →' : 'Emitir factura →'}</button></section>
    </form>}
  </>;
}
