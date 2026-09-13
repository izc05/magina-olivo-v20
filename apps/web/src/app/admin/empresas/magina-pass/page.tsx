import type { Metadata } from 'next';
import { MaginaPassAdmin } from '@/components/magina-pass-admin';
import '../../../admin.css';

export const metadata: Metadata = {
  title: 'Mágina Pass · Empresas · Administración',
  description: 'Administración de programas, paradas, QR y recompensas de Mágina Pass.',
  robots: { index: false, follow: false },
};

export default function MaginaPassAdminPage() {
  return <MaginaPassAdmin />;
}
