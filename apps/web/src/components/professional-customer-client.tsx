'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { getDocumentReadUrl } from '@/lib/document-data-source';
import { loadProfessionalCustomer, type ProfessionalCustomerDetail } from '@/lib/professional-customer-source';

function money(value: number) {
  return value.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
}

export function ProfessionalCustomerClient() {
  const params = useSearchParams();
  const customerId = params.get('id');
  const { selectedWorkspaceId } = useAuth();
  const [data, setData] = useState<ProfessionalCustomerDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [openingDocumentId, setOpeningDocumentId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!customerId || !selectedWorkspaceId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    loadProfessionalCustomer(selectedWorkspaceId, customerId)
      .then((value) => { if (!cancelled) setData(value); })
      .catch((cause) => {
        console.error('Unable to load professional customer', cause);
        if (!cancelled) setError('No se ha podido cargar el cliente.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [customerId, selectedWorkspaceId]);

  async function openDocument(documentId: string) {
    if (!selectedWorkspaceId || openingDocumentId) return;
    try {
      setOpeningDocumentId(documentId);
      setError(null);
      const result = await getDocumentReadUrl(selectedWorkspaceId, documentId);
      window.open(result.url, '_blank', 'noopener,noreferrer');
    } catch (cause) {
      console.error('Unable to open professional invoice document', cause);
      setError('No se ha podido abrir el PDF de la factura.');
    } finally {
      setOpeningDocumentId(null);
    }
  }

  if (loading) return <section className="card"><p>Cargando cliente…</p></section>;
  if (!data) return <section className="card"><h1>Cliente no disponible</h1><p>{error ?? 'Falta el identificador del cliente.'}</p><Link href="/mi-campo/profesional">Volver a Profesional</Link></section>;

  const customer = data.customer;
  const summary = data.summary;
  return <>
    <header className="page-title"><span className="eyebrow dark">MI CAMPO · PROFESIONAL · CLIENTE</span><h1>{customer.display_name}</h1><p>{[customer.tax_id, customer.phone, customer.email].filter(Boolean).join(' · ') || 'Ficha profesional'}</p></header>

    <section className="card field-summary campaign-summary"><div className="stats">
      <div className="stat"><b>{summary.work_count}</b><span>trabajos</span></div>
      <div className="stat"><b>{summary.invoice_count}</b><span>facturas</span></div>
      <div className="stat"><b>{money(summary.pending_eur)}</b><span>pendiente</span></div>
    </div></section>

    <section className="section"><div className="section-head"><h2>Rentabilidad</h2><Link href={`/mi-campo/profesional/facturas/nueva?customerId=${encodeURIComponent(customer.id)}`} className="detail-link">Nueva factura</Link></div><div className="quick-grid">
      <article className="card quick"><div><strong>{money(summary.charged_eur)}</strong><small>facturado/devengado</small></div></article>
      <article className="card quick"><div><strong>{money(summary.collected_eur)}</strong><small>cobrado</small></div></article>
      <article className="card quick"><div><strong>{money(summary.direct_cost_eur)}</strong><small>coste directo</small></div></article>
      <article className="card quick"><div><strong>{money(summary.accrued_margin_eur)}</strong><small>margen devengado</small></div></article>
    </div></section>

    {error ? <p className="form-error" role="alert">{error}</p> : null}

    <section className="section"><div className="section-head"><h2>Facturas</h2><span className="subtle">{data.invoices.length}</span></div>
      {data.invoices.length ? <div className="activity-list">{data.invoices.map((invoice) => {
        const invoiceDocuments = data.documents.filter((document) => document.invoice_id === invoice.id);
        const uploadQuery = new URLSearchParams({ invoiceId: invoice.id, customerId: customer.id, invoiceNumber: invoice.invoice_number ?? 'factura' });
        return <article className="card activity-item" key={invoice.id}>
          <div><small>{invoice.issued_on ?? 'Borrador'}</small><h3>{invoice.invoice_number ?? 'Factura sin número'}</h3><p>{invoice.work_count} trabajo{invoice.work_count === 1 ? '' : 's'} · {invoice.status}</p>{invoiceDocuments.length ? <small>{invoiceDocuments.length} documento{invoiceDocuments.length === 1 ? '' : 's'} vinculado{invoiceDocuments.length === 1 ? '' : 's'}</small> : null}</div>
          <div><strong>{money(Number(invoice.total_eur))}</strong><small>{Number(invoice.pending_eur) > 0 ? `${money(Number(invoice.pending_eur))} pendiente` : 'cobrada'}</small><Link className="detail-link" href={`/mi-campo/profesional/facturas/documento?${uploadQuery.toString()}`}>Adjuntar PDF</Link>{invoiceDocuments.map((document) => <button key={document.id} type="button" className="secondary-action" onClick={() => void openDocument(document.id)} disabled={openingDocumentId === document.id}>{openingDocumentId === document.id ? 'Abriendo…' : 'Abrir PDF'}</button>)}</div>
        </article>;
      })}</div> : <section className="card"><p>Este cliente todavía no tiene facturas.</p></section>}
    </section>

    <section className="section"><div className="section-head"><h2>Trabajos</h2><span className="subtle">{data.works.length}</span></div>
      <div className="activity-list">{data.works.map((work) => <article className="card activity-item" key={work.id}><div><small>{work.occurred_on}</small><h3>{work.title}</h3><p>{work.site_name ?? 'Sin finca/sitio asociado'}{work.invoice_number ? ` · Factura ${work.invoice_number}` : ' · Sin facturar'}</p><small>Coste {money(Number(work.direct_cost_eur))} · margen {money(Number(work.accrued_margin_eur))}</small></div><div><strong>{money(Number(work.charge_eur ?? 0))}</strong><small>{Number(work.pending_eur) > 0 ? `${money(Number(work.pending_eur))} pendiente` : 'cobrado'}</small>{!work.invoice_id ? <Link className="detail-link" href={`/mi-campo/profesional/facturas/nueva?customerId=${encodeURIComponent(customer.id)}&workId=${encodeURIComponent(work.id)}`}>Facturar</Link> : null}{Number(work.pending_eur) > 0 ? <Link className="detail-link" href={`/mi-campo/profesional/cobrar?workId=${encodeURIComponent(work.id)}`}>Registrar cobro</Link> : null}</div></article>)}</div>
    </section>

    {data.collections.length ? <section className="section"><div className="section-head"><h2>Cobros</h2><span className="subtle">{data.collections.length}</span></div><div className="activity-list">{data.collections.map((collection) => <article className="card activity-item" key={collection.id}><div><small>{collection.collected_on}</small><h3>{collection.work_title}</h3><p>{[collection.method, collection.reference].filter(Boolean).join(' · ') || 'Cobro registrado'}</p></div><strong>{money(Number(collection.amount_eur))}</strong></article>)}</div></section> : null}
  </>;
}
