import type { Metadata } from 'next';
import { SiteAdminConsoleV2 } from '../../../components/site-admin-console-v2';
import '../admin.css';
import './site-admin.css';
import './site-admin-v2.css';

export const metadata: Metadata = {
  title: 'Editar web · Administración · Mágina Olivo',
  description: 'Editor visual del contenido público de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminWebPage() {
  return <SiteAdminConsoleV2 />;
}
