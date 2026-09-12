export type OliveOilCategory = 'virgen-extra' | 'virgen' | 'lampante';

export type MarketPoint = {
  week: number;
  label: string;
  priceEurKg: number;
};

export type MarketSeries = {
  id: OliveOilCategory;
  name: string;
  shortName: string;
  description: string;
  points: MarketPoint[];
};

export type OliveMarketSnapshot = {
  revision: string;
  sourceName: string;
  sourceUrl: string;
  sourcePublishedOn: string;
  marketLevel: string;
  periodLabel: string;
  validatedThrough: string;
  note: string;
  series: MarketSeries[];
};

const weeks = [
  { week: 29, label: '13–19 jul' },
  { week: 30, label: '20–26 jul' },
  { week: 31, label: '27 jul–2 ago' },
  { week: 32, label: '3–9 ago' },
  { week: 33, label: '10–16 ago' },
  { week: 34, label: '17–23 ago' },
  { week: 35, label: '24–30 ago' },
  { week: 36, label: '31 ago–6 sep' },
] as const;

function toPoints(values: readonly number[]): MarketPoint[] {
  return values.map((priceEurKg, index) => ({
    week: weeks[index]!.week,
    label: weeks[index]!.label,
    priceEurKg,
  }));
}

/**
 * Fallback offline/preview del último snapshot oficial conocido. En ejecución
 * normal la pantalla intenta reemplazarlo por el contrato público de la API.
 */
export const oliveMarketSnapshot: OliveMarketSnapshot = {
  revision: 'junta-andalucia-olive-oil-2026-w36-v1',
  sourceName: 'Observatorio de Precios y Mercados · Junta de Andalucía',
  sourceUrl:
    'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp',
  sourcePublishedOn: '2026-09-09',
  marketLevel: 'Almazara o bodega · Andalucía',
  periodLabel: 'Semana 36 · 31 ago–6 sep 2026',
  validatedThrough: '2026-09-06',
  note:
    'Precios registrados y validados en almazara o bodega. Son una referencia de mercado del aceite y no equivalen a la liquidación que recibe cada agricultor por su aceituna.',
  series: [
    {
      id: 'virgen-extra',
      name: 'Aceite de oliva virgen extra',
      shortName: 'AOVE',
      description: 'Virgen extra en origen.',
      points: toPoints([3.76, 3.6, 3.7, 3.63, 3.44, 3.49, 3.7, 3.42]),
    },
    {
      id: 'virgen',
      name: 'Aceite de oliva virgen',
      shortName: 'Virgen',
      description: 'Aceite virgen en origen.',
      points: toPoints([3.34, 3.25, 3.24, 3.16, 3.28, 3.24, 3.29, 3.25]),
    },
    {
      id: 'lampante',
      name: 'Aceite de oliva lampante',
      shortName: 'Lampante',
      description: 'Lampante de 1 grado en origen.',
      points: toPoints([3.05, 2.99, 3.0, 3.01, 3.04, 3.09, 3.14, 3.17]),
    },
  ],
};

export function latestMarketPrice(series: MarketSeries): number {
  return series.points.at(-1)?.priceEurKg ?? 0;
}

export function previousMarketPrice(series: MarketSeries): number {
  return series.points.at(-2)?.priceEurKg ?? latestMarketPrice(series);
}

export function marketDelta(series: MarketSeries): { absolute: number; percent: number } {
  const latest = latestMarketPrice(series);
  const previous = previousMarketPrice(series);
  const absolute = latest - previous;
  return {
    absolute,
    percent: previous > 0 ? (absolute / previous) * 100 : 0,
  };
}
