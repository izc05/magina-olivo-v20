'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiRequestError, apiFetch } from '../lib/api-client';
import { adminApi, type AdminSession } from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import styles from './admin-professional-console.module.css';

type Metrics = {
  customers: number;
  open_quotes: number;
  accepted_quotes: number;
  issued_invoices: number;
  overdue_invoices: number;
  billed_eur: number;
  collected_eur: number;
  pending_eur: number;
};

type Customer = {
  id: string;
  workspace_id: string;
  workspace_name: string;
  kind: string;
  display_name: string;
  legal_name: string | null;
  tax_id: string | null;
  phone: string | null;
  email: string | null;
  notes: string | null;
  active: boolean;
  work_count: number;
  quote_count: number;
  invoice_count: number;
  charged_eur: number;
  collected_eur: number;
  pending_eur: number;
};

type Quote = {
  id: string;
  workspace_id: string;
  workspace_name: string;
  customer_party_id: string;
  customer_name: string;
  quote_number: string | null;
  title: string;
  issued_on: string | null;
  valid_until: string | null;
  status: string;
  subtotal_eur: number;
  tax_eur: number;
  total_eur: number;
};

type Invoice = {
  id: string;
  workspace_id: string;
  workspace_name: string;
  customer_party_id: string;
  customer_name: string;
  invoice_number: string | null;
  issued_on: string | null;
  due_on: string | null;
  status: string;
  subtotal_eur: number;
  tax_eur: number;
  total_eur: number;
  collected_eur: number;
  pending_eur: number;
  notes: string | null;
};

type CustomerDetail = {
  customer: Customer;
  sites: Array<{ id: string; name: string; active: boolean }>;
  works: Array<{ id: string; occurred_on: string; title: string; charge_eur: number | null; payment_status: string }>;
  quotes: Quote[];
  invoices: Invoice[];
  collections: Array<{ id: string; collected_on: string; amount_eur: number; work_title: string }>;
};

type CustomerDraft = {
  display_name: string;
  legal_name: string;
  tax_id: string;
  phone: string;
  email: string;
  notes: string;
  active: boolean;
};

type InvoiceIssueDraft = {
  invoice_number: string;
  issued_on: string;
  due_on: string;
};

type Tab = 'customers' | 'quotes' | 'invoices';

const money = (value: number | null | undefined) =>
  new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(value ?? 0);

const canEdit = (session: AdminSession | null) =>
  session?.platform_access.role === 'admin' || session?.platform_access.role === 'super_admin';

const draftFromCustomer = (customer: Customer): CustomerDraft => ({
  display_name: customer.display_name,
  legal_name: customer.legal_name ?? '',
  tax_id: customer.tax_id ?? '',
  phone: customer.phone ?? '',
  email: customer.email ?? '',
  notes: customer.notes ?? '',
  active: customer.active,
});

const draftFromInvoice = (invoice: Invoice): InvoiceIssueDraft => ({
  invoice_number: invoice.invoice_number ?? '',
  issued_on: invoice.issued_on ?? '',
  due_on: invoice.due_on ?? '',
});

