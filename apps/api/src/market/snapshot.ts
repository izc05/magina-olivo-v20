export type OliveOilMarketCategory = 'virgen-extra' | 'virgen' | 'lampante';

export type OliveOilMarketPoint = {
  week: number;
  periodStart: string;
  periodEnd: string;
  label: string;
  priceEurKg: number;
};

export type OliveOilMarketSeries = {
  id: OliveOilMarketCategory;
  name: string;
  shortName: string;
  description: string;
  unit: 'EUR/kg';
  points: OliveOilMarketPoint[];
  latest: {
    priceEurKg: number;
    previousPriceEurKg: number;
    deltaEurKg: number;
    deltaPercent: number;
  };
};

export type OliveOilMarketSnapshot = {
  schemaVersion: 1;
  revision: string;
  source: {
    name: string;
    url: string;
    marketLevel: string;
    publishedOn: string;
    validatedThrough: string;
  };
  period: {
    week: number;
    start: string;
    end: string;
    label: string;
  };
  note: string;
  series: OliveOilMarketSeries[];
};

const weeks = [
  { week: 29, periodStart: '2026-07-13', periodEnd: '2026-07-19', label: '13–19 jul' },
  { week: 30, periodStart: '2026-07-20', periodEnd: '2026-07-26', label: '20–26 jul' },
  { week: 31, periodStart: '2026-07-27', periodEnd: '2026-08-02', label: '27 jul–2 ago' },
  { week: 32, periodStart: '2026-08-03', periodEnd: '2026-08-09', label: '3–9 ago' },
  { week: 33, periodStart: '2026-08-10', periodEnd: '2026-08-16', label: '10–16 ago' },
  { week: 34, periodStart: '2026-08-17', periodEnd: '2026-08-23', label: '17–23 ago' },
  { week: 35, periodStart: '2026-08-24', periodEnd: '2026-08-30', label: '24–30 ago' },
  { week: 36, periodStart: '2026-08-31', periodEnd: '2026-09-06', label: '31 ago–6 sep' },
] as const;

function points(values: readonly number[]): OliveOilMarketPoint[] {
  return values.map((priceEurKg, index) => ({
    ...weeks[index]!,
    priceEurKg,
  }));
}

function series(
  id: OliveOilMarketCategory,
  name: string,
  shortName: string,
  description: string,
  values: readonly number[],
): OliveOilMarketSeries {
  const marketPoints = points(values);
  const latestPrice = marketPoints.at(-1)?.priceEurKg ?? 0;
  const previousPrice = marketPoints.at(-2)?.priceEurKg ?? latestPrice;
  const delta = latestPrice - previousPrice;

  return {
    id,
    name,
    shortName,
    description,
    unit: 'EUR/kg',
    points: marketPoints,
    latest: {
      priceEurKg: latestPrice,
      previousPriceEurKg: previousPrice,
      deltaEurKg: Number(delta.toFixed(4)),
      deltaPercent: previousPrice > 0 ? Number(((delta / previousPrice) * 100).toFixed(2)) : 0,
    },
  };
}

/**
 * Snapshot reproducible de la publicación semanal del Observatorio de Precios
 * y Mercados de la Junta de Andalucía. No representa una cotización en tiempo
 * real ni la liquidación que recibe un agricultor por su aceituna.
 */
export const oliveOilMarketSnapshot: OliveOilMarketSnapshot = {
  schemaVersion: 1,
  revision: 'junta-andalucia-olive-oil-2026-w36-v1',
  source: {
    name: 'Observatorio de Precios y Mercados · Junta de Andalucía',
    url: 'https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=Static&subsector=33&url=subsector.jsp',
    marketLevel: 'Almazara o bodega · Andalucía',
    publishedOn: '2026-09-09',
    validatedThrough: '2026-09-06',
  },
  period: {
    week: 36,
    start: '2026-08-31',
    end: '2026-09-06',
    label: 'Semana 36 · 31 ago–6 sep 2026',
  },
  note:
    'Precios registrados y validados en almazara o bodega. Son una referencia de mercado del aceite y no equivalen a la liquidación que recibe cada agricultor por su aceituna.',
  series: [
    series('virgen-extra', 'Aceite de oliva virgen extra', 'AOVE', 'Virgen extra en origen.', [3.76, 3.6, 3.7, 3.63, 3.44, 3.49, 3.7, 3.42]),
    series('virgen', 'Aceite de oliva virgen', 'Virgen', 'Aceite virgen en origen.', [3.34, 3.25, 3.24, 3.16, 3.28, 3.24, 3.29, 3.25]),
    series('lampante', 'Aceite de oliva lampante', 'Lampante', 'Lampante de 1 grado en origen.', [3.05, 2.99, 3.0, 3.01, 3.04, 3.09, 3.14, 3.17]),
  ],
};

export const oliveOilMarketEtag = '"junta-andalucia-olive-oil-2026-w36-v1"';
