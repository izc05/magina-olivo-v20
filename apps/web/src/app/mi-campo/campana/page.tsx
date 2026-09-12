import { BottomNav } from '@/components/bottom-nav';
import { CampaignSummaryClient } from '@/components/campaign-summary-client';
import { Topbar } from '@/components/topbar';

export default function CampaignPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page mi-campo-page campaign-page">
        <CampaignSummaryClient />
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
