import type { Metadata } from 'next';
import { MiOlivoDashboard } from '../../components/mi-olivo-dashboard';
import { MiOlivoWeatherController } from '../../components/mi-olivo-weather-controller';
import { MiOlivoCampaignController } from '../../components/mi-olivo-campaign-controller';
import { MiOlivoSeasonsController } from '../../components/mi-olivo-seasons-controller';
import { MiOlivoProgressionDashboard } from '../../components/mi-olivo-progression-dashboard';

export const metadata: Metadata = {
  title: 'Mi Olivo · Mágina Olivo',
  description: 'Tu progreso en Mágina a partir de acciones reales y útiles.',
};

export default function MiOlivoPage() {
  return (
    <MiOlivoCampaignController>
      <MiOlivoWeatherController />
      <MiOlivoDashboard />
      <MiOlivoProgressionDashboard />
      <MiOlivoSeasonsController />
    </MiOlivoCampaignController>
  );
}
