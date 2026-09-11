'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { uploadDomainAttachment } from '@/lib/document-upload-source';

export function ProfessionalQuoteDocumentUploadClient() {
  const params = useSearchParams();
  const quoteId = params.get('quoteId');
  const customerId = params.get('customerId');
  const quoteNumber = params.get('quoteNumber') ?? 'presupuesto';
  const { selectedWorkspaceId } = useAuth();
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const returnHref = customerId ? `/mi-campo/profesional/cliente?id=${encodeURIComponent(customerId)}` : '/mi-campo/profesional/presupuestos';

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedWorkspaceId || !quoteId || saving) return;
    const form = new FormData(event.currentTarget);
    const file = form.get('file');
    if (!(file instanceof File) || !file.size) {
      setError('Selecciona el PDF del presupuesto.');
      return;
    }
    if (file.type && file.type !== 'application/pdf') {
      setError('Para el presupuesto profesional usa un archivo PDF.');
      return;
    }

    try {
      setSaving(true);
      setError(null);
      await uploadDomainAttachment({
        workspaceId: selectedWorkspaceId,
        domainType: 'professional_quote',
        domainRecordId: quoteId,
        file,
        kind: 'sales_quote',
        title: `Presupuesto emitido ${quoteNumber}`,
        relation: 'issued_quote_pdf',
      });
      setSaved(true);
    } catch (cause) {
      console.error('Unable to upload professional quote PDF', cause);
      setError('No se ha podido guardar el PDF. El presupuesto sigue existiendo y puedes volver a adjuntarlo después.');
    } finally {
      setSaving(false);
    }
  }

  if (!quoteId) return <section className="card"><h1>Presupuesto no disponible</h1><p>Falta el identificador del presupuesto.</p><Link href={returnHref}>Volver</Link></section>;
  if (saved) return <section className="record-success card"><div className="success-mark">✓</div><h1>PDF vinculado</h1><p>El documento queda unido al presupuesto profesional y conserva su trazabilidad aunque después se convierta en trabajo.</p><Link className="primary action-link" href={returnHref}>Volver al cliente</Link></section>;

  return <>
    <header className="page-title"><span className="eyebrow dark">MI CAMPO · PROFESIONAL · DOCUMENTOS</span><h1>Adjuntar PDF de presupuesto</h1><p>{quoteNumber}. El archivo se conservará como presupuesto emitido y quedará vinculado a este registro.</p></header>
    <form className="quick-record-form" onSubmit={submit}>
      <section className="card record-panel"><div className="record-fields">
        <label className="record-field wide"><span>PDF de presupuesto</span><input className="record-control" name="file" type="file" accept="application/pdf,.pdf" required /><small>Se valida integridad y se almacena mediante el mismo sistema documental de facturas.</small></label>
      </div></section>
      <section className="card register-principle"><div><strong>Presupuesto estructurado + PDF</strong><small>El PDF conserva la versión enviada. El estado, aceptación y conversión continúan siendo datos estructurados independientes.</small></div></section>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <section className="record-save-bar"><Link className="secondary-action action-link" href={returnHref}>Cancelar</Link><button className="primary" type="submit" disabled={saving}>{saving ? 'Subiendo…' : 'Guardar PDF →'}</button></section>
    </form>
  </>;
}
