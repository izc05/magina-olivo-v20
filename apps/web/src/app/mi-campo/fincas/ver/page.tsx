import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { FarmDetailShell } from '@/components/farm-detail-shell';
import { Topbar } from '@/components/topbar';

export default function FarmDetailPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page mi-campo-page">
        <Suspense fallback={<section className="card"><p>Cargando finca…</p></section>}>
          <FarmDetailShell />
        </Suspense>
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
