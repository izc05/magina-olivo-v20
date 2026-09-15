import type { Metadata } from 'next';
import { AdminCampaignPlanConsole } from '../../../components/admin-campaign-plan-console';

export const metadata: Metadata = {
  title: 'Campañas y planes · Mágina Olivo',
  description: 'Administración auditada de campañas agrícolas, planes e intereses comerciales.',
  robots: { index: false, follow: false },
};

export default function AdminCampaignPlanPage() {
  return <AdminCampaignPlanConsole />;
}
