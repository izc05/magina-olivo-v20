import type { Metadata } from 'next';
import { Suspense } from 'react';
import { TownHubClient } from '../town-hub-client';

export const metadata: Metadata = {
  title: 'Territorio de Sierra Mágina · Mágina Olivo',
  description: 'Ficha territorial conectada con rutas, empresas, agenda y cooperativas publicadas.',
};

export default function TownTerritoryPage() {
  return <Suspense fallback={<main><section className="card"><p>Cargando territorio…</p></section></main>}><TownHubClient /></Suspense>;
}
