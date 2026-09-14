import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { PublicPlacesPage } from '@/components/public-places-page';
import { Topbar } from '@/components/topbar';

export default function PueblosPage() {
  return <main className="app-shell">
    <Topbar />
    <div className="page">
      <Suspense fallback={<section className="card"><p>Cargando pueblos de Sierra Mágina…</p></section>}>
        <PublicPlacesPage />
      </Suspense>
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
