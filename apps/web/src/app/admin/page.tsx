import type { Metadata } from 'next';
import { AdminControlCenter } from '../../components/admin-control-center';
import './admin.css';

export const metadata: Metadata = {
  title: 'Administración · Mágina Olivo',
  description: 'Centro de control corporativo de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminPage() {
  return (
    <>
      <AdminControlCenter />
      <a
        href="/admin/web"
        aria-label="Abrir editor visual de la web"
        style={{
          position: 'fixed',
          right: 18,
          bottom: 18,
          zIndex: 50,
          borderRadius: 999,
          padding: '12px 16px',
          background: '#315c3a',
          color: '#fff',
          fontWeight: 800,
          textDecoration: 'none',
          boxShadow: '0 12px 30px rgba(37, 74, 44, .28)',
        }}
      >
        Editar web
      </a>
    </>
  );
}
