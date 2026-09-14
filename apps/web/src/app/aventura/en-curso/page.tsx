import type { Metadata } from 'next';
import { AdventureLiveClient } from './adventure-live-client';

export const metadata: Metadata = {
  title: 'Aventura en curso · Mágina Aventura',
  description: 'Pantalla móvil de expedición para seguir una ruta real, checkpoints, progreso y grabación GPS privada en Sierra Mágina.',
};

export default function AdventureLivePage() {
  return <AdventureLiveClient />;
}
