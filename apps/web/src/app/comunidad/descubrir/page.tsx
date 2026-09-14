import type { Metadata } from 'next';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { CommunityDiscoveryClient } from './discovery-client';

export const metadata: Metadata = {
  title: 'Descubrir en Comunidad Mágina · Mágina Olivo',
  description: 'Busca conversaciones públicas de Sierra Mágina y descubre las más comentadas o valoradas.',
};

export default function CommunityDiscoveryPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page">
        <CommunityDiscoveryClient />
      </div>
      <BottomNav active="/explorar" />
    </main>
  );
}
