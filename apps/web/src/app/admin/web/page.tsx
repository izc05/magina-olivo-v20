import type { Metadata } from 'next';
import { SiteAdminConsole } from '../../../components/site-admin-console';
import '../admin.css';
import './site-admin.css';

export const metadata: Metadata = {
  title: 'Editar web · Administración · Mágina Olivo',
  description: 'Editor visual del contenido público de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminWebPage() {
  return (
    <>
      <SiteAdminConsole />
      <a
        href="/admin/media"
        aria-label="Abrir biblioteca multimedia"
        style={{
          position: 'fixed',
          right: 18,
          bottom: 18,
          zIndex: 60,
          borderRadius: 999,
          padding: '11px 15px',
          background: '#6b7446',
          color: '#fff',
          fontWeight: 800,
          textDecoration: 'none',
          boxShadow: '0 12px 28px rgba(64, 72, 43, .26)',
        }}
      >
        Multimedia
      </a>
    </>
  );
}
