import type { Metadata } from 'next';
import Link from 'next/link';
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
      <nav className="admin-shortcuts" aria-label="Herramientas de administración">
        <Link href="/admin/operaciones" aria-label="Abrir centro operativo de plataforma">Operaciones</Link>
        <Link href="/admin/analitica" aria-label="Abrir analítica histórica de plataforma">Analítica</Link>
        <Link href="/admin/gestion" aria-label="Abrir gestión de workspaces, miembros y fincas">Gestión</Link>
        <Link href="/admin/campanas-planes" aria-label="Abrir administración de campañas y planes">Campañas</Link>
        <Link href="/admin/agenda" aria-label="Abrir agenda global">Agenda</Link>
        <Link href="/admin/trabajos" aria-label="Abrir trabajos y actividad agrícola">Trabajos</Link>
        <Link href="/admin/documentos" aria-label="Abrir soporte de documentos y OCR">Documentos/OCR</Link>
        <Link href="/admin/profesional" aria-label="Abrir soporte comercial profesional">Profesional</Link>
        <Link href="/admin/fuentes" aria-label="Abrir estado de fuentes y datos">Fuentes</Link>
        <Link href="/admin/territorio" aria-label="Abrir administración de territorio y directorio">Territorio</Link>
        <Link href="/admin/media" aria-label="Abrir biblioteca multimedia">Multimedia</Link>
        <Link href="/admin/web" aria-label="Abrir editor visual de la web">Editar web</Link>
      </nav>
    </>
  );
}
