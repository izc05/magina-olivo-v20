import { apiFetch } from '@/lib/api-client';
import { getLocalParties, getLocalWorks } from '@/lib/local-prototype-store';

export type ProfessionalWorkView = {
  id: string;
  date: string;
  title: string;
  customerName?: string;
  siteName?: string;
  directCostEur: number;
  chargeEur: number;
  collectedEur: number;
  pendingEur: number;
  paymentStatus: string;
};

export type ProfessionalSummaryView = {
  workCount: number;
  customerCount: number;
  directCostEur: number;
  chargedEur: number;
  collectedEur: number;
  pendingEur: number;
  marginEur: number;
  cashMarginEur: number;
  recentWork: ProfessionalWorkView[];
};

type ApiPayload = {
  summary: {
    work_count: number;
    customer_count: number;
    direct_cost_eur: number;
    charged_eur: number;
    collected_eur: number;
    pending_eur: number;
    margin_eur: number;
    cash_margin_eur: number;
  };
  recent_work: Array<{
    id: string;
    occurred_on: string;
    title: string;
    customer_name: string | null;
    site_name: string | null;
    direct_cost_eur: number;
    charge_eur: number | null;
    collected_eur: number | null;
    payment_status: string;
  }>;
};

export async function loadApiProfessionalSummary(workspaceId: string): Promise<ProfessionalSummaryView> {
  const payload = await apiFetch<ApiPayload>('/api/v1/professional/summary', { workspaceId });
  return {
    workCount: payload.summary.work_count,
    customerCount: payload.summary.customer_count,
    directCostEur: payload.summary.direct_cost_eur,
    chargedEur: payload.summary.charged_eur,
    collectedEur: payload.summary.collected_eur,
    pendingEur: payload.summary.pending_eur,
    marginEur: payload.summary.margin_eur,
    cashMarginEur: payload.summary.cash_margin_eur,
    recentWork: payload.recent_work.map((item) => {
      const charge = item.charge_eur ?? 0;
      const collected = item.collected_eur ?? 0;
      return {
        id: item.id,
        date: item.occurred_on,
        title: item.title,
        customerName: item.customer_name ?? undefined,
        siteName: item.site_name ?? undefined,
        directCostEur: item.direct_cost_eur,
        chargeEur: charge,
        collectedEur: collected,
        pendingEur: Math.max(charge - collected, 0),
        paymentStatus: item.payment_status,
      };
    }),
  };
}

export function loadPreviewProfessionalSummary(): ProfessionalSummaryView {
  const parties = getLocalParties();
  const partyById = new Map(parties.map((party) => [party.id, party]));
  const works = getLocalWorks().filter((work) => work.performedFor === 'third-party');

  const recentWork = works.map((work) => {
    const participantCost = (work.participants ?? []).reduce((sum, item) => sum + (item.costEur ?? 0), 0);
    const resourceCost = (work.resources ?? []).reduce((sum, item) => sum + (item.costEur ?? 0), 0);
    const directCostEur = work.directCostEur ?? participantCost + resourceCost;
    const chargeEur = work.commercial?.chargeEur ?? 0;
    const collectedEur = work.commercial?.collectedEur ?? 0;
    return {
      id: work.id,
      date: work.occurredOn,
      title: work.title,
      customerName: work.commercial?.customerId ? partyById.get(work.commercial.customerId)?.displayName : undefined,
      directCostEur,
      chargeEur,
      collectedEur,
      pendingEur: Math.max(chargeEur - collectedEur, 0),
      paymentStatus: work.commercial?.paymentStatus ?? 'pending',
    } satisfies ProfessionalWorkView;
  }).sort((a, b) => b.date.localeCompare(a.date));

  const customerIds = new Set(works.map((work) => work.commercial?.customerId).filter((value): value is string => Boolean(value)));
  const directCostEur = recentWork.reduce((sum, item) => sum + item.directCostEur, 0);
  const chargedEur = recentWork.reduce((sum, item) => sum + item.chargeEur, 0);
  const collectedEur = recentWork.reduce((sum, item) => sum + item.collectedEur, 0);
  const pendingEur = recentWork.reduce((sum, item) => sum + item.pendingEur, 0);

  return {
    workCount: recentWork.length,
    customerCount: customerIds.size,
    directCostEur,
    chargedEur,
    collectedEur,
    pendingEur,
    marginEur: chargedEur - directCostEur,
    cashMarginEur: collectedEur - directCostEur,
    recentWork,
  };
}
