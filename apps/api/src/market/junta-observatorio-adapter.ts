import {
  oliveOilMarketSnapshot,
  type OliveOilMarketCategory,
  type OliveOilMarketPoint,
  type OliveOilMarketSeries,
  type OliveOilMarketSnapshot,
} from './snapshot.js';

export const juntaOliveOilPricesUrl =
  'https://ws128.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=UltimosPrecios&posicion=2291332&producto=33000&subsector=33';
export const juntaLatestPublicationsUrl =
  'https://ws128.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=List&page=1&table=12030';

const categoryOrder: OliveOilMarketCategory[] = ['virgen-extra', 'virgen', 'lampante'];
const categoryLabels: Record<OliveOilMarketCategory, string[]> = {
  'virgen-extra': ['VIRGEN EXTRA'],
  virgen: ['VIRGEN'],
  lampante: ['LAMPANTE 1 G', 'LAMPANTE 1G', 'LAMPANTE'],
};

type ParsedPeriod = {
  week: number;
  periodStart: string;
  periodEnd: string;
  label: string;
};

type FetchLike = (input: string | URL | Request, init?: RequestInit) => Promise<Response>;

export class MarketSourceContractError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'MarketSourceContractError';
  }
}

function decodeEntities(value: string): string {
  return value
    .replace(/&nbsp;|&#160;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&ordm;/gi, 'º')
    .replace(/&aacute;/gi, 'á')
    .replace(/&eacute;/gi, 'é')
    .replace(/&iacute;/gi, 'í')
    .replace(/&oacute;/gi, 'ó')
    .replace(/&uacute;/gi, 'ú')
    .replace(/&ntilde;/gi, 'ñ');
}

function stripHtml(value: string): string {
  return decodeEntities(value.replace(/<br\s*\/?>/gi, ' ').replace(/<[^>]+>/g, ' '))
    .replace(/\s+/g, ' ')
    .trim();
}

function normalizeLabel(value: string): string {
  return stripHtml(value)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function htmlRows(html: string): string[][] {
  const rows: string[][] = [];
  for (const rowMatch of html.matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/gi)) {
    const cells: string[] = [];
    const rowHtml = rowMatch[1] ?? '';
    for (const cellMatch of rowHtml.matchAll(/<(?:th|td)\b[^>]*>([\s\S]*?)<\/(?:th|td)>/gi)) {
      cells.push(stripHtml(cellMatch[1] ?? ''));
    }
    if (cells.length > 0) rows.push(cells);
  }
  if (rows.length === 0) throw new MarketSourceContractError('market_source_table_missing');
  return rows;
}

function isoDate(day: number, month: number, year: number): string {
  const fullYear = year < 100 ? 2000 + year : year;
  const date = new Date(Date.UTC(fullYear, month - 1, day));
  if (
    date.getUTCFullYear() !== fullYear ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    throw new MarketSourceContractError('market_source_invalid_date');
  }
  return date.toISOString().slice(0, 10);
}

function formatPeriodLabel(startIso: string, endIso: string): string {
  const months = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'] as const;
  const start = new Date(`${startIso}T00:00:00Z`);
  const end = new Date(`${endIso}T00:00:00Z`);
  const startDay = start.getUTCDate();
  const endDay = end.getUTCDate();
  const startMonth = months[start.getUTCMonth()] ?? '';
  const endMonth = months[end.getUTCMonth()] ?? '';
  return start.getUTCMonth() === end.getUTCMonth()
    ? `${startDay}–${endDay} ${endMonth}`
    : `${startDay} ${startMonth}–${endDay} ${endMonth}`;
}

function parsePeriodCell(cell: string): ParsedPeriod | null {
  const match = stripHtml(cell).match(
    /Semana\s+(\d{1,2})\s*:?\s*\(\s*(\d{1,2})\/(\d{1,2})\/(\d{2,4})\s*-\s*(\d{1,2})\/(\d{1,2})\/(\d{2,4})\s*\)/i,
  );
  if (!match) return null;

  const week = Number(match[1]);
  const periodStart = isoDate(Number(match[2]), Number(match[3]), Number(match[4]));
  const periodEnd = isoDate(Number(match[5]), Number(match[6]), Number(match[7]));
  if (week < 1 || week > 53) throw new MarketSourceContractError('market_source_invalid_week');

  const startMs = Date.parse(`${periodStart}T00:00:00Z`);
  const endMs = Date.parse(`${periodEnd}T00:00:00Z`);
  if (endMs - startMs !== 6 * 86_400_000) {
    throw new MarketSourceContractError('market_source_period_not_seven_days');
  }

  return { week, periodStart, periodEnd, label: formatPeriodLabel(periodStart, periodEnd) };
}

function parsePeriods(rows: string[][]): ParsedPeriod[] {
  const periods = new Map<string, ParsedPeriod>();
  for (const cell of rows.flat()) {
    const period = parsePeriodCell(cell);
    if (period) periods.set(`${period.periodStart}:${period.week}`, period);
  }

  const ordered = [...periods.values()].sort((left, right) => left.periodStart.localeCompare(right.periodStart));
  if (ordered.length < 2 || ordered.length > 12) {
    throw new MarketSourceContractError(`market_source_period_count_invalid:${ordered.length}`);
  }
  for (let index = 1; index < ordered.length; index += 1) {
    const previous = ordered[index - 1];
    const current = ordered[index];
    if (!previous || !current) continue;
    if (Date.parse(`${current.periodStart}T00:00:00Z`) <= Date.parse(`${previous.periodEnd}T00:00:00Z`)) {
      throw new MarketSourceContractError('market_source_period_order_invalid');
    }
  }
  return ordered;
}

function parsePriceCell(cell: string): number | null {
  const clean = stripHtml(cell).replace(/\s/g, '');
  if (!/^\d{1,2}[,.]\d{1,4}$/.test(clean)) return null;
  const value = Number(clean.replace(',', '.'));
  if (!Number.isFinite(value) || value <= 0 || value > 50) {
    throw new MarketSourceContractError('market_source_price_out_of_range');
  }
  return value;
}

function rowMatchesCategory(row: string[], category: OliveOilMarketCategory): boolean {
  const normalizedCells = row.map(normalizeLabel);
  if (category === 'virgen') return normalizedCells.some((cell) => cell === 'VIRGEN');
  if (category === 'virgen-extra') {
    return normalizedCells.some((cell) => cell === 'VIRGEN EXTRA' || cell === 'VIRGENEXTRA');
  }
  return normalizedCells.some((cell) => categoryLabels.lampante.some((label) => cell.includes(label)));
}

function parseCategoryValues(rows: string[][], category: OliveOilMarketCategory, expectedCount: number): number[] {
  const matchingRows = rows.filter((row) => rowMatchesCategory(row, category));
  if (matchingRows.length !== 1) {
    throw new MarketSourceContractError(`market_source_category_row_invalid:${category}:${matchingRows.length}`);
  }

  const values = matchingRows[0]!.map(parsePriceCell).filter((value): value is number => value !== null);
  if (values.length !== expectedCount) {
    throw new MarketSourceContractError(
      `market_source_price_count_invalid:${category}:${values.length}:${expectedCount}`,
    );
  }
  return values;
}

function buildSeries(category: OliveOilMarketCategory, periods: ParsedPeriod[], values: number[]): OliveOilMarketSeries {
  const metadata = oliveOilMarketSnapshot.series.find((series) => series.id === category);
  if (!metadata) throw new MarketSourceContractError(`market_source_metadata_missing:${category}`);

  const points: OliveOilMarketPoint[] = periods.map((period, index) => ({
    ...period,
    priceEurKg: values[index]!,
  }));
  const latestPrice = points.at(-1)?.priceEurKg ?? 0;
  const previousPrice = points.at(-2)?.priceEurKg ?? latestPrice;
  const delta = latestPrice - previousPrice;

  return {
    id: category,
    name: metadata.name,
    shortName: metadata.shortName,
    description: metadata.description,
    unit: metadata.unit,
    points,
    latest: {
      priceEurKg: latestPrice,
      previousPriceEurKg: previousPrice,
      deltaEurKg: Number(delta.toFixed(4)),
      deltaPercent: previousPrice > 0 ? Number(((delta / previousPrice) * 100).toFixed(2)) : 0,
    },
  };
}

function parsePublicationDate(rows: string[][], week: number): string {
  const titlePattern = `INFORME SEMANAL DE ACEITE SEMANA ${week}`;
  const matchingRows = rows.filter((row) => normalizeLabel(row.join(' ')).includes(titlePattern));
  if (matchingRows.length < 1) {
    throw new MarketSourceContractError(`market_source_publication_missing:${week}`);
  }

  for (const row of matchingRows) {
    for (const cell of row) {
      const match = stripHtml(cell).match(/\b(\d{1,2})[-/](\d{1,2})[-/](\d{4})\b/);
      if (match) return isoDate(Number(match[1]), Number(match[2]), Number(match[3]));
    }
  }
  throw new MarketSourceContractError(`market_source_publication_date_missing:${week}`);
}

export function buildJuntaOliveOilMarketSnapshot(
  pricesHtml: string,
  publicationsHtml: string,
): OliveOilMarketSnapshot {
  const priceRows = htmlRows(pricesHtml);
  const periods = parsePeriods(priceRows);
  const latestPeriod = periods.at(-1);
  if (!latestPeriod) throw new MarketSourceContractError('market_source_latest_period_missing');

  const publicationRows = htmlRows(publicationsHtml);
  const publishedOn = parsePublicationDate(publicationRows, latestPeriod.week);
  const publishedMs = Date.parse(`${publishedOn}T00:00:00Z`);
  const endMs = Date.parse(`${latestPeriod.periodEnd}T00:00:00Z`);
  if (publishedMs < endMs || publishedMs - endMs > 21 * 86_400_000) {
    throw new MarketSourceContractError('market_source_publication_date_inconsistent');
  }

  const series = categoryOrder.map((category) =>
    buildSeries(category, periods, parseCategoryValues(priceRows, category, periods.length)),
  );
  const latestYear = latestPeriod.periodEnd.slice(0, 4);

  return {
    schemaVersion: 1,
    revision: `junta-andalucia-olive-oil-${latestYear}-w${latestPeriod.week}-v1`,
    source: {
      name: oliveOilMarketSnapshot.source.name,
      url: juntaOliveOilPricesUrl,
      marketLevel: oliveOilMarketSnapshot.source.marketLevel,
      publishedOn,
      validatedThrough: latestPeriod.periodEnd,
    },
    period: {
      week: latestPeriod.week,
      start: latestPeriod.periodStart,
      end: latestPeriod.periodEnd,
      label: `Semana ${latestPeriod.week} · ${latestPeriod.label} ${latestYear}`,
    },
    note: oliveOilMarketSnapshot.note,
    series,
  };
}

async function fetchHtml(url: string, fetchImpl: FetchLike): Promise<string> {
  const response = await fetchImpl(url, {
    headers: {
      accept: 'text/html,application/xhtml+xml',
      'user-agent': 'MaginaOlivoV20-MarketMonitor/1.0 (+https://github.com/izc05/magina-olivo-v20)',
    },
    redirect: 'follow',
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw new MarketSourceContractError(`market_source_http_error:${response.status}`);
  const html = await response.text();
  if (html.length < 500) throw new MarketSourceContractError('market_source_response_too_small');
  return html;
}

export async function fetchJuntaOliveOilMarketSnapshot(
  fetchImpl: FetchLike = fetch,
): Promise<OliveOilMarketSnapshot> {
  const [pricesHtml, publicationsHtml] = await Promise.all([
    fetchHtml(juntaOliveOilPricesUrl, fetchImpl),
    fetchHtml(juntaLatestPublicationsUrl, fetchImpl),
  ]);
  return buildJuntaOliveOilMarketSnapshot(pricesHtml, publicationsHtml);
}
