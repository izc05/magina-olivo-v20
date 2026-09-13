import type { Metadata } from 'next';
import { RoutesCommunityAdmin } from '../../../../components/routes-community-admin';
import '../../admin.css';

export const metadata: Metadata = {
  title: 'Comunidad de rutas · Administración · Mágina Olivo',
  description: 'Moderación de reseñas, fotos, avisos y denuncias de rutas.',
  robots: { index: false, follow: false },
};

export default function AdminRoutesCommunityPage() {
  return <RoutesCommunityAdmin />;
}