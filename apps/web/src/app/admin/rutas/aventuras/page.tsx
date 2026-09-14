import type { Metadata } from 'next';
import { RouteAdventureCandidates } from '../../../../components/route-adventure-candidates';
import '../../admin.css';
import '../routes-admin.css';

export const metadata: Metadata = {
  title: 'Candidatas Mágina Aventura · Administración · Mágina Olivo',
  description: 'Análisis de rutas reales y preparación de experiencias de Mágina Aventura.',
  robots: { index: false, follow: false },
};

export default function AdventureCandidatesPage() {
  return <RouteAdventureCandidates />;
}
