import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { HarvestSettlementEntryClient } from '@/components/harvest-settlement-entry-client';
import { Topbar } from '@/components/topbar';

export default function HarvestSettlementPage() {
  return <main className="app-shell"><Topbar/><div className="page mi-campo-page"><Suspense fallback={<section className="card"><p>Cargando liquidación…</p></section>}><HarvestSettlementEntryClient/></Suspense></div><BottomNav active="/mi-campo"/></main>;
}
