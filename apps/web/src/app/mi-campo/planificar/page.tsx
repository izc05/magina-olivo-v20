import { BottomNav } from '@/components/bottom-nav';
import { PlanTaskClient } from '@/components/plan-task-client';
import { Topbar } from '@/components/topbar';

export default function PlanTaskPage() {
  return <main className="app-shell"><Topbar/><div className="page mi-campo-page"><PlanTaskClient/></div><BottomNav active="/mi-campo"/></main>;
}
