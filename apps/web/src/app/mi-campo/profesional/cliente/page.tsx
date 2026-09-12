import { Suspense } from 'react';
import { ProfessionalCustomerClient } from '@/components/professional-customer-client';

export default function ProfessionalCustomerPage() {
  return <Suspense fallback={<section className="card"><p>Cargando cliente…</p></section>}><ProfessionalCustomerClient /></Suspense>;
}
