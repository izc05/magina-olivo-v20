import type { AttentionItem } from '@/lib/attention-data-source';
import type { FinancialAttentionSummary } from '@/lib/financial-attention-data-source';

export const HOME_PRIORITY_RULE_VERSION = 'home-priority-v1';

export type HomePriorityItem = {
  id: string;
  kind: 'task' | 'settlement' | 'document';
  score: number;
  level: 'critical' | 'high' | 'medium' | 'low';
  title: string;
  subtitle: string;
  detail?: string;
  href: string;
  actionLabel: string;
  fieldId?: string;
  reason: string;
};

function levelForScore(score: number): HomePriorityItem['level'] {
  if (score >= 90) return 'critical';
  if (score >= 75) return 'high';
  if (score >= 55) return 'medium';
  return 'low';
}

function taskScore(item: AttentionItem) {
  if (item.overdue) return { score: 100, reason: 'Tarea atrasada' };
  if (item.advisory?.suitability === 'avoid') return { score: 95, reason: 'Conviene evitar por contexto agronómico' };
  if (item.advisory?.radarElevated) return { score: 88, reason: 'Radar observado elevó la severidad' };
  if (item.advisory?.suitability === 'caution' && !item.advisory.stale) return { score: 82, reason: 'Precaución por contexto agronómico' };
  const when = new Date(item.scheduledAt).getTime();
  const hours = (when - Date.now()) / 3_600_000;
  if (hours <= 24) return { score: 68, reason: 'Tarea próxima en menos de 24 h' };
  return { score: 45, reason: 'Tarea próxima' };
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

export function buildHomePriorities(input: {
  attention: AttentionItem[];
  financial: FinancialAttentionSummary | null;
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
    });
  }

  for (const document of input.financial?.documents ?? []) {
    const ranking = documentScore(document.status);
    const query = new URLSearchParams({ documentId: document.id, fieldId: document.fieldId, source: 'api' });
    items.push({
      id: `document:${document.id}`,
      kind: 'document',
      score: ranking.score,
      level: levelForScore(ranking.score),
      title: document.title,
      subtitle: document.subtitle,
      href: `/mi-campo/documentos/revisar?${query.toString()}`,
      actionLabel: 'Revisar',
      fieldId: document.fieldId,
      reason: ranking.reason,
    });
  }

  for (const settlement of input.financial?.settlements ?? []) {
    const ranking = settlementScore(settlement.pendingEur);
    items.push({
      id: `settlement:${settlement.id}`,
      kind: 'settlement',
      score: ranking.score,
      level: levelForScore(ranking.score),
      title: settlement.title,
      subtitle: `${settlement.subtitle || 'Liquidación'} · pendiente ${settlement.pendingEur.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €`,
      href: '/mi-campo/campana',
      actionLabel: 'Ver campaña',
      reason: ranking.reason,
    });
  }

  return items
    .sort((a, b) => b.score - a.score || a.id.localeCompare(b.id))
    .slice(0, input.limit ?? 4);
}
