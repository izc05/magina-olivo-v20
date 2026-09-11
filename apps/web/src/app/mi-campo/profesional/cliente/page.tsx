import { Suspense } from 'react';
import { ProfessionalCustomerActions } from '@/components/professional-customer-actions';
import { ProfessionalCustomerClient } from '@/components/professional-customer-client';

export default function ProfessionalCustomerPage() {
  return <Suspense fallback={<section className="card"><p>Cargando cliente…</p></section>}><ProfessionalCustomerActions /><ProfessionalCustomerClient /></Suspense>;
}
