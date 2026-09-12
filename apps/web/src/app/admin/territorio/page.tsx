import type { Metadata } from 'next';
import { TerritoryAdminConsole } from '../../../components/territory-admin-console';
import '../admin.css';
import '../web/site-admin.css';
import '../web/site-admin-v2.css';
import './territory-admin.css';

export const metadata: Metadata = {
  title: 'Territorio y directorio · Administración · Mágina Olivo',
  description: 'Gestión corporativa de pueblos, cooperativas, almazaras y servicios locales de Mágina Olivo.',
  robots: { index: false, follow: false },
};

export default function AdminTerritoryPage() {
  return <TerritoryAdminConsole />;
}
