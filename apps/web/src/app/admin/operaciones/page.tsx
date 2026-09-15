import type { Metadata } from 'next';
import { AdminOperationsHub } from '../../../components/admin-operations-hub';

export const metadata: Metadata = {
  title: 'Centro operativo · Mágina Olivo',
  description: 'Métricas, datos, fuentes y aplicaciones de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminOperationsPage() {
  return <AdminOperationsHub />;
}
