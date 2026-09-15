import type { Metadata } from 'next';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { RoutesPublicClient } from './routes-public-client';
import styles from './routes-public.module.css';

export const metadata: Metadata = {
  title: 'Rutas · Mágina Olivo',
  description: 'Rutas de Sierra Mágina con track validado, desnivel y fuentes trazables.',
};

export default function RoutesPage() {
  return <main className="app-shell">
    <Topbar />
    <div className={`page ${styles.page}`}><RoutesPublicClient /></div>
    <BottomNav active="/explorar" />
  </main>;
}
