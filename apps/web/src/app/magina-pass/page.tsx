import type { Metadata } from 'next';
import { MaginaPassPage } from '@/components/magina-pass-page';

export const metadata: Metadata = {
  title: 'Mágina Pass · Descubre y apoya lo local',
  description: 'Pasaporte territorial de Sierra Mágina con negocios, puntos y recompensas.',
};

export default function MaginaPassRoute() {
  return <MaginaPassPage />;
}
