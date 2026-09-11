import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ExplorePublicClient } from './explore-public-client';

export default function ExplorePage() {
  return <main className="app-shell"><Topbar/><div className="page explore-page">
    <ExplorePublicClient />
  </div><BottomNav active="/explorar"/></main>;
}
