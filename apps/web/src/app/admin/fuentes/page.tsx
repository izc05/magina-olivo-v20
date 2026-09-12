import type { Metadata } from 'next';
import { AdminSourcesConsole } from '../../../components/admin-sources-console';
import '../admin.css';
import './sources-admin.css';

export const metadata: Metadata = {
  title: 'Fuentes y datos | Mágina Olivo Admin',
  description: 'Telemetría y acciones operativas seguras sobre AEMET, radar, OCR, Catastro y SIGPAC.',
};

export default function AdminSourcesPage() {
  return <AdminSourcesConsole />;
}
