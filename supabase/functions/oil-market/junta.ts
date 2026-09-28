// Junta de Andalucía — Observatorio de Precios y Mercados, "Últimos precios" of olive oil at
// "Almazara o Bodega" (Phase 20D, docs/07-plans/PHASE20D-OIL-MARKET-CONTRACT.md).
//
// The page is read here, server side, never in the app. The table is found by its meaning, not by
// column positions: the header row that names the weeks, and the rows labelled LAMPANTE / VIRGEN /
// VIRGEN-EXTRA. A value is read with the Spanish decimal comma; an empty or "--" cell stays
// missing; a row label that is not one of the three categories fails the whole page (never
// guessed), and so does a page where the table cannot be found.

export const JUNTA_SOURCE_ID = "junta-andalucia-observatorio";
export const JUNTA_SOURCE_NAME = "Observatorio de Precios y Mercados - Junta de Andalucía";
export const JUNTA_URL =
  "https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=UltimosPrecios&posicion=2291332&producto=33000&subsector=33";

export type Category = "AOVE" | "AOV" | "AOL";

export interface JuntaWeekValue {
  category: Category;
  isoYear: number;
  isoWeek: number;
  periodStart: string; // Monday, YYYY-MM-DD
  periodEnd: string; // Sunday, YYYY-MM-DD
  /** Exactly as published, with a dot for the decimal comma ("3,46" -> "3.46"). */
  value: string;
}

// Olive-oil prices at the mill are a few €/kg. Anything outside this range means the page is not
// what we think it is (for example €/100 kg), so it is refused rather than shown.
const MIN_EUR_PER_KG = 0.5;
const MAX_EUR_PER_KG = 30;

export function parseJuntaPage(html: string, today: Date): JuntaWeekValue[] {
  // The price table is the (innermost) table whose header row names the weeks.
  const table = innermostTables(html).map(tableRows).find((rows) => rows.some((cells) => weekColumns(cells) !== null));
  if (!table) throw new Error("junta_no_week_header");
  const rows = table;
  const headerIndex = rows.findIndex((cells) => weekColumns(cells) !== null);
  const weeks = weekColumns(rows[headerIndex])!;

  const values: JuntaWeekValue[] = [];
  const seen = new Set<Category>();
  for (const cells of rows.slice(headerIndex + 1)) {
    // Week values are the last cells of the row; anything before them (product, type, subtype
    // with rowspans) is label text.
    if (cells.length <= weeks.length) continue;
    const valueCells = cells.slice(cells.length - weeks.length);
    const labelCells = cells.slice(0, cells.length - weeks.length);
    if (!valueCells.some((cell) => NUMBER.test(cell))) continue; // not a price row
    const label = labelCells[labelCells.length - 1];
    const category = categoryOf(label);
    if (category === null) throw new Error(`junta_unknown_category:${label}`);
    if (seen.has(category)) throw new Error(`junta_duplicate_category:${category}`);
    seen.add(category);
    valueCells.forEach((cell, index) => {
      const value = decimal(cell);
      if (value === null) return; // not published that week
      const number = Number(value);
      if (!(number >= MIN_EUR_PER_KG && number <= MAX_EUR_PER_KG)) throw new Error(`junta_value_out_of_range:${cell}`);
      const week = weeks[index];
      const isoYear = week.year ?? yearOfWeek(week.week, today);
      const start = isoWeekMonday(isoYear, week.week);
      values.push({
        category,
        isoYear,
        isoWeek: week.week,
        periodStart: day(start),
        periodEnd: day(addDays(start, 6)),
        value,
      });
    });
  }
  if (seen.size === 0) throw new Error("junta_no_price_rows");
  return values;
}

