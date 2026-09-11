import { apiFetch } from '@/lib/api-client';
import { getLocalParties, getLocalWorks } from '@/lib/local-prototype-store';

export type ProfessionalWorkView = {
  id: string;
  date: string;
  title: string;
  customerId?: string;
  customerName?: string;
  siteName?: string;
  directCostEur: number;
  chargeEur: number;
  collectedEur: number;
  pendingEur: number;
  accruedMarginEur: number;
  paymentStatus: string;
};

export type ProfessionalCustomerView = {
  id: string;
  name: string;
  workCount: number;
  directCostEur: number;
  chargedEur: number;
  collectedEur: number;
  pendingEur: number;
  accruedMarginEur: number;
};

export type ProfessionalSummaryView = {
  workCount: number;
  customerCount: number;
  directCostEur: number;
  chargedEur: number;
  collectedEur: number;
  pendingEur: number;
  accruedMarginEur: number;
  collectedLessDirectCostsEur: number;
  customers: ProfessionalCustomerView[];
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
    accrued_margin_eur: number;
    collected_less_direct_costs_eur: number;
  };
  customers: Array<{
    customer_id: string;
    customer_name: string;
    work_count: number;
    direct_cost_eur: number;
    charged_eur: number;
    collected_eur: number;
    pending_eur: number;
    accrued_margin_eur: number;
  }>;
  recent_work: Array<{
    id: string;
    occurred_on: string;
    title: string;
    customer_id: string | null;
    customer_name: string | null;
    site_name: string | null;
    direct_cost_eur: number;
    charge_eur: number | null;
    collected_eur: number | null;
    accrued_margin_eur: number;
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
    accruedMarginEur: payload.summary.accrued_margin_eur,
    collectedLessDirectCostsEur: payload.summary.collected_less_direct_costs_eur,
    customers: payload.customers.map((item) => ({
      id: item.customer_id,
      name: item.customer_name,
      workCount: item.work_count,
      directCostEur: item.direct_cost_eur,
      chargedEur: item.charged_eur,
      collectedEur: item.collected_eur,
      pendingEur: item.pending_eur,
      accruedMarginEur: item.accrued_margin_eur,
    })),
    recentWork: payload.recent_work.map((item) => {
      const charge = item.charge_eur ?? 0;
      const collected = item.collected_eur ?? 0;
      return {
        id: item.id,
        date: item.occurred_on,
        title: item.title,
        customerId: item.customer_id ?? undefined,
        customerName: item.customer_name ?? undefined,
        siteName: item.site_name ?? undefined,
        directCostEur: item.direct_cost_eur,
        chargeEur: charge,
        collectedEur: collected,
        pendingEur: Math.max(charge - collected, 0),
        accruedMarginEur: item.accrued_margin_eur,
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
    const customerId = work.commercial?.customerId;
    return {
      id: work.id,
      date: work.occurredOn,
      title: work.title,
      customerId,
      customerName: customerId ? partyById.get(customerId)?.displayName : undefined,
      directCostEur,
      chargeEur,
      collectedEur,
      pendingEur: Math.max(chargeEur - collectedEur, 0),
      accruedMarginEur: chargeEur - directCostEur,
      paymentStatus: work.commercial?.paymentStatus ?? 'pending',
    } satisfies ProfessionalWorkView;
  }).sort((a, b) => b.date.localeCompare(a.date));

  const customerIds = new Set(recentWork.map((work) => work.customerId).filter((value): value is string => Boolean(value)));
  const directCostEur = recentWork.reduce((sum, item) => sum + item.directCostEur, 0);
  const chargedEur = recentWork.reduce((sum, item) => sum + item.chargeEur, 0);
  const collectedEur = recentWork.reduce((sum, item) => sum + item.collectedEur, 0);
  const pendingEur = recentWork.reduce((sum, item) => sum + item.pendingEur, 0);
  const customers = Array.from(customerIds).map((customerId) => {
    const rows = recentWork.filter((work) => work.customerId === customerId);
    const customerDirectCost = rows.reduce((sum, item) => sum + item.directCostEur, 0);
    const customerCharged = rows.reduce((sum, item) => sum + item.chargeEur, 0);
    const customerCollected = rows.reduce((sum, item) => sum + item.collectedEur, 0);
    return {
      id: customerId,
      name: partyById.get(customerId)?.displayName ?? 'Cliente',
      workCount: rows.length,
      directCostEur: customerDirectCost,
      chargedEur: customerCharged,
      collectedEur: customerCollected,
      pendingEur: rows.reduce((sum, item) => sum + item.pendingEur, 0),
      accruedMarginEur: customerCharged - customerDirectCost,
    } satisfies ProfessionalCustomerView;
  }).sort((a, b) => b.pendingEur - a.pendingEur || b.chargedEur - a.chargedEur);

  return {
    workCount: recentWork.length,
    customerCount: customerIds.size,
    directCostEur,
    chargedEur,
    collectedEur,
    pendingEur,
    accruedMarginEur: chargedEur - directCostEur,
    collectedLessDirectCostsEur: collectedEur - directCostEur,
    customers,
    recentWork,
  };
}
