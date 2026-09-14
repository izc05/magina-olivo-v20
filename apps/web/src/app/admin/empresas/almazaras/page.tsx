import type { Metadata } from 'next';
import { AlmazaraAdminConsole } from '@/components/almazara-admin-console';
import '../../admin.css';

export const metadata: Metadata = {
  title: 'Almazaras y premios · Administración · Mágina Olivo',
  description: 'Supervisión de recompensas, stock y canjes de almazaras.',
  robots: { index: false, follow: false },
};

export default function AdminAlmazarasPage() {
  return <AlmazaraAdminConsole />;
}
