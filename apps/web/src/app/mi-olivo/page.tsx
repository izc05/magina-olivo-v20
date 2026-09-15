import type { Metadata } from 'next';
import { MiOlivoCampaignComparison } from '../../components/mi-olivo-campaign-comparison';
import { MiOlivoCampaignController } from '../../components/mi-olivo-campaign-controller';
import { MiOlivoCareController } from '../../components/mi-olivo-care-controller';
import { MiOlivoProgressionDashboard } from '../../components/mi-olivo-progression-dashboard';
import { MiOlivoSeasonsController } from '../../components/mi-olivo-seasons-controller';
import { MiOlivoTimelineController } from '../../components/mi-olivo-timeline-controller';
import { MiOlivoWeatherController } from '../../components/mi-olivo-weather-controller';
import { MiOlivoWelcomeController } from '../../components/mi-olivo-welcome-controller';

export const metadata: Metadata = {
  title: 'Mi Olivo · Mágina Olivo',
  description: 'Tu olivo digital: progreso, niveles, memoria, cuidados y recompensas reales de Sierra Mágina.',
};

export default function MiOlivoPage() {
  return (
    <MiOlivoCampaignController>
      <MiOlivoWeatherController />
      <MiOlivoWelcomeController />
      <MiOlivoProgressionDashboard />
      <MiOlivoSeasonsController />
      <MiOlivoCampaignComparison />
      <MiOlivoCareController />
      <MiOlivoTimelineController />
    </MiOlivoCampaignController>
  );
}
