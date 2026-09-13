import type { Metadata } from 'next';
import { Suspense } from 'react';
import { PublicEditorialPage } from '../../components/public-editorial-page';
import { TerritoryReturnLink } from '../../components/territory-return-link';

export const metadata: Metadata = {
  title: 'Eventos · Mágina Olivo',
  description: 'Agenda pública de jornadas, ferias, cultura y actividades de Sierra Mágina.',
};

export default function EventsPage() {
  return <Suspense fallback={null}><TerritoryReturnLink /><PublicEditorialPage type="event" /></Suspense>;
}
