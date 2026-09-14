import type { Metadata } from 'next';
import { Suspense } from 'react';
import { RouteActivityDetailClient } from './route-activity-detail-client';
import styles from '../activity.module.css';

export const metadata: Metadata = {
  title: 'Recorrido grabado · Mágina Aventura',
  description: 'Consulta de forma privada el mapa y las métricas de un recorrido grabado.',
};

export default function RouteActivityDetailPage() {
  return <Suspense fallback={<main className={styles.shell}><section className={styles.card}><h1>Preparando tu recorrido…</h1><p>Cargando el track privado y sus métricas.</p></section></main>}><RouteActivityDetailClient /></Suspense>;
}
