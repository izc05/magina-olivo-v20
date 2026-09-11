'use client';

import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { loadWorkDirectory, type CustomerSiteOption, type WorkPartyOption } from '@/lib/work-api-source';
import { createProfessionalQuote, convertProfessionalQuote, loadProfessionalQuotes, updateProfessionalQuoteStatus, type ProfessionalQuote } from '@/lib/professional-quote-source';

function money(value: number) {
  return value.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
}

type ConvertedState = { workId: string; customerId: string; quoteId: string };

export function ProfessionalQuotesClient() {
  const params = useSearchParams();
  const requestedQuoteId = params.get('quoteId');
  const requestedCustomerId = params.get('customerId') ?? '';
  const { selectedWorkspaceId } = useAuth();
  const [quotes, setQuotes] = useState<ProfessionalQuote[]>([]);
  const [customers, setCustomers] = useState<WorkPartyOption[]>([]);
  const [sites, setSites] = useState<CustomerSiteOption[]>([]);
  const [createCustomerId, setCreateCustomerId] = useState(requestedCustomerId);
  const [createSiteId, setCreateSiteId] = useState('');
  const [selectedQuoteId, setSelectedQuoteId] = useState<string | null>(requestedQuoteId);
  const [createdQuoteId, setCreatedQuoteId] = useState<string | null>(null);
  const [converted, setConverted] = useState<ConvertedState | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function reload() {
    if (!selectedWorkspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const [directory, quoteRows] = await Promise.all([loadWorkDirectory(selectedWorkspaceId), loadProfessionalQuotes(selectedWorkspaceId)]);
      const customerRows = directory.parties.filter((party) => party.roles?.includes('customer'));
      setCustomers(customerRows);
      setSites(directory.sites);
      setQuotes(quoteRows);
      if (requestedCustomerId && customerRows.some((customer) => customer.id === requestedCustomerId)) setCreateCustomerId(requestedCustomerId);
      if (requestedQuoteId && quoteRows.some((quote) => quote.id === requestedQuoteId)) setSelectedQuoteId(requestedQuoteId);
    } catch (cause) {
      console.error('Unable to load professional quotes', cause);
      setError('No se han podido cargar los presupuestos.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void reload(); }, [selectedWorkspaceId]);

  const selectedQuote = useMemo(() => quotes.find((quote) => quote.id === selectedQuoteId) ?? null, [quotes, selectedQuoteId]);
  const createSites = useMemo(() => sites.filter((site) => !createCustomerId || site.customer_party_id === createCustomerId), [createCustomerId, sites]);

  async function createQuote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || saving) return;
    const form = new FormData(event.currentTarget);
    const title = String(form.get('title') ?? '').trim();
    const quoteNumber = String(form.get('number') ?? '').trim();
    const issuedOn = String(form.get('issued_on') ?? '').trim();
    const validUntil = String(form.get('valid_until') ?? '').trim();
    const quantity = Number(String(form.get('quantity') ?? '1').replace(',', '.'));
    const unitPrice = Number(String(form.get('unit_price') ?? '').replace(',', '.'));
    const taxRate = Number(String(form.get('tax_rate') ?? '21').replace(',', '.')) || 0;
    const status = String(form.get('status') ?? 'draft') as 'draft' | 'sent';
    if (!createCustomerId || !title || !Number.isFinite(quantity) || quantity <= 0 || !Number.isFinite(unitPrice) || unitPrice < 0) {
      setError('Completa cliente, concepto, cantidad e importe.');
      return;
    }
    const subtotal = Number((quantity * unitPrice).toFixed(2));
    const tax = Number((subtotal * taxRate / 100).toFixed(2));
    const total = Number((subtotal + tax).toFixed(2));
    try {
      setSaving(true);
      setError(null);
      setConverted(null);
      const quote = await createProfessionalQuote(selectedWorkspaceId, {
        customer_party_id: createCustomerId,
        customer_site_id: createSiteId || undefined,
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
      setCreatedQuoteId(quote.id);
      setSelectedQuoteId(quote.id);
      event.currentTarget.reset();
      setCreateSiteId('');
      await reload();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (cause) {
      console.error('Unable to create quote', cause);
      setError('No se ha podido guardar el presupuesto. Comprueba el número y los importes.');
    } finally {
      setSaving(false);
    }
  }

  async function changeStatus(quoteId: string, status: 'sent' | 'accepted' | 'rejected' | 'expired') {
    if (!selectedWorkspaceId) return;
    try {
      setError(null);
      await updateProfessionalQuoteStatus(selectedWorkspaceId, quoteId, status);
      await reload();
    } catch (cause) {
      console.error('Unable to update quote status', cause);
      setError('No se ha podido actualizar el estado del presupuesto.');
    }
  }

  async function convert(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !selectedQuote || selectedQuote.status !== 'accepted' || saving) return;
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
      setSaving(true);
      setError(null);
      const work = await convertProfessionalQuote(selectedWorkspaceId, selectedQuote.id, {
        customer_site_id: siteId,
        type,
        occurred_on: occurredOn,
        title: selectedQuote.title,
        labor_cost_eur: labor,
        other_cost_eur: other,
      });
      setConverted({ workId: work.id, customerId: selectedQuote.customer_party_id, quoteId: selectedQuote.id });
      setSelectedQuoteId(null);
      await reload();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (cause) {
      console.error('Unable to convert quote', cause);
      setError('No se ha podido convertir el presupuesto. Debe estar aceptado y no haberse convertido antes.');
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <section className="card"><p>Cargando presupuestos…</p></section>;

  return <>
    <header className="page-title"><span className="eyebrow dark">MI CAMPO · PROFESIONAL</span><h1>Presupuestos</h1><p>Presupuesta primero y convierte a trabajo solo cuando el cliente lo acepte.</p></header>

    {createdQuoteId ? <section className="card register-principle"><div><strong>Presupuesto guardado</strong><small>Ahora puedes imprimirlo/compartirlo o avanzar su estado cuando el cliente responda.</small></div><div className="record-actions"><Link className="secondary-action action-link" href={`/mi-campo/profesional/documento?type=quote&id=${encodeURIComponent(createdQuoteId)}`}>Ver / compartir PDF</Link></div></section> : null}
    {converted ? <section className="record-success card"><div className="success-mark">✓</div><h2>Trabajo creado desde presupuesto</h2><p>Se conserva la trazabilidad con el presupuesto aceptado. El trabajo ya puede facturarse.</p><div className="record-actions"><Link className="primary action-link" href={`/mi-campo/profesional/facturas/nueva?customerId=${encodeURIComponent(converted.customerId)}&workId=${encodeURIComponent(converted.workId)}`}>Facturar este trabajo →</Link><Link className="secondary-action action-link" href={`/mi-campo/profesional/cliente?id=${encodeURIComponent(converted.customerId)}`}>Ver cliente</Link><Link className="secondary-action action-link" href={`/mi-campo/profesional/documento?type=quote&id=${encodeURIComponent(converted.quoteId)}`}>Ver presupuesto</Link></div></section> : null}

    {customers.length === 0 ? <section className="card"><h2>Primero necesitas un cliente</h2><p>Registra un trabajo para un cliente nuevo y Mágina creará su ficha y ubicación profesional.</p><Link className="primary action-link" href="/mi-campo/registrar/trabajo">Registrar trabajo para cliente →</Link></section> : <section className="section"><div className="section-head"><h2>Nuevo presupuesto</h2></div>
      <form className="quick-record-form" onSubmit={createQuote}>
        <section className="card record-panel"><div className="record-fields">
          <label className="record-field wide"><span>Cliente</span><select className="record-control" name="customer" value={createCustomerId} onChange={(event) => { setCreateCustomerId(event.target.value); setCreateSiteId(''); }} required><option value="">Seleccionar</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.display_name}</option>)}</select></label>
          <label className="record-field wide"><span>Finca / sitio del cliente</span><select className="record-control" name="site" value={createSiteId} onChange={(event) => setCreateSiteId(event.target.value)} disabled={!createCustomerId}><option value="">Sin sitio todavía</option>{createSites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}</select><small>{createCustomerId ? `${createSites.length} ubicacion(es) de este cliente.` : 'Selecciona primero un cliente.'}</small></label>
          <label className="record-field wide"><span>Concepto</span><input className="record-control" name="title" required /></label>
          <label className="record-field"><span>Nº presupuesto</span><input className="record-control" name="number" placeholder="P-2026-001" /></label>
          <label className="record-field"><span>Estado inicial</span><select className="record-control" name="status" defaultValue="draft"><option value="draft">Borrador</option><option value="sent">Enviado</option></select></label>
          <label className="record-field"><span>Fecha</span><input className="record-control" name="issued_on" type="date" /></label>
          <label className="record-field"><span>Válido hasta</span><input className="record-control" name="valid_until" type="date" /></label>
          <label className="record-field"><span>Cantidad</span><input className="record-control" name="quantity" type="number" min="0.001" step="0.001" defaultValue="1" required /></label>
          <label className="record-field"><span>Precio unidad</span><input className="record-control" name="unit_price" type="number" min="0" step="0.01" required /></label>
          <label className="record-field"><span>IVA %</span><input className="record-control" name="tax_rate" type="number" min="0" max="100" step="0.01" defaultValue="21" /></label>
        </div></section>
        <section className="record-save-bar"><small>El presupuesto no crea ningún trabajo.</small><button className="primary" type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Guardar presupuesto →'}</button></section>
      </form>
    </section>}

    {error ? <p className="form-error" role="alert">{error}</p> : null}

    <section className="section"><div className="section-head"><h2>Histórico</h2><span className="subtle">{quotes.length}</span></div>
      {!quotes.length ? <section className="card"><h3>Sin presupuestos todavía</h3><p>Cuando guardes el primero aparecerá aquí con su estado, trazabilidad y siguientes acciones.</p></section> : null}
      <div className="activity-list">{quotes.map((quote) => {
        const quoted = Number(quote.total_eur ?? 0);
        const actualCost = Number(quote.actual_cost_eur ?? 0);
        const invoiced = Number(quote.invoice_total_eur ?? 0);
        const focused = quote.id === requestedQuoteId || quote.id === createdQuoteId;
        return <article className={`card activity-item${focused ? ' selected' : ''}`} key={quote.id}><div><small>{quote.quote_number || 'Sin número'} · {quote.status}</small><h3>{quote.title}</h3><p>{quote.customer_name}{quote.site_name ? ` · ${quote.site_name}` : ''}</p>{quote.status === 'converted' ? <small>Presupuestado {money(quoted)} · coste real {money(actualCost)}{quote.invoice_id ? ` · facturado ${money(invoiced)}` : ' · aún sin factura'}</small> : null}</div><div><strong>{money(quoted)}</strong><div className="record-actions"><Link className="detail-link" href={`/mi-campo/profesional/cliente?id=${encodeURIComponent(quote.customer_party_id)}`}>Cliente</Link><Link className="detail-link" href={`/mi-campo/profesional/documento?type=quote&id=${encodeURIComponent(quote.id)}`}>Imprimir / PDF</Link>{quote.status === 'draft' ? <button className="secondary-action" type="button" onClick={() => void changeStatus(quote.id, 'sent')}>Marcar enviado</button> : null}{quote.status === 'sent' ? <><button className="secondary-action" type="button" onClick={() => void changeStatus(quote.id, 'accepted')}>Aceptar</button><button className="secondary-action" type="button" onClick={() => void changeStatus(quote.id, 'rejected')}>Rechazar</button></> : null}{quote.status === 'accepted' ? <button className="primary" type="button" onClick={() => setSelectedQuoteId(quote.id)}>Convertir a trabajo</button> : null}{quote.status === 'converted' && quote.work_id && !quote.invoice_id ? <Link className="primary action-link" href={`/mi-campo/profesional/facturas/nueva?customerId=${encodeURIComponent(quote.customer_party_id)}&workId=${encodeURIComponent(quote.work_id)}`}>Facturar</Link> : null}</div></div></article>;
      })}</div>
    </section>

    {selectedQuote?.status === 'accepted' ? <section className="section"><div className="section-head"><h2>Convertir presupuesto aceptado</h2></div><form className="quick-record-form" onSubmit={convert}><section className="card record-panel"><div className="record-fields">
      <label className="record-field wide"><span>Finca / sitio</span><select className="record-control" name="site" defaultValue={selectedQuote.customer_site_id ?? ''} required><option value="">Seleccionar</option>{sites.filter((site) => site.customer_party_id === selectedQuote.customer_party_id).map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}</select></label>
      <label className="record-field"><span>Fecha trabajo</span><input className="record-control" name="occurred_on" type="date" required /></label>
      <label className="record-field"><span>Tipo</span><select className="record-control" name="type" defaultValue="manual-work"><option value="manual-work">Trabajo manual</option><option value="pruning">Poda</option><option value="harvest">Recolección</option><option value="treatment">Tratamiento</option><option value="transport">Transporte</option><option value="machinery-work">Maquinaria</option><option value="other">Otro</option></select></label>
      <label className="record-field"><span>Coste real mano de obra</span><input className="record-control" name="labor" type="number" min="0" step="0.01" defaultValue="0" /></label>
      <label className="record-field"><span>Otros costes reales</span><input className="record-control" name="other" type="number" min="0" step="0.01" defaultValue="0" /></label>
    </div></section><section className="record-save-bar"><small>El trabajo conservará el presupuesto de origen y su importe acordado.</small><button className="primary" type="submit" disabled={saving}>{saving ? 'Convirtiendo…' : 'Crear trabajo desde presupuesto →'}</button></section></form></section> : null}
  </>;
}
