import { BottomNav } from '@/components/bottom-nav';
import { ManagedAdSlot } from '@/components/managed-ad-slot';
import { ManagedExploreContent } from '@/components/managed-explore-content';
import { Topbar } from '@/components/topbar';
import { ExplorePublicClient } from './explore-public-client';

export default function ExplorePage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page explore-page">
        <ManagedAdSlot slot="explore_top" />
        <ExplorePublicClient />
        <ManagedExploreContent />
        <ManagedAdSlot slot="explore_inline" />
      </div>
      <BottomNav active="/explorar" />
    </main>
  );
}
