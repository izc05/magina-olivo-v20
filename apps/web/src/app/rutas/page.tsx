import type { Metadata } from 'next';
import { RoutesPublicClient } from './routes-public-client';
import styles from './routes-public.module.css';

export const metadata: Metadata = {
  title: 'Rutas · Mágina Olivo',
  description: 'Rutas de Sierra Mágina con track validado, desnivel y fuentes trazables.',
};

export default function RoutesPage() {
  return <div className={styles.page}><RoutesPublicClient /></div>;
}
