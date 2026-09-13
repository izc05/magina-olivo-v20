import type { Metadata } from 'next';
import { RoutesSponsorshipAdmin } from '../../../../components/routes-sponsorship-admin';
import '../../admin.css';

export const metadata: Metadata = {
  title: 'Patrocinios de rutas · Administración · Mágina Olivo',
  description: 'Gestión y métricas de patrocinio contextual de rutas.',
  robots: { index: false, follow: false },
};

export default function AdminRoutesSponsorshipPage() {
  return <RoutesSponsorshipAdmin />;
}