/** LAMPANTE (1 g) -> AOL, VIRGEN -> AOV, VIRGEN-EXTRA -> AOVE; anything else is unknown. */
export function categoryOf(label: string | undefined): Category | null {
  const normalized = (label ?? "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toUpperCase()
    .replace(/\s*\(.*\)\s*/g, " ")
    .replace(/[\s_-]+/g, " ")
    .trim();
  switch (normalized) {
    case "VIRGEN EXTRA":
      return "AOVE";
    case "VIRGEN":
      return "AOV";
    case "LAMPANTE":
      return "AOL";
    default:
      return null;
  }
}

const NUMBER = /^\d+(?:[.,]\d+)?$/;

function decimal(cell: string): string | null {
  const text = cell.replace(/\s/g, "");
  if (text === "" || /^-+$/.test(text)) return null;
  if (!NUMBER.test(text)) throw new Error(`junta_bad_value:${cell}`);
  if (text.includes(".") && text.includes(",")) throw new Error(`junta_bad_value:${cell}`);
  return text.replace(",", ".");
}

interface WeekColumn {
  week: number;
  year: number | null;
}

// "Semana 38", "Sem. 38", "38", "38/2026", "Semana 38 (2026)", "2026-38".
const WEEK_CELL = /^(?:sem(?:ana)?\.?\s*)?(\d{1,2})(?:\s*(?:\/|\(|-)\s*(\d{4})\)?)?(?:\s*:\s*.*)?$|^(\d{4})\s*-\s*(\d{1,2})(?:\s*:\s*.*)?$/i;

/** The week columns of a header row (its trailing week cells), or null if it is not one. */
function weekColumns(cells: string[]): WeekColumn[] | null {
  const columns: WeekColumn[] = [];
  for (let index = cells.length - 1; index >= 0; index--) {
    const match = WEEK_CELL.exec(cells[index].trim());
    if (!match) break;
    const week = Number(match[1] ?? match[4]);
    const yearText = match[2] ?? match[3];
    if (week < 1 || week > 53) return null;
    columns.unshift({ week, year: yearText ? Number(yearText) : null });
  }
  // A header names at least two weeks and says "semana" somewhere, so a row of prices that
  // happen to be whole numbers is never taken for it.
  if (columns.length < 2) return null;
  if (!cells.some((cell) => /sem/i.test(cell))) return null;
  return columns;
}

/** Tables that contain no other table (layout tables around them are skipped). */
function innermostTables(html: string): string[] {
  return [...html.matchAll(/<table\b[^>]*>((?:(?!<table\b)[\s\S])*?)<\/table>/gi)].map((match) => match[1]);
}

/** Rows of a table as plain cell text (tags stripped, entities decoded). */
function tableRows(html: string): string[][] {
  const rows: string[][] = [];
  for (const row of html.matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/gi)) {
    const cells = [...row[1].matchAll(/<t[hd]\b[^>]*>([\s\S]*?)<\/t[hd]>/gi)].map((cell) => text(cell[1]));
    if (cells.length > 0) rows.push(cells);
  }
  return rows;
}

function text(fragment: string): string {
  return fragment
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&#(\d+);/g, (_, code) => String.fromCharCode(Number(code)))
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => String.fromCharCode(parseInt(code, 16)))
    .replace(/&([a-z]+);/gi, (entity, name) => ENTITIES[name.toLowerCase()] ?? entity)
    .replace(/\s+/g, " ")
    .trim();
}

const ENTITIES: Record<string, string> = {
  amp: "&", lt: "<", gt: ">", quot: "\"", apos: "'",
  aacute: "á", eacute: "é", iacute: "í", oacute: "ó", uacute: "ú", ntilde: "ñ",
  Aacute: "Á", Eacute: "É", Iacute: "Í", Oacute: "Ó", Uacute: "Ú", Ntilde: "Ñ",
};

/** A week number without a year belongs to this ISO year unless it is still ahead of today. */
function yearOfWeek(week: number, today: Date): number {
  const current = isoWeekOf(today);
  return week <= current.week ? current.year : current.year - 1;
}

function isoWeekOf(date: Date): { year: number; week: number } {
  const utc = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const weekday = utc.getUTCDay() || 7;
  utc.setUTCDate(utc.getUTCDate() + 4 - weekday); // the Thursday of this week
  const year = utc.getUTCFullYear();
  const week = Math.ceil(((utc.getTime() - Date.UTC(year, 0, 1)) / 86_400_000 + 1) / 7);
  return { year, week };
}

export function isoWeekMonday(year: number, week: number): Date {
  const jan4 = new Date(Date.UTC(year, 0, 4));
  const weekday = jan4.getUTCDay() || 7;
  return addDays(jan4, (week - 1) * 7 - (weekday - 1));
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getTime() + days * 86_400_000);
}

function day(date: Date): string {
  return date.toISOString().slice(0, 10);
}
