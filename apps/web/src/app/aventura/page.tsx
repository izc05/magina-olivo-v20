import type { Metadata } from 'next';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { AdventureHubClient } from './adventure-hub-client';
import { AdventureResumeBanner } from './adventure-resume-banner';
import { AdventureTerritoryBoard } from './adventure-territory-board';
import styles from './adventure.module.css';

export const metadata: Metadata = {
  title: 'Mágina Aventura · Mágina Olivo',
  description: 'Explora Sierra Mágina caminando: retos geolocalizados, puntos, insignias y descubrimientos sobre rutas validadas.',
};

export default function AdventurePage() {
  return <main className="app-shell">
    <Topbar />
    <div className={`page ${styles.page}`}>
      <AdventureHubClient />
      <AdventureResumeBanner />
      <AdventureTerritoryBoard />
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
