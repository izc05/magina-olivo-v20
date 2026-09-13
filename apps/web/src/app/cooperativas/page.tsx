import { Suspense } from 'react';
import { PublicMillsPage } from '@/components/public-mills-page';
import { TerritoryReturnLink } from '@/components/territory-return-link';

export default function CooperativasPage() {
  return <Suspense fallback={<main><section className="card"><p>Cargando cooperativas y almazaras…</p></section></main>}><TerritoryReturnLink /><PublicMillsPage basePath="/cooperativas" /></Suspense>;
}
