import type { Metadata } from 'next';
import Link from 'next/link';
import { RoutesAdminConsole } from '../../../components/routes-admin-console';
import '../admin.css';
import './routes-admin.css';

export const metadata: Metadata = {
  title: 'Rutas · Administración · Mágina Olivo',
  description: 'Gestión de rutas, GPX, validación, comunidad, patrocinios y publicación de Mágina Olivo.',
  robots: { index: false, follow: false },
};

export default function AdminRoutesPage() {
  return <>
    <div className="admin-actions" style={{maxWidth:1500,margin:'20px auto 0',padding:'0 24px'}}>
      <Link href="/admin/rutas/aventuras" className="admin-button">Candidatas Mágina Aventura</Link>
      <Link href="/admin/rutas/comunidad" className="admin-button secondary">Moderación de comunidad</Link>
      <Link href="/admin/rutas/patrocinios" className="admin-button secondary">Patrocinios y métricas</Link>
    </div>
    <RoutesAdminConsole />
  </>;
}