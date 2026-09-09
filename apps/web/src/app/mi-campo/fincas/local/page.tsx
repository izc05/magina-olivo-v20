import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { LocalFieldPage } from '@/components/local-field-page';

export default function LocalFarmPage() {
  return <main className="app-shell"><Topbar/><div className="page local-field-page"><LocalFieldPage/></div><BottomNav active="/mi-campo"/></main>;
}
