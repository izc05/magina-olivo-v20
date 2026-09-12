import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { MiCampoDashboard } from '@/components/mi-campo-dashboard';

export default function MiCampoPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page mi-campo-page">
        <MiCampoDashboard />
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
