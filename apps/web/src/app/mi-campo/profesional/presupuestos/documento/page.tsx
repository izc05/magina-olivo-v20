import { Suspense } from 'react';
import { ProfessionalQuoteDocumentUploadClient } from '@/components/professional-quote-document-upload-client';

export default function ProfessionalQuoteDocumentPage() {
  return <Suspense fallback={<section className="card"><p>Cargando documento…</p></section>}><ProfessionalQuoteDocumentUploadClient /></Suspense>;
}
