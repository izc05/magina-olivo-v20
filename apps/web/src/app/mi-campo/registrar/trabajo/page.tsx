import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { WorkEntryClient } from '@/components/work-entry-client';

export default function WorkEntryPage() {
  return <main className="app-shell"><Topbar/><div className="page"><WorkEntryClient/></div><BottomNav active="/mi-campo"/></main>;
}
