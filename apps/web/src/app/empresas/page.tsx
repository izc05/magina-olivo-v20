import { Suspense } from 'react';
import { BusinessDetailPage } from '@/components/business-detail-page';

export default function EmpresaPage() {
  return <Suspense fallback={<main><section className="card"><p>Cargando empresa…</p></section></main>}>
    <BusinessDetailPage />
  </Suspense>;
}
