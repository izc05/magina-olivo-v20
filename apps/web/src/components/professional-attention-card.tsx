'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadProfessionalAttention, type ProfessionalAttentionSummary } from '@/lib/professional-attention-data-source';

function money(value: number) {
  return value.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
}

export function ProfessionalAttentionCard() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [data, setData] = useState<ProfessionalAttentionSummary | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!apiConfigured || status !== 'authenticated' || !selectedWorkspaceId) {
      setData(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    loadProfessionalAttention(selectedWorkspaceId, 6)
      .then((next) => { if (!cancelled) setData(next); })
      .catch((error) => {
        console.warn('Professional attention unavailable', error);
        if (!cancelled) setData(null);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [apiConfigured, selectedWorkspaceId, status]);

  if (!apiConfigured || status !== 'authenticated') return null;
  if (loading && !data) return <section className="card"><p>Revisando seguimiento comercial…</p></section>;
  if (!data) return null;

  const hasAttention = data.summary.overdueInvoiceCount > 0
    || data.summary.unbilledWorkCount > 0
    || data.summary.agedCustomerCount > 0
    || data.summary.expiredQuoteCount > 0
    || data.summary.quoteFollowupCount > 0;
  if (!hasAttention) return null;

  return <section className="section">
    <div className="section-head"><div><h2>Seguimiento comercial</h2><small>Vencimientos, facturación y presupuestos sin respuesta</small></div><Link href="/mi-campo/profesional/presupuestos">Presupuestos</Link></div>
    <div className="quick-grid">
      {data.summary.overdueInvoiceCount > 0 ? <article className="card quick"><div><strong>{money(data.summary.overdueInvoiceEur)}</strong><small>{data.summary.overdueInvoiceCount} factura{data.summary.overdueInvoiceCount === 1 ? '' : 's'} vencida{data.summary.overdueInvoiceCount === 1 ? '' : 's'}</small></div></article> : null}
      {data.summary.unbilledWorkCount > 0 ? <article className="card quick"><div><strong>{money(data.summary.unbilledWorkEur)}</strong><small>{data.summary.unbilledWorkCount} trabajo{data.summary.unbilledWorkCount === 1 ? '' : 's'} sin facturar</small></div></article> : null}
      {data.summary.expiredQuoteCount > 0 ? <article className="card quick"><div><strong>{money(data.summary.expiredQuoteEur)}</strong><small>{data.summary.expiredQuoteCount} presupuesto{data.summary.expiredQuoteCount === 1 ? '' : 's'} fuera de plazo</small></div></article> : null}
      {data.summary.quoteFollowupCount > 0 ? <article className="card quick"><div><strong>{data.summary.quoteFollowupCount}</strong><small>presupuesto{data.summary.quoteFollowupCount === 1 ? '' : 's'} enviado{data.summary.quoteFollowupCount === 1 ? '' : 's'} sin respuesta</small></div></article> : null}
      {data.summary.agedCustomerCount > 0 ? <article className="card quick"><div><strong>{money(data.summary.agedReceivableEur)}</strong><small>{data.summary.agedCustomerCount} cliente{data.summary.agedCustomerCount === 1 ? '' : 's'} con deuda de 30+ días</small></div></article> : null}
    </div>

    {data.expiredQuotes.length ? <div className="card feed today-list">
      {data.expiredQuotes.slice(0, 3).map((quote) => <div className="feed-row" key={quote.id}>
        <div className="feed-copy"><strong>Presupuesto {quote.quoteNumber ?? 'sin nº'} · {quote.customerName}</strong><small>{quote.overdueDays} días fuera de plazo · {money(quote.totalEur)}</small></div>
        <Link className="secondary-action action-link" href={`/mi-campo/profesional/presupuestos?quoteId=${encodeURIComponent(quote.id)}&customerId=${encodeURIComponent(quote.customerId)}`}>Gestionar</Link>
      </div>)}
    </div> : null}

    {data.quoteFollowups.length ? <div className="card feed today-list">
      {data.quoteFollowups.slice(0, 3).map((quote) => <div className="feed-row" key={quote.id}>
        <div className="feed-copy"><strong>{quote.title}</strong><small>{quote.customerName} · enviado hace {quote.ageDays} días · {money(quote.totalEur)}</small></div>
        <Link className="secondary-action action-link" href={`/mi-campo/profesional/presupuestos?quoteId=${encodeURIComponent(quote.id)}&customerId=${encodeURIComponent(quote.customerId)}`}>Revisar</Link>
      </div>)}
    </div> : null}

    {data.overdueInvoices.length ? <div className="card feed today-list">
      {data.overdueInvoices.slice(0, 3).map((invoice) => <div className="feed-row" key={invoice.id}>
        <div className="feed-copy"><strong>Factura {invoice.invoiceNumber ?? 'sin nº'} · {invoice.customerName}</strong><small>{invoice.overdueDays} días vencida · pendiente {money(invoice.pendingEur)}</small></div>
        <Link className="secondary-action action-link" href={`/mi-campo/profesional/cliente?id=${encodeURIComponent(invoice.customerId)}`}>Ver cliente</Link>
      </div>)}
    </div> : null}

    {data.unbilledWorks.length ? <div className="card feed today-list">
      {data.unbilledWorks.slice(0, 3).map((work) => <div className="feed-row" key={work.id}>
        <div className="feed-copy"><strong>{work.title}</strong><small>{work.customerName} · {work.ageDays} días sin facturar · {money(work.chargeEur)}</small></div>
        <Link className="secondary-action action-link" href={`/mi-campo/profesional/facturas/nueva?customerId=${encodeURIComponent(work.customerId)}&workId=${encodeURIComponent(work.id)}`}>Facturar</Link>
      </div>)}
    </div> : null}
  </section>;
}
