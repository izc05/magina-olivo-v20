import type { Metadata } from 'next';
import { RouteActivityRecorder } from './route-activity-recorder';
import styles from '../adventure.module.css';

export const metadata: Metadata = {
  title: 'Grabar recorrido · Mágina Aventura',
  description: 'Registra de forma voluntaria y privada la distancia y el tiempo de un recorrido por Sierra Mágina.',
};

export default function RouteActivityPage() {
  return <div className={styles.page}><RouteActivityRecorder /></div>;
}
