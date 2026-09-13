import { Suspense } from 'react';
import { BusinessDetailPage } from '@/components/business-detail-page';
import { BusinessNearbyRoutes } from '@/components/business-nearby-routes';

export default function EmpresaPage() {
  return <Suspense fallback={<main><section className="card"><p>Cargando empresa…</p></section></main>}>
    <BusinessDetailPage />
    <BusinessNearbyRoutes />
  </Suspense>;
}
