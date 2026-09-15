import { latestMarketPrice, marketDelta, type MarketSeries, type OliveMarketSnapshot } from './market-data';

export type MarketInsight = {
  id: 'aove-week' | 'aove-range' | 'quality-spread';
  label: string;
  value: string;
  detail: string;
  tone: 'up' | 'down' | 'neutral';
};

function formatPrice(value: number): string {
  return new Intl.NumberFormat('es-ES', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

function formatPercent(value: number): string {
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(1).replace('.', ',')}%`;
}

function seriesById(snapshot: OliveMarketSnapshot, id: MarketSeries['id']): MarketSeries | null {
  return snapshot.series.find((series) => series.id === id) ?? null;
}

export function buildMarketInsights(snapshot: OliveMarketSnapshot): MarketInsight[] {
  const aove = seriesById(snapshot, 'virgen-extra');
  const lampante = seriesById(snapshot, 'lampante');
  if (!aove || !lampante || aove.points.length === 0 || lampante.points.length === 0) return [];

  const weekly = marketDelta(aove);
  const aoveLatest = latestMarketPrice(aove);
  const aoveValues = aove.points.map((point) => point.priceEurKg);
  const eightWeekMin = Math.min(...aoveValues);
  const eightWeekMax = Math.max(...aoveValues);
  const lampanteLatest = latestMarketPrice(lampante);
  const spread = aoveLatest - lampanteLatest;

  const weeklyDirection = weekly.absolute > 0.005 ? 'sube' : weekly.absolute < -0.005 ? 'baja' : 'se mantiene';
  const weeklyTone: MarketInsight['tone'] = weekly.absolute > 0.005 ? 'up' : weekly.absolute < -0.005 ? 'down' : 'neutral';
  const atMinimum = Math.abs(aoveLatest - eightWeekMin) < 0.005;
  const atMaximum = Math.abs(aoveLatest - eightWeekMax) < 0.005;

  return [
    {
      id: 'aove-week',
      label: 'AOVE esta semana',
      value: formatPercent(weekly.percent),
      detail: `El AOVE ${weeklyDirection} frente a la semana anterior: ${formatPrice(aoveLatest)} €/kg.`,
      tone: weeklyTone,
    },
    {
      id: 'aove-range',
      label: 'Rango de 8 semanas',
      value: `${formatPrice(eightWeekMin)}–${formatPrice(eightWeekMax)} €/kg`,
      detail: atMinimum
        ? 'El último AOVE coincide con el mínimo de las ocho semanas mostradas.'
        : atMaximum
          ? 'El último AOVE coincide con el máximo de las ocho semanas mostradas.'
          : `El último AOVE está dentro del rango, en ${formatPrice(aoveLatest)} €/kg.`,
      tone: 'neutral',
    },
    {
      id: 'quality-spread',
      label: 'Diferencial AOVE–Lampante',
      value: `${formatPrice(spread)} €/kg`,
      detail: `Diferencia descriptiva entre las dos referencias en la última semana (${formatPrice(aoveLatest)} vs. ${formatPrice(lampanteLatest)} €/kg).`,
      tone: 'neutral',
    },
  ];
}
