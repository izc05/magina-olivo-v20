import type { Metadata } from 'next';
import { MiOlivoCampaignComparison } from '../../components/mi-olivo-campaign-comparison';
import { MiOlivoCareController } from '../../components/mi-olivo-care-controller';
import { MiOlivoDashboard } from '../../components/mi-olivo-dashboard';
import { MiOlivoWeatherController } from '../../components/mi-olivo-weather-controller';
import { MiOlivoCampaignController } from '../../components/mi-olivo-campaign-controller';
import { MiOlivoDiscoveryCompass } from '../../components/mi-olivo-discovery-compass';
import { MiOlivoSeasonsController } from '../../components/mi-olivo-seasons-controller';
import { MiOlivoProgressionDashboard } from '../../components/mi-olivo-progression-dashboard';
import { MiOlivoTimelineController } from '../../components/mi-olivo-timeline-controller';
import { MiOlivoWelcomeController } from '../../components/mi-olivo-welcome-controller';

export const metadata: Metadata = {
  title: 'Mi Olivo · Mágina Olivo',
  description: 'Tu pasaporte vivo para descubrir Sierra Mágina, crecer con cada experiencia y conservar tu historia.',
};

export default function MiOlivoPage() {
  return (
    <MiOlivoCampaignController>
      <MiOlivoWeatherController />
      <MiOlivoWelcomeController />
      <MiOlivoDashboard />
      <MiOlivoProgressionDashboard />
      <MiOlivoDiscoveryCompass />
      <MiOlivoSeasonsController />
      <MiOlivoCampaignComparison />
      <MiOlivoCareController />
      <MiOlivoTimelineController />
    </MiOlivoCampaignController>
  );
}
