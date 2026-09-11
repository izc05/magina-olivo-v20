import type { Metadata } from 'next';
import { SiteAdminConsole } from '../../../components/site-admin-console';
import '../admin.css';
import './site-admin.css';

export const metadata: Metadata = {
  title: 'Editar web · Administración · Mágina Olivo',
  description: 'Editor visual del contenido público de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminWebPage() {
  return <SiteAdminConsole />;
}
