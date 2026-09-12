import type { Metadata } from 'next';
import Link from 'next/link';
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
      <div style={{ position: 'fixed', right: 18, bottom: 18, zIndex: 50, display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end', maxWidth: 'min(1180px, calc(100vw - 36px))' }}>
        <Link href="/admin/operaciones" aria-label="Abrir centro operativo de plataforma" style={{ ...shortcutStyle, background: '#173f2a' }}>Operaciones</Link>
        <Link href="/admin/analitica" aria-label="Abrir analítica histórica de plataforma" style={{ ...shortcutStyle, background: '#245e4a' }}>Analítica</Link>
        <Link href="/admin/gestion" aria-label="Abrir gestión de workspaces, miembros y fincas" style={{ ...shortcutStyle, background: '#325746' }}>Gestión</Link>
        <Link href="/admin/campanas-planes" aria-label="Abrir administración de campañas y planes" style={{ ...shortcutStyle, background: '#4b5c33' }}>Campañas</Link>
        <Link href="/admin/agenda" aria-label="Abrir agenda global" style={{ ...shortcutStyle, background: '#5c643f' }}>Agenda</Link>
        <Link href="/admin/trabajos" aria-label="Abrir trabajos y actividad agrícola" style={{ ...shortcutStyle, background: '#385f35' }}>Trabajos</Link>
        <Link href="/admin/documentos" aria-label="Abrir soporte de documentos y OCR" style={{ ...shortcutStyle, background: '#3b5867' }}>Documentos/OCR</Link>
        <Link href="/admin/profesional" aria-label="Abrir soporte comercial profesional" style={{ ...shortcutStyle, background: '#4d4f73' }}>Profesional</Link>
        <Link href="/admin/fuentes" aria-label="Abrir estado de fuentes y datos" style={{ ...shortcutStyle, background: '#46666d' }}>Fuentes</Link>
        <Link href="/admin/territorio" aria-label="Abrir administración de territorio y directorio" style={{ ...shortcutStyle, background: '#8a6c2f' }}>Territorio</Link>
        <Link href="/admin/media" aria-label="Abrir biblioteca multimedia" style={{ ...shortcutStyle, background: '#6b7446' }}>Multimedia</Link>
        <Link href="/admin/web" aria-label="Abrir editor visual de la web" style={{ ...shortcutStyle, background: '#315c3a' }}>Editar web</Link>
      </div>
    </>
  );
}
