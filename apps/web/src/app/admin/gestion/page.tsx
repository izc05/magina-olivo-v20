import type { Metadata } from 'next';
import { AdminManagementConsole } from '../../../components/admin-management-console';

export const metadata: Metadata = {
  title: 'Gestión de datos · Mágina Olivo',
  description: 'Gestión administrativa auditada de espacios, miembros y fincas.',
  robots: { index: false, follow: false },
};

export default function AdminManagementPage() {
  return <AdminManagementConsole />;
}
