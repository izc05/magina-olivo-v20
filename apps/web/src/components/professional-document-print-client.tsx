'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { uploadDomainAttachment } from '@/lib/document-upload-source';
import { generateProfessionalPdf } from '@/lib/professional-pdf-generator';
import { loadProfessionalPrintData, type ProfessionalPrintPayload } from '@/lib/professional-print-source';

function money(value: number) {
  return value.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
}

function dateLabel(value?: string | null) {
  if (!value) return '—';
  const [year, month, day] = value.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

function text(value: unknown) {
  return value == null ? '' : String(value);
}

function safeFilename(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^A-Za-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 120) || 'documento';
}

export function ProfessionalDocumentPrintClient() {
  const params = useSearchParams();
  const rawType = params.get('type');
  const type = rawType === 'invoice' || rawType === 'quote' ? rawType : null;
  const id = params.get('id');
  const { selectedWorkspaceId } = useAuth();
  const [data, setData] = useState<ProfessionalPrintPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [archiving, setArchiving] = useState(false);
  const [archived, setArchived] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedWorkspaceId || !type || !id) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    loadProfessionalPrintData(selectedWorkspaceId, type, id)
      .then((payload) => { if (!cancelled) setData(payload); })
      .catch((cause) => {
        console.error('Unable to load professional print data', cause);
        if (!cancelled) setError('No se ha podido cargar el documento.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id, selectedWorkspaceId, type]);

  if (loading) return <section className="card"><p>Preparando documento…</p></section>;
  if (!data || !type || !id) return <section className="card"><h1>Documento no disponible</h1><p>{error ?? 'Faltan datos para abrirlo.'}</p><Link href="/mi-campo/profesional">Volver a Profesional</Link></section>;

  const printData = data;
  const issuer = printData.issuer;
  const customerName = printData.customer.legal_name || printData.customer.display_name;
  const isInvoice = printData.document_type === 'invoice';
  const title = isInvoice ? 'FACTURA' : 'PRESUPUESTO';
  const number = printData.document.number || (isInvoice ? 'Borrador' : 'Sin número');
  const uploadHref = isInvoice
    ? `/mi-campo/profesional/facturas/documento?${new URLSearchParams({ invoiceId: printData.document.id, customerId: printData.customer.id, invoiceNumber: number }).toString()}`
    : `/mi-campo/profesional/presupuestos/documento?${new URLSearchParams({ quoteId: printData.document.id, customerId: printData.customer.id, quoteNumber: number }).toString()}`;

  async function generateAndArchive() {
    if (!selectedWorkspaceId || archiving) return;
    try {
      setArchiving(true);
      setArchived(false);
      setError(null);
      const blob = generateProfessionalPdf(printData);
      const filename = `${safeFilename(isInvoice ? `factura-${number}` : `presupuesto-${number}`)}.pdf`;
      const file = new File([blob], filename, { type: 'application/pdf', lastModified: Date.now() });
      await uploadDomainAttachment({
        workspaceId: selectedWorkspaceId,
        domainType: isInvoice ? 'professional_invoice' : 'professional_quote',
        domainRecordId: printData.document.id,
        file,
        kind: isInvoice ? 'sales_invoice' : 'sales_quote',
        title: `${isInvoice ? 'Factura emitida' : 'Presupuesto emitido'} ${number}`,
        relation: 'generated_pdf',
      });
      setArchived(true);
    } catch (cause) {
      console.error('Unable to generate/archive professional PDF', cause);
      setError('No se ha podido generar y archivar el PDF. El documento estructurado no se ha modificado.');
    } finally {
      setArchiving(false);
    }
  }

  return <main className="commercial-print-shell">
    <div className="commercial-print-toolbar no-print">
      <Link className="secondary-action action-link" href={`/mi-campo/profesional/cliente?id=${encodeURIComponent(printData.customer.id)}`}>← Volver al cliente</Link>
      <div className="action-row">
        <button className="primary" type="button" onClick={() => void generateAndArchive()} disabled={archiving}>{archiving ? 'Generando…' : archived ? 'PDF archivado ✓' : 'Generar y archivar PDF'}</button>
        <button className="secondary-action" type="button" onClick={() => window.print()}>Imprimir / Guardar manualmente</button>
        <Link className="detail-link" href={uploadHref}>Adjuntar otro PDF</Link>
      </div>
    </div>
    {error ? <p className="form-error no-print" role="alert">{error}</p> : null}

    <article className="commercial-a4">
      <header className="commercial-doc-head">
        <div className="commercial-brand"><span>MÁGINA</span><strong>{issuer?.legal_name || issuer?.workspace_name || 'Profesional agrícola'}</strong><small>{[issuer?.tax_id, issuer?.phone, issuer?.email].filter(Boolean).join(' · ')}</small></div>
        <div className="commercial-doc-title"><span>{title}</span><strong>{number}</strong><small>{`Fecha ${dateLabel(printData.document.issued_on)}`}</small></div>
      </header>

      <section className="commercial-parties">
        <div><small>EMISOR</small><strong>{issuer?.legal_name || issuer?.workspace_name || 'Por configurar'}</strong><p>{[issuer?.address, [issuer?.postal_code, issuer?.municipality].filter(Boolean).join(' '), issuer?.province].filter(Boolean).join(' · ')}</p><p>{issuer?.tax_id ? `NIF/CIF ${issuer.tax_id}` : 'NIF/CIF pendiente de configurar'}</p></div>
        <div><small>CLIENTE</small><strong>{customerName}</strong><p>{printData.customer.tax_id ? `NIF/CIF ${printData.customer.tax_id}` : 'NIF/CIF no indicado'}</p><p>{[printData.customer.phone, printData.customer.email].filter(Boolean).join(' · ')}</p></div>
      </section>

      <section className="commercial-meta">
        {isInvoice ? <><div><small>Vencimiento</small><strong>{dateLabel(printData.document.due_on)}</strong></div><div><small>Estado</small><strong>{printData.document.status}</strong></div></> : <><div><small>Válido hasta</small><strong>{dateLabel(printData.document.valid_until)}</strong></div><div><small>Estado</small><strong>{printData.document.status}</strong></div>{printData.document.site_name ? <div><small>Finca / sitio</small><strong>{printData.document.site_name}</strong></div> : null}</>}
      </section>

      {!isInvoice && printData.document.title ? <section className="commercial-intro"><h2>{printData.document.title}</h2></section> : null}

      <table className="commercial-lines">
        <thead><tr>{isInvoice ? <><th>Trabajo</th><th>Fecha</th><th className="right">Importe IVA incl.</th></> : <><th>Concepto</th><th className="right">Cantidad</th><th>Unidad</th><th className="right">Precio</th><th className="right">Importe</th></>}</tr></thead>
        <tbody>{printData.lines.map((line, index) => isInvoice
          ? <tr key={`${text(line.work_id)}-${index}`}><td><strong>{text(line.title)}</strong>{line.site_name ? <small>{text(line.site_name)}</small> : null}</td><td>{dateLabel(text(line.occurred_on))}</td><td className="right">{money(Number(line.amount_eur ?? 0))}</td></tr>
          : <tr key={`${text(line.description)}-${index}`}><td>{text(line.description)}</td><td className="right">{Number(line.quantity ?? 0).toLocaleString('es-ES')}</td><td>{text(line.unit)}</td><td className="right">{money(Number(line.unit_price_eur ?? 0))}</td><td className="right">{money(Number(line.line_total_eur ?? 0))}</td></tr>
        )}</tbody>
      </table>

      <section className="commercial-totals">
        <div><span>Base</span><strong>{money(printData.document.subtotal_eur)}</strong></div>
        <div><span>Impuestos</span><strong>{money(printData.document.tax_eur)}</strong></div>
        <div className="grand"><span>Total</span><strong>{money(printData.document.total_eur)}</strong></div>
      </section>

      {printData.document.notes ? <section className="commercial-notes"><small>NOTAS</small><p>{printData.document.notes}</p></section> : null}
      {issuer?.payment_terms ? <section className="commercial-notes"><small>CONDICIONES / PAGO</small><p>{issuer.payment_terms}</p></section> : null}

      <footer className="commercial-footer"><p>{issuer?.footer_note || 'Documento generado desde datos estructurados de Mágina Olivo.'}</p><small>{isInvoice ? 'La factura y sus cobros se conservan como registros separados.' : 'La aceptación del presupuesto no registra automáticamente un trabajo ni un cobro.'}</small></footer>
    </article>

    <style jsx global>{`
      .commercial-print-shell{min-height:100vh;background:#ecebe5;padding:24px;font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#263126}
      .commercial-print-toolbar{max-width:210mm;margin:0 auto 18px;display:flex;gap:12px;align-items:center;justify-content:space-between;flex-wrap:wrap}
      .commercial-a4{box-sizing:border-box;width:min(210mm,100%);min-height:297mm;margin:0 auto;background:#fff;padding:18mm 16mm;box-shadow:0 16px 45px rgba(31,44,31,.13)}
      .commercial-doc-head{display:flex;justify-content:space-between;gap:24px;padding-bottom:18px;border-bottom:2px solid #364d36}
      .commercial-brand,.commercial-doc-title{display:flex;flex-direction:column;gap:4px}.commercial-brand>span,.commercial-doc-title>span,.commercial-parties small,.commercial-meta small,.commercial-notes small{font-size:10px;letter-spacing:.15em;font-weight:800;color:#667466}.commercial-brand>strong{font-size:22px}.commercial-doc-title{text-align:right}.commercial-doc-title>strong{font-size:20px}.commercial-brand small,.commercial-doc-title small{color:#6d756d}
      .commercial-parties{display:grid;grid-template-columns:1fr 1fr;gap:28px;margin:24px 0}.commercial-parties>div{padding:14px 0;border-top:1px solid #dfe4dc}.commercial-parties strong{display:block;margin:5px 0 7px}.commercial-parties p{margin:3px 0;color:#586258;font-size:13px}
      .commercial-meta{display:flex;gap:28px;flex-wrap:wrap;padding:12px 14px;background:#f5f7f2;border-radius:10px;margin-bottom:24px}.commercial-meta div{display:flex;flex-direction:column;gap:3px;min-width:110px}.commercial-intro h2{font-size:18px;margin:0 0 18px}
      .commercial-lines{width:100%;border-collapse:collapse;margin-top:10px}.commercial-lines th{font-size:10px;text-transform:uppercase;letter-spacing:.08em;text-align:left;border-bottom:1px solid #9eaa9e;padding:9px 6px}.commercial-lines td{font-size:12px;border-bottom:1px solid #e6e9e4;padding:11px 6px;vertical-align:top}.commercial-lines td strong,.commercial-lines td small{display:block}.commercial-lines td small{color:#7a827a;margin-top:3px}.commercial-lines .right{text-align:right}
      .commercial-totals{width:min(310px,100%);margin:22px 0 24px auto}.commercial-totals div{display:flex;justify-content:space-between;padding:6px 0}.commercial-totals .grand{border-top:2px solid #364d36;margin-top:6px;padding-top:10px;font-size:17px}
      .commercial-notes{margin-top:18px;padding-top:12px;border-top:1px solid #e0e4df}.commercial-notes p{white-space:pre-wrap;font-size:12px;line-height:1.55;color:#4e594e}.commercial-footer{margin-top:30px;padding-top:16px;border-top:1px solid #cfd6cd;color:#6b746b;font-size:11px}.commercial-footer p{margin:0 0 5px}.commercial-footer small{font-size:10px}
      @media(max-width:700px){.commercial-print-shell{padding:10px}.commercial-a4{padding:18px 14px;min-height:auto}.commercial-parties{grid-template-columns:1fr}.commercial-doc-head{gap:12px}.commercial-brand>strong{font-size:18px}.commercial-doc-title>strong{font-size:16px}.commercial-lines{display:block;overflow-x:auto}}
      @media print{@page{size:A4;margin:0}.no-print{display:none!important}.commercial-print-shell{background:#fff;padding:0}.commercial-a4{width:210mm;min-height:297mm;margin:0;box-shadow:none;padding:16mm 15mm;print-color-adjust:exact;-webkit-print-color-adjust:exact}.commercial-lines tr,.commercial-parties,.commercial-totals{break-inside:avoid}}
    `}</style>
  </main>;
}
