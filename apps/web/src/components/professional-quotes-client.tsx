'use client';

import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadWorkDirectory, type CustomerSiteOption, type WorkPartyOption } from '@/lib/work-api-source';
import { createProfessionalQuote, convertProfessionalQuote, loadProfessionalQuotes, updateProfessionalQuoteStatus, type ProfessionalQuote } from '@/lib/professional-quote-source';

function money(value: number) {
  return value.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
}

export function ProfessionalQuotesClient() {
  const { selectedWorkspaceId } = useAuth();
  const [quotes, setQuotes] = useState<ProfessionalQuote[]>([]);
  const [customers, setCustomers] = useState<WorkPartyOption[]>([]);
  const [sites, setSites] = useState<CustomerSiteOption[]>([]);
  const [selectedQuoteId, setSelectedQuoteId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function reload() {
    if (!selectedWorkspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const [directory, quoteRows] = await Promise.all([loadWorkDirectory(selectedWorkspaceId), loadProfessionalQuotes(selectedWorkspaceId)]);
      setCustomers(directory.parties.filter((party) => party.roles?.includes('customer')));
      setSites(directory.sites);
      setQuotes(quoteRows);
    } catch (cause) {
      console.error('Unable to load professional quotes', cause);
      setError('No se han podido cargar los presupuestos.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void reload(); }, [selectedWorkspaceId]);

  const selectedQuote = useMemo(() => quotes.find((quote) => quote.id === selectedQuoteId) ?? null, [quotes, selectedQuoteId]);

  async function createQuote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId) return;
    const form = new FormData(event.currentTarget);
    const customerId = String(form.get('customer') ?? '');
    const siteId = String(form.get('site') ?? '');
    const title = String(form.get('title') ?? '').trim();
    const quoteNumber = String(form.get('number') ?? '').trim();
    const issuedOn = String(form.get('issued_on') ?? '').trim();
    const validUntil = String(form.get('valid_until') ?? '').trim();
    const quantity = Number(String(form.get('quantity') ?? '1').replace(',', '.'));
    const unitPrice = Number(String(form.get('unit_price') ?? '').replace(',', '.'));
    const taxRate = Number(String(form.get('tax_rate') ?? '21').replace(',', '.')) || 0;
    const status = String(form.get('status') ?? 'draft') as 'draft' | 'sent';
    if (!customerId || !title || !Number.isFinite(quantity) || quantity <= 0 || !Number.isFinite(unitPrice) || unitPrice < 0) {
      setError('Completa cliente, concepto, cantidad e importe.');
      return;
    }
    const subtotal = Number((quantity * unitPrice).toFixed(2));
    const tax = Number((subtotal * taxRate / 100).toFixed(2));
    const total = Number((subtotal + tax).toFixed(2));
    try {
      await createProfessionalQuote(selectedWorkspaceId, {
        customer_party_id: customerId,
        customer_site_id: siteId || undefined,
        quote_number: quoteNumber || undefined,
        title,
        issued_on: issuedOn || undefined,
        valid_until: validUntil || undefined,
        status,
        subtotal_eur: subtotal,
        tax_eur: tax,
        total_eur: total,
        lines: [{ description: title, quantity, unit: 'servicio', unit_price_eur: unitPrice, line_total_eur: subtotal }],
      });
      event.currentTarget.reset();
      await reload();
    } catch (cause) {
      console.error('Unable to create quote', cause);
      setError('No se ha podido guardar el presupuesto. Comprueba el número y los importes.');
    }
  }

  async function changeStatus(quoteId: string, status: 'sent' | 'accepted' | 'rejected' | 'expired') {
    if (!selectedWorkspaceId) return;
    try {
      await updateProfessionalQuoteStatus(selectedWorkspaceId, quoteId, status);
      await reload();
    } catch (cause) {
      console.error('Unable to update quote status', cause);
      setError('No se ha podido actualizar el estado del presupuesto.');
    }
  }

  async function convert(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !selectedQuote) return;
    const form = new FormData(event.currentTarget);
    const siteId = String(form.get('site') ?? selectedQuote.customer_site_id ?? '');
    const occurredOn = String(form.get('occurred_on') ?? '');
    const type = String(form.get('type') ?? 'manual-work');
    const labor = Number(String(form.get('labor') ?? '0').replace(',', '.')) || 0;
    const other = Number(String(form.get('other') ?? '0').replace(',', '.')) || 0;
    if (!siteId || !occurredOn) {
      setError('Para convertir, selecciona finca/sitio del cliente y fecha del trabajo.');
      return;
    }
    try {
      await convertProfessionalQuote(selectedWorkspaceId, selectedQuote.id, {
        customer_site_id: siteId,
        type,
        occurred_on: occurredOn,
        title: selectedQuote.title,
        labor_cost_eur: labor,
        other_cost_eur: other,
      });
      setSelectedQuoteId(null);
      await reload();
    } catch (cause) {
      console.error('Unable to convert quote', cause);
      setError('No se ha podido convertir el presupuesto. Debe estar aceptado y no haberse convertido antes.');
    }
  }

  if (loading) return <section className="card"><p>Cargando presupuestos…</p></section>;

  return <>
    <header className="page-title"><span className="eyebrow dark">MI CAMPO · PROFESIONAL</span><h1>Presupuestos</h1><p>Presupuesta primero y convierte a trabajo solo cuando el cliente lo acepte.</p></header>

    <section className="section"><div className="section-head"><h2>Nuevo presupuesto</h2></div>
      <form className="quick-record-form" onSubmit={createQuote}>
        <section className="card record-panel"><div className="record-fields">
          <label className="record-field wide"><span>Cliente</span><select className="record-control" name="customer" required><option value="">Seleccionar</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.display_name}</option>)}</select></label>
          <label className="record-field wide"><span>Finca / sitio del cliente</span><select className="record-control" name="site"><option value="">Sin sitio todavía</option>{sites.map((site) => <option key={site.id} value={site.id}>{site.customer_name ? `${site.customer_name} · ` : ''}{site.name}</option>)}</select></label>
          <label className="record-field wide"><span>Concepto</span><input className="record-control" name="title" required /></label>
          <label className="record-field"><span>Nº presupuesto</span><input className="record-control" name="number" placeholder="P-2026-001" /></label>
          <label className="record-field"><span>Estado inicial</span><select className="record-control" name="status" defaultValue="draft"><option value="draft">Borrador</option><option value="sent">Enviado</option></select></label>
          <label className="record-field"><span>Fecha</span><input className="record-control" name="issued_on" type="date" /></label>
          <label className="record-field"><span>Válido hasta</span><input className="record-control" name="valid_until" type="date" /></label>
          <label className="record-field"><span>Cantidad</span><input className="record-control" name="quantity" type="number" min="0.001" step="0.001" defaultValue="1" required /></label>
          <label className="record-field"><span>Precio unidad</span><input className="record-control" name="unit_price" type="number" min="0" step="0.01" required /></label>
          <label className="record-field"><span>IVA %</span><input className="record-control" name="tax_rate" type="number" min="0" max="100" step="0.01" defaultValue="21" /></label>
        </div></section>
        <section className="record-save-bar"><small>El presupuesto no crea ningún trabajo.</small><button className="primary" type="submit">Guardar presupuesto →</button></section>
      </form>
    </section>

    {error ? <p className="form-error" role="alert">{error}</p> : null}

    <section className="section"><div className="section-head"><h2>Histórico</h2><span className="subtle">{quotes.length}</span></div>
      <div className="activity-list">{quotes.map((quote) => {
        const quoted = Number(quote.total_eur ?? 0);
        const actualCost = Number(quote.actual_cost_eur ?? 0);
        const invoiced = Number(quote.invoice_total_eur ?? 0);
        return <article className="card activity-item" key={quote.id}><div><small>{quote.quote_number || 'Sin número'} · {quote.status}</small><h3>{quote.title}</h3><p>{quote.customer_name}{quote.site_name ? ` · ${quote.site_name}` : ''}</p>{quote.status === 'converted' ? <small>Presupuestado {money(quoted)} · coste real {money(actualCost)}{quote.invoice_id ? ` · facturado ${money(invoiced)}` : ' · aún sin factura'}</small> : null}</div><div><strong>{money(quoted)}</strong><div className="record-actions">{quote.status === 'draft' ? <button className="secondary-action" type="button" onClick={() => void changeStatus(quote.id, 'sent')}>Marcar enviado</button> : null}{quote.status === 'sent' ? <><button className="secondary-action" type="button" onClick={() => void changeStatus(quote.id, 'accepted')}>Aceptar</button><button className="secondary-action" type="button" onClick={() => void changeStatus(quote.id, 'rejected')}>Rechazar</button></> : null}{quote.status === 'accepted' ? <button className="primary" type="button" onClick={() => setSelectedQuoteId(quote.id)}>Convertir a trabajo</button> : null}</div></div></article>;
      })}</div>
    </section>

    {selectedQuote ? <section className="section"><div className="section-head"><h2>Convertir presupuesto aceptado</h2></div><form className="quick-record-form" onSubmit={convert}><section className="card record-panel"><div className="record-fields">
      <label className="record-field wide"><span>Finca / sitio</span><select className="record-control" name="site" defaultValue={selectedQuote.customer_site_id ?? ''} required><option value="">Seleccionar</option>{sites.filter((site) => site.customer_party_id === selectedQuote.customer_party_id).map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}</select></label>
      <label className="record-field"><span>Fecha trabajo</span><input className="record-control" name="occurred_on" type="date" required /></label>
      <label className="record-field"><span>Tipo</span><select className="record-control" name="type" defaultValue="manual-work"><option value="manual-work">Trabajo manual</option><option value="pruning">Poda</option><option value="harvest">Recolección</option><option value="treatment">Tratamiento</option><option value="transport">Transporte</option><option value="machinery-work">Maquinaria</option><option value="other">Otro</option></select></label>
      <label className="record-field"><span>Coste real mano de obra</span><input className="record-control" name="labor" type="number" min="0" step="0.01" defaultValue="0" /></label>
      <label className="record-field"><span>Otros costes reales</span><input className="record-control" name="other" type="number" min="0" step="0.01" defaultValue="0" /></label>
    </div></section><section className="record-save-bar"><small>El trabajo conservará el presupuesto de origen y su importe acordado.</small><button className="primary" type="submit">Crear trabajo desde presupuesto →</button></section></form></section> : null}
  </>;
}
