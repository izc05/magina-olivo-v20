'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { uploadDomainAttachment } from '@/lib/document-upload-source';

export function ProfessionalInvoiceDocumentUploadClient() {
  const params = useSearchParams();
  const invoiceId = params.get('invoiceId');
  const customerId = params.get('customerId');
  const invoiceNumber = params.get('invoiceNumber') ?? 'factura';
  const { selectedWorkspaceId } = useAuth();
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const returnHref = customerId ? `/mi-campo/profesional/cliente?id=${encodeURIComponent(customerId)}` : '/mi-campo/profesional';

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !invoiceId || saving) return;
    const form = new FormData(event.currentTarget);
    const file = form.get('file');
    if (!(file instanceof File) || !file.size) {
      setError('Selecciona el PDF de la factura.');
      return;
    }
    if (file.type && file.type !== 'application/pdf') {
      setError('Para la factura profesional usa un archivo PDF.');
      return;
    }

    try {
      setSaving(true);
      setError(null);
      await uploadDomainAttachment({
        workspaceId: selectedWorkspaceId,
        domainType: 'professional_invoice',
        domainRecordId: invoiceId,
        file,
        kind: 'sales_invoice',
        title: `Factura emitida ${invoiceNumber}`,
        relation: 'issued_invoice_pdf',
      });
      setSaved(true);
    } catch (cause) {
      console.error('Unable to upload professional invoice PDF', cause);
      setError('No se ha podido guardar el PDF. La factura sigue existiendo y puedes volver a adjuntarlo después.');
    } finally {
      setSaving(false);
    }
  }

  if (!invoiceId) return <section className="card"><h1>Factura no disponible</h1><p>Falta el identificador de la factura.</p><Link href={returnHref}>Volver</Link></section>;
  if (saved) return <section className="record-success card"><div className="success-mark">✓</div><h1>PDF vinculado</h1><p>El documento queda unido directamente a la factura profesional, sin asignarlo artificialmente a una sola finca.</p><Link className="primary action-link" href={returnHref}>Volver al cliente</Link></section>;

  return <>
    <header className="page-title"><span className="eyebrow dark">MI CAMPO · PROFESIONAL · DOCUMENTOS</span><h1>Adjuntar PDF de factura</h1><p>{invoiceNumber}. El archivo se conservará como documento emitido y quedará trazado contra esta factura.</p></header>
    <form className="quick-record-form" onSubmit={submit}>
      <section className="card record-panel"><div className="record-fields">
        <label className="record-field wide"><span>PDF de factura</span><input className="record-control" name="file" type="file" accept="application/pdf,.pdf" required /><small>Se valida integridad y se almacena mediante el sistema documental existente.</small></label>
      </div></section>
      <section className="card register-principle"><div><strong>Factura estructurada + PDF</strong><small>El PDF es evidencia/documento emitido. Los importes, vencimiento, trabajos y cobros siguen siendo datos estructurados independientes.</small></div></section>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <section className="record-save-bar"><Link className="secondary-action action-link" href={returnHref}>Cancelar</Link><button className="primary" type="submit" disabled={saving}>{saving ? 'Subiendo…' : 'Guardar PDF →'}</button></section>
    </form>
  </>;
}
