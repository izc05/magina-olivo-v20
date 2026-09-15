import type { Metadata } from 'next';
import { Suspense } from 'react';
import { BusinessExperiencesPage } from '@/components/business-experiences-page';

export const metadata: Metadata = {
  title: 'Experiencias en Sierra Mágina · Mágina Olivo',
  description: 'Catas de AOVE, visitas, gastronomía, naturaleza y actividades ofrecidas por negocios de Sierra Mágina.',
};

export default function ExperienciasPage() {
  return <Suspense fallback={<main style={{padding:32}}>Cargando experiencias…</main>}><BusinessExperiencesPage /></Suspense>;
}
