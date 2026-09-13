import type { Metadata } from 'next';
import { RoutesAdminConsole } from '../../../components/routes-admin-console';
import '../admin.css';
import './routes-admin.css';

export const metadata: Metadata = {
  title: 'Rutas · Administración · Mágina Olivo',
  description: 'Gestión de rutas, GPX, validación y publicación de Mágina Olivo.',
  robots: { index: false, follow: false },
};

export default function AdminRoutesPage() {
  return <RoutesAdminConsole />;
}