export function AdminProfessionalConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [quotes, setQuotes] = useState<Quote[]>([]);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [invoiceDrafts, setInvoiceDrafts] = useState<Record<string, InvoiceIssueDraft>>({});
  const [tab, setTab] = useState<Tab>('customers');
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<CustomerDetail | null>(null);
  const [draft, setDraft] = useState<CustomerDraft | null>(null);
  const editable = canEdit(session);

  const load = useCallback(async () => {
    const params = new URLSearchParams({ limit: '300' });
    if (query.trim()) params.set('q', query.trim());
    const [overview, customerResponse, quoteResponse, invoiceResponse] = await Promise.all([
      apiFetch<{ metrics: Metrics }>('/api/v1/admin/professional/overview'),
      apiFetch<{ customers: Customer[] }>(`/api/v1/admin/professional/customers?${params}`),
      apiFetch<{ quotes: Quote[] }>(`/api/v1/admin/professional/quotes?${params}`),
      apiFetch<{ invoices: Invoice[] }>(`/api/v1/admin/professional/invoices?${params}`),
    ]);

    setMetrics(overview.metrics);
    setCustomers(customerResponse.customers);
    setQuotes(quoteResponse.quotes);
    setInvoices(invoiceResponse.invoices);
    setInvoiceDrafts((current) => {
      const next: Record<string, InvoiceIssueDraft> = {};
      for (const invoice of invoiceResponse.invoices) {
        next[invoice.id] = current[invoice.id] ?? draftFromInvoice(invoice);
      }
      return next;
    });
    setSelected((current) =>
      current && customerResponse.customers.some((customer) => customer.id === current)
        ? current
        : customerResponse.customers[0]?.id ?? null,
    );
  }, [query]);

  const hydrate = useCallback(async () => {
    if (auth.status !== 'authenticated') return;
    setLoading(true);
    setError(null);
    try {
      setSession(await adminApi.session());
      await load();
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 403) setDenied(true);
      else setError('No se ha podido cargar Profesional.');
    } finally {
      setLoading(false);
    }
  }, [auth.status, load]);

  useEffect(() => {
    if (auth.status === 'authenticated') void hydrate();
    if (auth.status === 'anonymous') setLoading(false);
  }, [auth.status, hydrate]);

  useEffect(() => {
    if (!selected) {
      setDetail(null);
      setDraft(null);
      return;
    }
    let cancelled = false;
    void apiFetch<CustomerDetail>(`/api/v1/admin/professional/customers/${selected}`)
      .then((response) => {
        if (!cancelled) {
          setDetail(response);
          setDraft(draftFromCustomer(response.customer));
        }
      })
      .catch(() => {
        if (!cancelled) setError('No se ha podido cargar el cliente.');
      });
    return () => {
      cancelled = true;
    };
  }, [selected]);

  const customerRows = useMemo(() => customers, [customers]);

  function updateInvoiceDraft(invoiceId: string, field: keyof InvoiceIssueDraft, value: string) {
    setInvoiceDrafts((current) => ({
      ...current,
      [invoiceId]: {
        ...(current[invoiceId] ?? { invoice_number: '', issued_on: '', due_on: '' }),
        [field]: value,
      },
    }));
  }

  async function refreshSelectedCustomer() {
    if (!selected) return;
    const response = await apiFetch<CustomerDetail>(`/api/v1/admin/professional/customers/${selected}`);
    setDetail(response);
    setDraft(draftFromCustomer(response.customer));
  }

  async function saveCustomer() {
    if (!detail || !draft || !editable) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const response = await apiFetch<{ customer: Customer }>(
        `/api/v1/admin/professional/customers/${detail.customer.id}`,
        {
          method: 'PATCH',
          body: JSON.stringify({
            display_name: draft.display_name.trim(),
            legal_name: draft.legal_name.trim() || null,
            tax_id: draft.tax_id.trim() || null,
            phone: draft.phone.trim() || null,
            email: draft.email.trim() || null,
            notes: draft.notes.trim() || null,
            active: draft.active,
          }),
        },
      );
      setDetail({ ...detail, customer: response.customer });
      setDraft(draftFromCustomer(response.customer));
      setMessage('Cliente actualizado y auditado.');
      await load();
    } catch {
      setError('No se ha podido actualizar el cliente.');
    } finally {
      setBusy(false);
    }
  }

  async function quoteStatus(id: string, status: string) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await apiFetch(`/api/v1/admin/professional/quotes/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      });
      setMessage('Estado del presupuesto actualizado.');
      await load();
      await refreshSelectedCustomer();
    } catch (caught) {
      setError(
        caught instanceof ApiRequestError && caught.status === 409
          ? 'La transición no está permitida por el flujo comercial.'
          : 'No se ha podido actualizar el presupuesto.',
      );
    } finally {
      setBusy(false);
    }
  }

  async function invoiceStatus(invoice: Invoice, status: 'issued' | 'void') {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const issueDraft = invoiceDrafts[invoice.id] ?? draftFromInvoice(invoice);
      const payload =
        status === 'issued'
          ? {
              status,
              invoice_number: issueDraft.invoice_number.trim(),
              issued_on: issueDraft.issued_on,
              due_on: issueDraft.due_on || null,
            }
          : { status };
      await apiFetch(`/api/v1/admin/professional/invoices/${invoice.id}`, {
        method: 'PATCH',
        body: JSON.stringify(payload),
      });
      setMessage(status === 'void' ? 'Factura anulada con trazabilidad.' : 'Factura emitida y auditada.');
      await load();
      await refreshSelectedCustomer();
    } catch (caught) {
      setError(
        caught instanceof ApiRequestError && caught.status === 400
          ? 'Para emitir se requiere número y fecha de factura válidos.'
          : caught instanceof ApiRequestError && caught.status === 409
            ? 'La factura está anulada y es inmutable.'
            : 'No se ha podido actualizar la factura.',
      );
    } finally {
      setBusy(false);
    }
  }

  if (auth.status === 'loading' || loading) return <main className={styles.gate}>Cargando Profesional…</main>;
  if (auth.status === 'anonymous') {
    return (
      <main className={styles.gate}>
        <div>
          <h1>Profesional protegido</h1>
          <GoogleSignInButton />
        </div>
      </main>
    );
  }
  if (denied) return <main className={styles.gate}>Acceso restringido.</main>;
  if (!session) return <main className={styles.gate}>{error}</main>;

  return (
    <main className={styles.shell}>
      <header className={styles.hero}>
        <div>
          <span>ADMIN · PROFESIONAL</span>
          <h1>Clientes, presupuestos y facturas</h1>
          <p>Soporte comercial transversal con importes financieros protegidos.</p>
        </div>
        <nav>
          <Link href="/admin/trabajos">Trabajos</Link>
          <Link href="/admin/documentos">Documentos</Link>
          <Link href="/admin/operaciones">Operaciones</Link>
        </nav>
      </header>

      {message ? <div className={styles.ok}>{message}</div> : null}
      {error ? <div className={styles.err}>{error}</div> : null}

      {metrics ? (
        <section className={styles.metrics}>
          <article>Clientes<strong>{metrics.customers}</strong></article>
          <article>Presupuestos abiertos<strong>{metrics.open_quotes}</strong></article>
          <article>Facturas emitidas<strong>{metrics.issued_invoices}</strong></article>
          <article>Facturación<strong>{money(metrics.billed_eur)}</strong></article>
          <article>Cobrado<strong>{money(metrics.collected_eur)}</strong></article>
          <article>Pendiente<strong>{money(metrics.pending_eur)}</strong></article>
          <article>Vencidas<strong>{metrics.overdue_invoices}</strong></article>
        </section>
      ) : null}

      <section className={styles.toolbar}>
        <div>
          <button className={tab === 'customers' ? styles.active : ''} onClick={() => setTab('customers')}>Clientes</button>
          <button className={tab === 'quotes' ? styles.active : ''} onClick={() => setTab('quotes')}>Presupuestos</button>
          <button className={tab === 'invoices' ? styles.active : ''} onClick={() => setTab('invoices')}>Facturas</button>
        </div>
        <div>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar cliente, documento, workspace…" />
          <button onClick={() => void load()}>Buscar</button>
        </div>
      </section>

      {tab === 'customers' ? (
        <section className={styles.grid}>
          <aside className={styles.panel}>
            <div className={styles.list}>
              {customerRows.map((customer) => (
                <button key={customer.id} className={selected === customer.id ? styles.selected : ''} onClick={() => setSelected(customer.id)}>
                  <strong>{customer.display_name}</strong>
                  <span>{customer.workspace_name} · {customer.invoice_count} facturas</span>
                  <small>{money(customer.pending_eur)} pendiente · {customer.active ? 'activo' : 'inactivo'}</small>
                </button>
              ))}
            </div>
          </aside>
          <section className={styles.panel}>
            {detail && draft ? (
              <>
                <div className={styles.head}>
                  <div>
                    <span>Cliente</span>
                    <h2>{detail.customer.display_name}</h2>
                    <p>{detail.customer.workspace_name}</p>
                  </div>
                  <b>{detail.customer.active ? 'Activo' : 'Inactivo'}</b>
                </div>
                <div className={styles.form}>
                  <label>Nombre<input disabled={!editable} value={draft.display_name} onChange={(event) => setDraft({ ...draft, display_name: event.target.value })} /></label>
                  <label>Razón social<input disabled={!editable} value={draft.legal_name} onChange={(event) => setDraft({ ...draft, legal_name: event.target.value })} /></label>
                  <label>NIF/CIF<input disabled={!editable} value={draft.tax_id} onChange={(event) => setDraft({ ...draft, tax_id: event.target.value })} /></label>
                  <label>Teléfono<input disabled={!editable} value={draft.phone} onChange={(event) => setDraft({ ...draft, phone: event.target.value })} /></label>
                  <label>Email<input disabled={!editable} value={draft.email} onChange={(event) => setDraft({ ...draft, email: event.target.value })} /></label>
                  <label className={styles.full}>Notas<textarea disabled={!editable} rows={3} value={draft.notes} onChange={(event) => setDraft({ ...draft, notes: event.target.value })} /></label>
                  <label className={styles.check}><input disabled={!editable} type="checkbox" checked={draft.active} onChange={(event) => setDraft({ ...draft, active: event.target.checked })} /> Cliente activo</label>
                </div>
                {editable ? <button className={styles.primary} disabled={busy || !draft.display_name.trim()} onClick={() => void saveCustomer()}>Guardar cliente</button> : null}
                <div className={styles.cards}>
                  <article>Trabajos<strong>{detail.customer.work_count}</strong></article>
                  <article>Presupuestos<strong>{detail.customer.quote_count}</strong></article>
                  <article>Facturas<strong>{detail.customer.invoice_count}</strong></article>
                  <article>Pendiente<strong>{money(detail.customer.pending_eur)}</strong></article>
                </div>
                <h3>Últimas facturas</h3>
                {detail.invoices.slice(0, 6).map((invoice) => (
                  <div className={styles.row} key={invoice.id}>
                    <span>{invoice.invoice_number ?? 'Borrador'} · {invoice.status}</span>
                    <strong>{money(invoice.total_eur)} · {money(invoice.pending_eur)} pendiente</strong>
                  </div>
                ))}
              </>
            ) : <p>Selecciona un cliente.</p>}
          </section>
        </section>
      ) : null}

      {tab === 'quotes' ? (
        <section className={styles.panel}>
          <div className={styles.tableWrap}>
            <table>
              <thead><tr><th>Presupuesto</th><th>Cliente</th><th>Workspace</th><th>Estado</th><th>Total</th><th>Acciones</th></tr></thead>
              <tbody>
                {quotes.map((quote) => (
                  <tr key={quote.id}>
                    <td>{quote.quote_number ?? '—'}<small>{quote.title}</small></td>
                    <td>{quote.customer_name}</td>
                    <td>{quote.workspace_name}</td>
                    <td>{quote.status}</td>
                    <td>{money(quote.total_eur)}</td>
                    <td>
                      {editable && quote.status !== 'converted' ? (
                        <div className={styles.actions}>
                          <button disabled={busy} onClick={() => void quoteStatus(quote.id, 'sent')}>Enviado</button>
                          <button disabled={busy} onClick={() => void quoteStatus(quote.id, 'accepted')}>Aceptar</button>
                          <button disabled={busy} onClick={() => void quoteStatus(quote.id, 'rejected')}>Rechazar</button>
                          <button disabled={busy} onClick={() => void quoteStatus(quote.id, 'expired')}>Caducar</button>
                        </div>
                      ) : <span>Inmutable</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      {tab === 'invoices' ? (
        <section className={styles.panel}>
          <p className={styles.financialNotice}>Los importes, impuestos, trabajos vinculados y cobros están bloqueados. En borrador solo se completa la identidad de emisión.</p>
          <div className={styles.tableWrap}>
            <table className={styles.invoiceTable}>
              <thead><tr><th>Factura / emisión</th><th>Cliente</th><th>Estado</th><th>Total</th><th>Cobrado</th><th>Pendiente</th><th>Acciones</th></tr></thead>
              <tbody>
                {invoices.map((invoice) => {
                  const issueDraft = invoiceDrafts[invoice.id] ?? draftFromInvoice(invoice);
                  return (
                    <tr key={invoice.id}>
                      <td>
                        {invoice.status === 'draft' && editable ? (
                          <div className={styles.invoiceDraft}>
                            <label>Número<input aria-label={`Número de factura de ${invoice.customer_name}`} value={issueDraft.invoice_number} onChange={(event) => updateInvoiceDraft(invoice.id, 'invoice_number', event.target.value)} placeholder="F-2026-001" /></label>
                            <label>Emisión<input aria-label={`Fecha de emisión de ${invoice.customer_name}`} type="date" value={issueDraft.issued_on} onChange={(event) => updateInvoiceDraft(invoice.id, 'issued_on', event.target.value)} /></label>
                            <label>Vencimiento<input aria-label={`Fecha de vencimiento de ${invoice.customer_name}`} type="date" value={issueDraft.due_on} onChange={(event) => updateInvoiceDraft(invoice.id, 'due_on', event.target.value)} /></label>
                          </div>
                        ) : (
                          <>{invoice.invoice_number ?? 'Sin número'}<small>{invoice.issued_on ?? 'Sin emitir'}{invoice.due_on ? ` · vence ${invoice.due_on}` : ''}</small></>
                        )}
                      </td>
                      <td>{invoice.customer_name}<small>{invoice.workspace_name}</small></td>
                      <td>{invoice.status}</td>
                      <td>{money(invoice.total_eur)}<small>Importe bloqueado</small></td>
                      <td>{money(invoice.collected_eur)}</td>
                      <td>{money(invoice.pending_eur)}</td>
                      <td>
                        {editable && invoice.status !== 'void' ? (
                          <div className={styles.actions}>
                            {invoice.status === 'draft' ? (
                              <button disabled={busy || !issueDraft.invoice_number.trim() || !issueDraft.issued_on} onClick={() => void invoiceStatus(invoice, 'issued')}>Emitir</button>
                            ) : null}
                            <button disabled={busy} onClick={() => void invoiceStatus(invoice, 'void')}>Anular</button>
                          </div>
                        ) : <span>Inmutable</span>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}
    </main>
  );
}
