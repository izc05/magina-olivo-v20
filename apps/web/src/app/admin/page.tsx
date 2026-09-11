import type { Metadata } from 'next';
import { AdminControlCenter } from '../../components/admin-control-center';
import './admin.css';

export const metadata: Metadata = {
  title: 'Administración · Mágina Olivo',
  description: 'Centro de control corporativo de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

const shortcutStyle = {
  borderRadius: 999,
  padding: '12px 16px',
  color: '#fff',
  fontWeight: 800,
  textDecoration: 'none',
  boxShadow: '0 12px 30px rgba(37, 74, 44, .24)',
} as const;

export default function AdminPage() {
  return (
    <>
      <AdminControlCenter />
      <div style={{ position: 'fixed', right: 18, bottom: 18, zIndex: 50, display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
        <a href="/admin/territorio" aria-label="Abrir administración de territorio y directorio" style={{ ...shortcutStyle, background: '#8a6c2f' }}>Territorio</a>
        <a href="/admin/media" aria-label="Abrir biblioteca multimedia" style={{ ...shortcutStyle, background: '#6b7446' }}>Multimedia</a>
        <a href="/admin/web" aria-label="Abrir editor visual de la web" style={{ ...shortcutStyle, background: '#315c3a' }}>Editar web</a>
      </div>
    </>
  );
}
