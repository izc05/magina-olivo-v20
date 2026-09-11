import type { Metadata } from 'next';
import { AdminControlCenter } from '../../components/admin-control-center';
import './admin.css';

export const metadata: Metadata = {
  title: 'Administración · Mágina Olivo',
  description: 'Centro de control corporativo de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminPage() {
  return <AdminControlCenter />;
}
