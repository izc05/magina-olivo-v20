import type { AttentionItem } from '@/lib/attention-data-source';
import type { FinancialAttentionSummary } from '@/lib/financial-attention-data-source';
import type { ProfessionalAttentionSummary } from '@/lib/professional-attention-data-source';
import type { HomePriorityPreferences } from '@/lib/home-priority-preferences';

export const HOME_PRIORITY_RULE_VERSION = 'home-priority-v4';

export type HomePriorityItem = {
  id: string;
  kind: 'task' | 'settlement' | 'document' | 'professional_invoice' | 'professional_unbilled' | 'professional_quote_expired' | 'professional_quote_followup';
  score: number;
  level: 'critical' | 'high' | 'medium' | 'low';
  title: string;
  subtitle: string;
  detail?: string;
  href: string;
  actionLabel: string;
  fieldId?: string;
  reason: string;
  protected: boolean;
};

function levelForScore(score: number): HomePriorityItem['level'] {
  if (score >= 90) return 'critical';
  if (score >= 75) return 'high';
  if (score >= 55) return 'medium';
  return 'low';
}

function taskScore(item: AttentionItem) {
  if (item.overdue) return { score: 100, reason: 'Tarea atrasada', protected: true };
  if (item.advisory?.suitability === 'avoid') return { score: 95, reason: 'Conviene evitar por contexto agronómico', protected: true };
  if (item.advisory?.radarElevated) return { score: 88, reason: 'Radar observado elevó la severidad', protected: false };
  if (item.advisory?.suitability === 'caution' && !item.advisory.stale) return { score: 82, reason: 'Precaución por contexto agronómico', protected: false };
  const when = new Date(item.scheduledAt).getTime();
  const hours = (when - Date.now()) / 3_600_000;
  if (hours <= 24) return { score: 68, reason: 'Tarea próxima en menos de 24 h', protected: false };
  return { score: 45, reason: 'Tarea próxima', protected: false };
}

function documentScore(status: FinancialAttentionSummary['documents'][number]['status']) {
  if (status === 'ocr_failed') return { score: 84, reason: 'El OCR falló y requiere intervención' };
  if (status === 'needs_review') return { score: 78, reason: 'Lectura pendiente de revisión humana' };
  if (status === 'unstructured') return { score: 72, reason: 'OCR leído sin estructura reconocible' };
  if (status === 'needs_ocr') return { score: 62, reason: 'Documento pendiente de analizar' };
  return { score: 50, reason: 'Documento en procesamiento' };
}

function settlementScore(pendingEur: number) {
  if (pendingEur >= 5000) return { score: 76, reason: 'Cobro pendiente de importe alto' };
  if (pendingEur >= 1000) return { score: 70, reason: 'Cobro pendiente relevante' };
  return { score: 60, reason: 'Liquidación con saldo pendiente' };
}

function overdueInvoiceScore(overdueDays: number, pendingEur: number) {
  if (overdueDays >= 30) return { score: 88, reason: 'Factura vencida hace 30 días o más' };
  if (overdueDays >= 7 || pendingEur >= 2500) return { score: 80, reason: 'Factura vencida relevante' };
  return { score: 74, reason: 'Factura vencida pendiente de cobro' };
}

function unbilledWorkScore(ageDays: number, chargeEur: number) {
  if (ageDays >= 30) return { score: 73, reason: 'Trabajo realizado hace 30 días o más y aún sin facturar' };
  if (ageDays >= 14 || chargeEur >= 2500) return { score: 66, reason: 'Trabajo pendiente de facturar' };
  return { score: 56, reason: 'Trabajo realizado todavía sin factura' };
}

function expiredQuoteScore(overdueDays: number, totalEur: number) {
  if (overdueDays >= 30 || totalEur >= 5000) return { score: 72, reason: 'Presupuesto fuera de plazo que conviene revisar' };
  if (overdueDays >= 7 || totalEur >= 2500) return { score: 64, reason: 'Presupuesto enviado ya fuera de vigencia' };
  return { score: 57, reason: 'Presupuesto fuera de plazo' };
}

function quoteFollowupScore(ageDays: number, totalEur: number) {
  if (ageDays >= 21 || totalEur >= 5000) return { score: 63, reason: 'Presupuesto enviado sin respuesta desde hace tiempo' };
  if (ageDays >= 14 || totalEur >= 2500) return { score: 58, reason: 'Conviene hacer seguimiento del presupuesto' };
  return { score: 52, reason: 'Presupuesto enviado pendiente de respuesta' };
}

function adjustedScore(score: number, kind: HomePriorityItem['kind'], preferences?: HomePriorityPreferences) {
  if (!preferences) return score;
  if ((kind === 'settlement' || kind === 'professional_invoice' || kind === 'professional_unbilled' || kind === 'professional_quote_expired' || kind === 'professional_quote_followup') && preferences.economicWeight === 'reduced') return Math.max(score - 18, 0);
  if (kind === 'document' && preferences.documentWeight === 'reduced') return Math.max(score - 16, 0);
  return score;
}

