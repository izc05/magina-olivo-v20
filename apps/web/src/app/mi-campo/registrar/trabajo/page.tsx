import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { WorkEntryClient } from '@/components/work-entry-client';

export default function WorkEntryPage() {
  return <main className="app-shell"><Topbar/><div className="page"><Suspense fallback={null}><WorkEntryClient/></Suspense></div><BottomNav active="/mi-campo"/></main>;
}
