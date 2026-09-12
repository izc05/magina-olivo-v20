import type { Metadata } from 'next';
import { MiOlivoDashboard } from '../../components/mi-olivo-dashboard';
import { MiOlivoWeatherController } from '../../components/mi-olivo-weather-controller';

export const metadata: Metadata = {
  title: 'Mi Olivo · Mágina Olivo',
  description: 'Tu progreso en Mágina a partir de acciones reales y útiles.',
};

export default function MiOlivoPage() {
  return <>
    <MiOlivoWeatherController />
    <MiOlivoDashboard />
  </>;
}
