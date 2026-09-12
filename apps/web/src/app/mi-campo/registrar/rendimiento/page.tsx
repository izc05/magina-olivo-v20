import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { HarvestResultEntryClient } from '@/components/harvest-result-entry-client';
import { Topbar } from '@/components/topbar';

export default function HarvestResultPage() {
  return <main className="app-shell"><Topbar/><div className="page ocr-page"><Suspense fallback={<section className="card"><p>Cargando rendimiento…</p></section>}><HarvestResultEntryClient/></Suspense></div><BottomNav active="/mi-campo"/></main>;
}
