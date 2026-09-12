import type { Metadata } from 'next';
import { MiOlivoDashboard } from '../../components/mi-olivo-dashboard';
import { MiOlivoWeatherController } from '../../components/mi-olivo-weather-controller';
import { MiOlivoCampaignController } from '../../components/mi-olivo-campaign-controller';
import { MiOlivoSeasonsController } from '../../components/mi-olivo-seasons-controller';

export const metadata: Metadata = {
  title: 'Mi Olivo · Mágina Olivo',
  description: 'Tu progreso en Mágina a partir de acciones reales y útiles.',
};

export default function MiOlivoPage() {
  return (
    <MiOlivoCampaignController>
      <MiOlivoWeatherController />
      <MiOlivoDashboard />
      <MiOlivoSeasonsController />
    </MiOlivoCampaignController>
  );
}
