import type { Metadata } from 'next';
import { BusinessAdminConsole } from '@/components/business-admin-console';
import '../admin.css';

export const metadata: Metadata = {
  title: 'Empresas · Administración · Mágina Olivo',
  description: 'Gestión del directorio estructurado de empresas y servicios de Sierra Mágina.',
  robots: { index: false, follow: false },
};

export default function AdminEmpresasPage() {
  return <BusinessAdminConsole />;
}
