import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { FarmEditClient } from '@/components/farm-edit-client';
import { Topbar } from '@/components/topbar';

export default function EditFarmPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page mi-campo-page">
        <Suspense fallback={<section className="card"><p>Cargando finca…</p></section>}>
          <FarmEditClient />
        </Suspense>
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
