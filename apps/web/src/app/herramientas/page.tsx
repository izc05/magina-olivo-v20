import type { Metadata } from 'next';
import { BottomNav } from '@/components/bottom-nav';
import { FarmToolsClient } from '@/components/farm-tools-client';
import { Topbar } from '@/components/topbar';

export const metadata: Metadata = {
  title: 'Herramientas rápidas · Mágina Olivo',
  description: 'Cálculos locales de superficie, marco de plantación y coste unitario para organizar el trabajo del olivar.',
};

export default function FarmToolsPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <FarmToolsClient />
      <BottomNav active="/mi-campo" />
    </main>
  );
}
