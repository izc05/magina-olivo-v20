import { Suspense } from 'react';
import { ProfessionalInvoiceDocumentUploadClient } from '@/components/professional-invoice-document-upload-client';

export default function ProfessionalInvoiceDocumentPage() {
  return <Suspense fallback={<section className="card"><p>Cargando documento…</p></section>}><ProfessionalInvoiceDocumentUploadClient /></Suspense>;
}
