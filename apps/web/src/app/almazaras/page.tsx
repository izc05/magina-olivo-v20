import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { PublicMillsPage } from '@/components/public-mills-page';
import { Topbar } from '@/components/topbar';

export default function AlmazarasPage() {
  return (
    <div className="app-shell">
      <Topbar />
      <Suspense fallback={<main><section className="card"><p>Cargando cooperativas y almazaras…</p></section></main>}>
        <PublicMillsPage basePath="/almazaras" />
      </Suspense>
      <BottomNav active="/explorar" />
    </div>
  );
}
