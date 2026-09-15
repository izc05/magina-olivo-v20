import type { Metadata } from 'next';
import { MediaLibraryConsole } from '../../../components/media-library-console';
import '../admin.css';
import './media-admin.css';

export const metadata: Metadata = {
  title: 'Multimedia · Administración · Mágina Olivo',
  description: 'Biblioteca corporativa de imágenes de Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminMediaPage() {
  return <MediaLibraryConsole />;
}
