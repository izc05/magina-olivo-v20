import { Suspense } from 'react';
import { ProfessionalInvoiceEntryClient } from '@/components/professional-invoice-entry-client';

export default function ProfessionalInvoicePage() {
  return <Suspense fallback={<section className="card"><p>Cargando factura…</p></section>}><ProfessionalInvoiceEntryClient /></Suspense>;
}
