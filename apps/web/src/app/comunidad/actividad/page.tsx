import type { Metadata } from 'next';
import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { CommunityActivityClient } from './activity-client';

export const metadata: Metadata = {
  title: 'Mi actividad · Comunidad Mágina',
  description: 'Likes, comentarios y respuestas de tu actividad en Comunidad Mágina.',
};

export default function CommunityActivityPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page">
        <nav className="section-head" aria-label="Herramientas de Comunidad Mágina">
          <span>Mi actividad</span>
          <Link href="/comunidad">Volver a Comunidad</Link>
        </nav>
        <CommunityActivityClient />
      </div>
      <BottomNav active="/explorar" />
    </main>
  );
}
