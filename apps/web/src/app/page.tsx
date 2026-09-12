import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { HomeDailyCenter } from '@/components/home-daily-center';
import { ManagedHomeContent } from '@/components/managed-home-content';
import { ManagedAdSlot } from '@/components/managed-ad-slot';

export default function HomePage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page home-page">
        <ManagedAdSlot slot="home_top" />
        <HomeDailyCenter />
        <ManagedAdSlot slot="home_inline" />
        <ManagedHomeContent />
      </div>
      <BottomNav active="/" />
    </main>
  );
}
