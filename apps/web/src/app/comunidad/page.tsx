import type { Metadata } from 'next';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { CommunityClient } from './community-client';

export const metadata: Metadata = {
  title: 'Comunidad Mágina · Mágina Olivo',
  description: 'Conversaciones de campo, pueblos, cosecha, maquinaria, gastronomía y rutas de Sierra Mágina.',
};

export default function CommunityPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page">
        <CommunityClient />
      </div>
      <BottomNav active="/explorar" />
    </main>
  );
}
