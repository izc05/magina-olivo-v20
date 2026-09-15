import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { HomeDailyCenter } from '@/components/home-daily-center';
import { ManagedHomeContent } from '@/components/managed-home-content';
import { ManagedAdSlot } from '@/components/managed-ad-slot';

export default function HomePage() {
  return (
    <main className="app-shell">
      <h1
        style={{
          position: 'absolute',
          width: 1,
          height: 1,
          padding: 0,
          margin: -1,
          overflow: 'hidden',
          clip: 'rect(0, 0, 0, 0)',
          whiteSpace: 'nowrap',
          border: 0,
        }}
      >
        Mágina Olivo · Inicio
      </h1>
      <Topbar />
      <div className="page home-page">
        <ManagedAdSlot slot="home_top" />
        <ManagedHomeContent>
          <HomeDailyCenter />
          <ManagedAdSlot slot="home_inline" />
        </ManagedHomeContent>
      </div>
      <BottomNav active="/" />
    </main>
  );
}
