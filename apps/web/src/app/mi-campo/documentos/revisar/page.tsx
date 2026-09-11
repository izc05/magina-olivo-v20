import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { DocumentReviewClient } from '@/components/document-review-client';
import { Topbar } from '@/components/topbar';

export default function ReviewDocumentPage() {
  return <main className="app-shell"><Topbar/><div className="page mi-campo-page"><Suspense fallback={<section className="card"><p>Cargando revisión…</p></section>}><DocumentReviewClient/></Suspense></div><BottomNav active="/mi-campo"/></main>;
}
