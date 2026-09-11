import { Suspense } from 'react';
import { ProfessionalDeliveryPanel } from '@/components/professional-delivery-panel';
import { ProfessionalDocumentPrintClient } from '@/components/professional-document-print-client';

export default function ProfessionalDocumentPage() {
  return <Suspense fallback={<section className="card"><p>Preparando documento…</p></section>}><ProfessionalDeliveryPanel/><ProfessionalDocumentPrintClient /></Suspense>;
}
