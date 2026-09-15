import { BottomNav } from '@/components/bottom-nav';
import { ProfessionalDashboard } from '@/components/professional-dashboard';
import { Topbar } from '@/components/topbar';

export default function ProfessionalPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page mi-campo-page professional-page">
        <ProfessionalDashboard />
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
