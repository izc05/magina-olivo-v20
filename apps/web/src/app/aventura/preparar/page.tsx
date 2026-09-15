import type { Metadata } from 'next';
import { AdventurePrepareClient } from './adventure-prepare-client';

export const metadata: Metadata = {
  title: 'Preparar aventura · Mágina Aventura',
  description: 'Revisa ruta, seguridad y clima local antes de iniciar una expedición en Sierra Mágina.',
};

export default function AdventurePreparePage() {
  return <AdventurePrepareClient />;
}
