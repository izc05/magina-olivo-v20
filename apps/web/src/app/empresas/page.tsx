import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { BusinessDetailPage } from '@/components/business-detail-page';
import { Topbar } from '@/components/topbar';

export default function EmpresaPage() {
  return (
    <div className="app-shell">
      <Topbar />
      <Suspense fallback={<main><section className="card"><p>Cargando empresa…</p></section></main>}>
        <BusinessDetailPage />
      </Suspense>
      <BottomNav active="/explorar" />
    </div>
  );
}
