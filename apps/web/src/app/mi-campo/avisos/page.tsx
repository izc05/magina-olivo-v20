import { BottomNav } from '@/components/bottom-nav';
import { NotificationCenterClient } from '@/components/notification-center-client';
import { Topbar } from '@/components/topbar';

export default function NotificationsPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page mi-campo-page">
        <NotificationCenterClient />
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
