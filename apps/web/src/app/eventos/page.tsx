import type { Metadata } from 'next';
import { PublicEditorialPage } from '../../components/public-editorial-page';

export const metadata: Metadata = {
  title: 'Eventos · Mágina Olivo',
  description: 'Agenda pública de jornadas, ferias, cultura y actividades de Sierra Mágina.',
};

export default function EventsPage() {
  return <PublicEditorialPage type="event" />;
}
