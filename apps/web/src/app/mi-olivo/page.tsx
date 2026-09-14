import type { Metadata } from 'next';
import { MiOlivoDashboard } from '../../components/mi-olivo-dashboard';
import { MiOlivoWeatherController } from '../../components/mi-olivo-weather-controller';
import { MiOlivoCampaignController } from '../../components/mi-olivo-campaign-controller';
import { MiOlivoDiscoveryCompass } from '../../components/mi-olivo-discovery-compass';
import { MiOlivoSeasonsController } from '../../components/mi-olivo-seasons-controller';
import { MiOlivoProgressionDashboard } from '../../components/mi-olivo-progression-dashboard';

export const metadata: Metadata = {
  title: 'Mi Olivo · Mágina Olivo',
  description: 'Tu pasaporte vivo para descubrir Sierra Mágina, crecer con cada experiencia y conservar tu historia.',
};

export default function MiOlivoPage() {
  return (
    <MiOlivoCampaignController>
      <MiOlivoWeatherController />
      <MiOlivoDashboard />
      <MiOlivoProgressionDashboard />
      <MiOlivoDiscoveryCompass />
      <MiOlivoSeasonsController />
    </MiOlivoCampaignController>
  );
}
