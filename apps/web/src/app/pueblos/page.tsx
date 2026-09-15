import { Suspense } from 'react';
import { PublicPlacesPage } from '@/components/public-places-page';

export default function PueblosPage() {
  return <Suspense fallback={<main><section className="card"><p>Cargando pueblos de Sierra Mágina…</p></section></main>}>
    <PublicPlacesPage />
  </Suspense>;
}
