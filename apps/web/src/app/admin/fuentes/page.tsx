import type { Metadata } from 'next';
import { AdminSourcesConsole } from '../../../components/admin-sources-console';
import '../admin.css';
import './sources-admin.css';

export const metadata: Metadata = {
  title: 'Fuentes y datos · Administración · Mágina Olivo',
  description: 'Telemetría administrativa de fuentes de datos y pipelines de Mágina Olivo.',
  robots: { index: false, follow: false },
};

export default function AdminSourcesPage() {
  return <AdminSourcesConsole />;
}
