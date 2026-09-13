import { Suspense } from 'react';
import { BusinessDetailPage } from '@/components/business-detail-page';
import { BusinessNearbyRoutes } from '@/components/business-nearby-routes';
import { TerritoryReturnLink } from '@/components/territory-return-link';

export default function EmpresaPage() {
  return <Suspense fallback={<main><section className="card"><p>Cargando empresa…</p></section></main>}>
    <TerritoryReturnLink />
    <BusinessDetailPage />
    <BusinessNearbyRoutes />
  </Suspense>;
}
