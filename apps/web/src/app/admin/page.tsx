import type { Metadata } from 'next';
import Link from 'next/link';
import { AdminControlCenter } from '../../components/admin-control-center';
import moduleRegistry from './modulos/admin-modules.json';
import './admin.css';

export const metadata: Metadata = {
  title: 'Administración · Mágina Olivo',
  description: 'Centro de control corporativo de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

type AdminShortcut = {
  id: string;
  title: string;
  status: 'available' | 'implemented';
  href?: string;
};

const availableShortcuts = (moduleRegistry as AdminShortcut[])
  .filter((module) => module.status === 'available' && module.href)
  .sort((a, b) => a.title.localeCompare(b.title, 'es'));

export default function AdminPage() {
  return (
    <>
      <AdminControlCenter />
      <nav className="admin-shortcuts" aria-label="Herramientas de administración">
        <Link href="/admin/modulos" aria-label="Abrir directorio unificado de módulos">Módulos</Link>
        {availableShortcuts.map((module) => (
          <Link href={module.href!} aria-label={`Abrir administración de ${module.title}`} key={module.id}>
            {module.title}
          </Link>
        ))}
      </nav>
    </>
  );
}
