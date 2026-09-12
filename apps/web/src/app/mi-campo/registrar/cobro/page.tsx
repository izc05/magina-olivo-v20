import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { HarvestCollectionEntryClient } from '@/components/harvest-collection-entry-client';
import { Topbar } from '@/components/topbar';

export default function HarvestCollectionPage() {
  return <main className="app-shell"><Topbar/><div className="page mi-campo-page"><Suspense fallback={<section className="card"><p>Cargando cobro…</p></section>}><HarvestCollectionEntryClient/></Suspense></div><BottomNav active="/mi-campo"/></main>;
}
