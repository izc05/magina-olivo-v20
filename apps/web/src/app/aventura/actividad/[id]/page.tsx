import type { Metadata } from 'next';
import { RouteActivityDetailClient } from './route-activity-detail-client';

export const metadata: Metadata = {
  title: 'Recorrido grabado · Mágina Aventura',
  description: 'Consulta de forma privada el mapa y las métricas de un recorrido grabado.',
};

export default async function RouteActivityDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <RouteActivityDetailClient activityId={id} />;
}
