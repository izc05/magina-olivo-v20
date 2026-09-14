import type { Metadata } from 'next';
import { AdminCommunityConsole } from '../../../components/admin-community-console';

export const metadata: Metadata = {
  title: 'Comunidad y moderación · Mágina Olivo',
  description: 'Cola de reportes y moderación auditada de Comunidad Mágina.',
  robots: { index: false, follow: false },
};

export default function AdminCommunityPage() {
  return <AdminCommunityConsole />;
}
