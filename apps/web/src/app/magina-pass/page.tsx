import type { Metadata } from 'next';
import { BottomNav } from '@/components/bottom-nav';
import { MaginaPassPage } from '@/components/magina-pass-page';
import { Topbar } from '@/components/topbar';

export const metadata: Metadata = {
  title: 'Mágina Pass · Descubre y apoya lo local',
  description: 'Pasaporte territorial de Sierra Mágina con negocios, puntos y recompensas.',
};

export default function MaginaPassRoute() {
  return (
    <div className="app-shell">
      <Topbar />
      <MaginaPassPage />
      <BottomNav active="/explorar" />
    </div>
  );
}