export function buildHomePriorities(input: {
  attention: AttentionItem[];
  financial: FinancialAttentionSummary | null;
  professional?: ProfessionalAttentionSummary | null;
  preferences?: HomePriorityPreferences;
  limit?: number;
}): HomePriorityItem[] {
  const items: HomePriorityItem[] = [];

  for (const task of input.attention) {
    const ranking = taskScore(task);
    items.push({
      id: `task:${task.id}`,
      kind: 'task',
      score: ranking.score,
      level: levelForScore(ranking.score),
      title: task.title,
      subtitle: `${task.fieldName} · ${task.scheduledAt.slice(0, 16).replace('T', ' ')}`,
      detail: task.advisory?.summary,
      href: '/mi-campo/hoy',
      actionLabel: 'Abrir tarea',
      fieldId: task.fieldId,
      reason: ranking.reason,
      protected: ranking.protected,
    });
  }

  for (const document of input.financial?.documents ?? []) {
    const ranking = documentScore(document.status);
    const score = adjustedScore(ranking.score, 'document', input.preferences);
    const query = new URLSearchParams({ documentId: document.id, fieldId: document.fieldId, source: 'api' });
    items.push({
      id: `document:${document.id}`,
      kind: 'document',
      score,
      level: levelForScore(score),
      title: document.title,
      subtitle: document.subtitle,
      href: `/mi-campo/documentos/revisar?${query.toString()}`,
      actionLabel: 'Revisar',
      fieldId: document.fieldId,
      reason: ranking.reason,
      protected: false,
    });
  }

  for (const settlement of input.financial?.settlements ?? []) {
    const ranking = settlementScore(settlement.pendingEur);
    const score = adjustedScore(ranking.score, 'settlement', input.preferences);
    items.push({
      id: `settlement:${settlement.id}`,
      kind: 'settlement',
      score,
      level: levelForScore(score),
      title: settlement.title,
      subtitle: `${settlement.subtitle || 'Liquidación'} · pendiente ${settlement.pendingEur.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €`,
      href: '/mi-campo/campana',
      actionLabel: 'Ver campaña',
      reason: ranking.reason,
      protected: false,
    });
  }

  for (const invoice of input.professional?.overdueInvoices ?? []) {
    const ranking = overdueInvoiceScore(invoice.overdueDays, invoice.pendingEur);
    const score = adjustedScore(ranking.score, 'professional_invoice', input.preferences);
    items.push({
      id: `professional-invoice:${invoice.id}`,
      kind: 'professional_invoice',
      score,
      level: levelForScore(score),
      title: `Factura ${invoice.invoiceNumber ?? 'sin nº'} vencida`,
      subtitle: `${invoice.customerName} · pendiente ${invoice.pendingEur.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €`,
      detail: `${invoice.overdueDays} días desde el vencimiento`,
      href: `/mi-campo/profesional/cliente?id=${encodeURIComponent(invoice.customerId)}`,
      actionLabel: 'Ver cliente',
      reason: ranking.reason,
      protected: false,
    });
  }

  for (const work of input.professional?.unbilledWorks ?? []) {
    const ranking = unbilledWorkScore(work.ageDays, work.chargeEur);
    const score = adjustedScore(ranking.score, 'professional_unbilled', input.preferences);
    items.push({
      id: `professional-unbilled:${work.id}`,
      kind: 'professional_unbilled',
      score,
      level: levelForScore(score),
      title: work.title,
      subtitle: `${work.customerName} · ${work.chargeEur.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} € sin facturar`,
      detail: `${work.ageDays} días desde el trabajo`,
      href: `/mi-campo/profesional/facturas/nueva?customerId=${encodeURIComponent(work.customerId)}&workId=${encodeURIComponent(work.id)}`,
      actionLabel: 'Facturar',
      reason: ranking.reason,
      protected: false,
    });
  }

  for (const quote of input.professional?.expiredQuotes ?? []) {
    const ranking = expiredQuoteScore(quote.overdueDays, quote.totalEur);
    const score = adjustedScore(ranking.score, 'professional_quote_expired', input.preferences);
    items.push({
      id: `professional-quote-expired:${quote.id}`,
      kind: 'professional_quote_expired',
      score,
      level: levelForScore(score),
      title: `Presupuesto ${quote.quoteNumber ?? 'sin nº'} fuera de plazo`,
      subtitle: `${quote.customerName} · ${quote.totalEur.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €`,
      detail: `${quote.overdueDays} días fuera de vigencia`,
      href: `/mi-campo/profesional/presupuestos?quoteId=${encodeURIComponent(quote.id)}&customerId=${encodeURIComponent(quote.customerId)}`,
      actionLabel: 'Gestionar',
      reason: ranking.reason,
      protected: false,
    });
  }

  for (const quote of input.professional?.quoteFollowups ?? []) {
    const ranking = quoteFollowupScore(quote.ageDays, quote.totalEur);
    const score = adjustedScore(ranking.score, 'professional_quote_followup', input.preferences);
    items.push({
      id: `professional-quote-followup:${quote.id}`,
      kind: 'professional_quote_followup',
      score,
      level: levelForScore(score),
      title: quote.title,
      subtitle: `${quote.customerName} · presupuesto ${quote.totalEur.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €`,
      detail: `${quote.ageDays} días enviado sin decisión`,
      href: `/mi-campo/profesional/presupuestos?quoteId=${encodeURIComponent(quote.id)}&customerId=${encodeURIComponent(quote.customerId)}`,
      actionLabel: 'Revisar',
      reason: ranking.reason,
      protected: false,
    });
  }

  return items
    .filter((item) => item.protected || input.preferences?.showLowPriority !== false || item.level !== 'low')
    .sort((a, b) => b.score - a.score || Number(b.protected) - Number(a.protected) || a.id.localeCompare(b.id))
    .slice(0, input.limit ?? 4);
}
