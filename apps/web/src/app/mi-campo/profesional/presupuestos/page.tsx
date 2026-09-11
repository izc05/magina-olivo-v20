import { Suspense } from 'react';
import { ProfessionalQuotesClient } from '@/components/professional-quotes-client';

export default function ProfessionalQuotesPage() {
  return <Suspense fallback={<section className="card"><p>Cargando presupuestos…</p></section>}><ProfessionalQuotesClient /></Suspense>;
}
