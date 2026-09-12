import type { Metadata } from 'next';
import { AdminDocumentsConsole } from '../../../components/admin-documents-console';

export const metadata: Metadata = {
  title: 'Documentos y OCR · Administración · Mágina Olivo',
  description: 'Soporte administrativo de documentos, integridad y procesos OCR de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminDocumentsPage() { return <AdminDocumentsConsole />; }
