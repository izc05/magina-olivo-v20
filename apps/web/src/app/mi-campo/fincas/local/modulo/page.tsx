import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { LocalFieldModulePage } from '@/components/local-field-module-page';

export default function LocalFarmModulePage() {
  return <main className="app-shell"><Topbar/><div className="page farm-module-page"><LocalFieldModulePage/></div><BottomNav active="/mi-campo"/></main>;
}
