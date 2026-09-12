import { Suspense } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { FarmDetailShell } from '@/components/farm-detail-shell';
import { FarmEditShortcut } from '@/components/farm-edit-shortcut';
import { AttentionSummaryCard } from '@/components/attention-summary-card';
import { FinancialAttentionCard } from '@/components/financial-attention-card';
import { FarmPlanShortcut } from '@/components/farm-plan-shortcut';
import { FarmDocumentsPanel } from '@/components/farm-documents-panel';
import { Topbar } from '@/components/topbar';

export default function FarmDetailPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page mi-campo-page">
        <Suspense fallback={<section className="card"><p>Cargando finca…</p></section>}>
          <FarmDetailShell />
          <FarmEditShortcut />
          <FarmPlanShortcut />
          <AttentionSummaryCard inferFieldFromQuery compact />
          <FinancialAttentionCard inferFieldFromQuery compact />
          <FarmDocumentsPanel />
        </Suspense>
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
