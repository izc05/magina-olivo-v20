import { Suspense } from 'react';
import { PublicCommercialShareClient } from '@/components/public-commercial-share-client';

export default function PublicCommercialDocumentPage() {
  return <Suspense fallback={<main className="public-doc-shell"><section className="public-doc-card"><p>Preparando documento…</p></section></main>}><PublicCommercialShareClient /></Suspense>;
}
