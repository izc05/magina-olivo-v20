import type { Metadata } from 'next';
import { AdminWorkActivityConsole } from '../../../components/admin-work-activity-console';

export const metadata: Metadata = {
  title: 'Trabajos · Administración · Mágina Olivo',
  description: 'Consulta y corrección administrativa de trabajos y actividad agrícola de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminWorkActivityPage() {
  return <AdminWorkActivityConsole />;
}
