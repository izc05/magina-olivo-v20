import type { Metadata } from 'next';
import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { BusinessExperiencesPage } from '@/components/business-experiences-page';
import { Topbar } from '@/components/topbar';

export const metadata: Metadata = {
  title: 'Experiencias en Sierra Mágina · Mágina Olivo',
  description: 'Catas de AOVE, visitas, gastronomía, naturaleza y actividades ofrecidas por negocios de Sierra Mágina.',
};

export default function ExperienciasPage() {
  return (
    <div className="app-shell">
      <Topbar />
      <Suspense fallback={<main><section className="card"><p>Cargando experiencias…</p></section></main>}>
        <BusinessExperiencesPage />
      </Suspense>
      <BottomNav active="/explorar" />
    </div>
  );
}
