import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { HarvestOcrClient } from '@/components/harvest-ocr-client';

export default function HarvestOcrPage(){
  return <main className="app-shell"><Topbar/><div className="page ocr-page"><Suspense fallback={<section className="card"><p>Cargando cosecha…</p></section>}><HarvestOcrClient/></Suspense></div><BottomNav active="/mi-campo"/></main>;
}
