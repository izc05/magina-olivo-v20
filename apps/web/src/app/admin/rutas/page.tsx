import type { Metadata } from 'next';
import Link from 'next/link';
import { RoutesAdminConsole } from '../../../components/routes-admin-console';
import '../admin.css';
import './routes-admin.css';

export const metadata: Metadata = {
  title: 'Rutas · Administración · Mágina Olivo',
  description: 'Gestión de rutas, GPX, validación, comunidad y publicación de Mágina Olivo.',
  robots: { index: false, follow: false },
};

export default function AdminRoutesPage() {
  return <>
    <div style={{maxWidth: 1500, margin: '20px auto 0', padding: '0 24px'}}>
      <Link href="/admin/rutas/comunidad" className="admin-primary-button">Moderación de comunidad</Link>
    </div>
    <RoutesAdminConsole />
  </>;
}