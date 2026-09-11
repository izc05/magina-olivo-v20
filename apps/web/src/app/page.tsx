import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { HomeDailyCenter } from '@/components/home-daily-center';
import { ManagedHomeContent } from '@/components/managed-home-content';

export default function HomePage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page home-page">
        <HomeDailyCenter />
        <ManagedHomeContent />
      </div>
      <BottomNav active="/" />
    </main>
  );
}
