import { Suspense } from 'react';
import { PublicMillsPage } from '@/components/public-mills-page';

export default function CooperativasPage() {
  return <Suspense fallback={<main><section className="card"><p>Cargando cooperativas y almazaras…</p></section></main>}><PublicMillsPage basePath="/cooperativas" /></Suspense>;
}
