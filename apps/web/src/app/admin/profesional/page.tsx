import type { Metadata } from 'next';
import { AdminProfessionalConsole } from '../../../components/admin-professional-console';

export const metadata: Metadata = {
  title: 'Profesional · Administración · Mágina Olivo',
  description: 'Soporte global de clientes, presupuestos, facturas y cobros de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminProfesionalPage() {
  return <AdminProfessionalConsole />;
}
