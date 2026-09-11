import { Suspense } from 'react';
import { PublicServicesPage } from '@/components/public-services-page';

export default function ServiciosPage() {
  return <Suspense fallback={<main><section className="card"><p>Cargando servicios locales…</p></section></main>}>
    <PublicServicesPage />
  </Suspense>;
}
