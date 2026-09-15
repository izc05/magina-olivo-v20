import { BottomNav } from '@/components/bottom-nav';
import { TodayAgendaClient } from '@/components/today-agenda-client';
import { Topbar } from '@/components/topbar';

export default function TodayPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page mi-campo-page today-page">
        <TodayAgendaClient />
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
