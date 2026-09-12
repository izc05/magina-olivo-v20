import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { RegisterHubClient } from '@/components/register-hub-client';

export default function RegisterHubPage() {
  return <main className="app-shell"><Topbar/><div className="page register-hub-page"><RegisterHubClient/></div><BottomNav active="/mi-campo"/></main>;
}
