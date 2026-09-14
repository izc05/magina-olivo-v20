import type { Metadata } from 'next';
import Link from 'next/link';
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
        <nav className="section-head" aria-label="Herramientas de Comunidad Mágina">
          <span>Comunidad Mágina</span>
          <div>
            <Link href="/comunidad/actividad">Mi actividad</Link>
            <Link href="/comunidad/descubrir">Buscar y descubrir</Link>
          </div>
        </nav>
        <CommunityClient />
      </div>
      <BottomNav active="/explorar" />
    </main>
  );
}
