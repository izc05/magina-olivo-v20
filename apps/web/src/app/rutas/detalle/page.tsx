import type { Metadata } from 'next';
import { RouteDetailClient } from './route-detail-client';

export const metadata: Metadata = {
  title: 'Detalle de ruta · Mágina Olivo',
  description: 'Ficha de ruta con track validado y perfil de elevación.',
};

export default function RouteDetailPage() {
  return <RouteDetailClient />;
}
