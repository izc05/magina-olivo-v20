import { Suspense } from 'react';
import { ProfessionalCollectionEntryClient } from '@/components/professional-collection-entry-client';

export default function ProfessionalCollectionPage() {
  return <Suspense fallback={<section className="card"><p>Cargando cobro…</p></section>}><ProfessionalCollectionEntryClient /></Suspense>;
}
