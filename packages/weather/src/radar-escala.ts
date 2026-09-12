export type RadarScaleBand = {
  min: number;
  max: number | null;
  rgba: [number, number, number, number];
};

export type RadarScaleParseResult = {
  valid: boolean;
  bands: RadarScaleBand[];
  error: string | null;
};

const MAX_SCALE_LENGTH = 32_768;
const MAX_BANDS = 128;

const NUMBER_TOKEN = `(?:[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)`;
const VALUE_TOKEN = `(?:['\"]?(${NUMBER_TOKEN})['\"]?|['\"]{2})`;
const COMPONENT_TOKEN = `['\"]?(${NUMBER_TOKEN})['\"]?`;
const ENTRY_RE = new RegExp(
  `[{'\"\\s,]*['\"]Valores['\"]\\s*:\\s*\\[\\s*${VALUE_TOKEN}\\s*,\\s*${VALUE_TOKEN}\\s*\\]\\s*,?\\s*['\"]RGBA['\"]\\s*:\\s*\\[\\s*${COMPONENT_TOKEN}\\s*,\\s*${COMPONENT_TOKEN}\\s*,\\s*${COMPONENT_TOKEN}(?:\\s*,\\s*${COMPONENT_TOKEN})?\\s*\\]`,
  'g',
);

function rgbaComponent(value: string | undefined, fallback = 255) {
  if (value == null) return fallback;
  const number = Number(value);
  if (!Number.isInteger(number) || number < 0 || number > 255) return null;
  return number;
}

export function parseAemetRadarEscala(raw: string | null | undefined): RadarScaleParseResult {
  if (!raw?.trim()) return { valid: false, bands: [], error: 'scale_missing' };
  if (raw.length > MAX_SCALE_LENGTH) return { valid: false, bands: [], error: 'scale_too_large' };
  if (!/Lista\s+RGBA/i.test(raw) || !/Valores/i.test(raw)) {
    return { valid: false, bands: [], error: 'scale_shape_unrecognized' };
  }

  const bands: RadarScaleBand[] = [];
  ENTRY_RE.lastIndex = 0;
  for (let match = ENTRY_RE.exec(raw); match; match = ENTRY_RE.exec(raw)) {
    if (bands.length >= MAX_BANDS) return { valid: false, bands: [], error: 'scale_too_many_bands' };

    const min = Number(match[1]);
    const max = match[2] == null || match[2] === '' ? null : Number(match[2]);
    const r = rgbaComponent(match[3]);
    const g = rgbaComponent(match[4]);
    const b = rgbaComponent(match[5]);
    const a = rgbaComponent(match[6], 255);

    if (!Number.isFinite(min) || (max !== null && !Number.isFinite(max))) {
      return { valid: false, bands: [], error: 'scale_value_invalid' };
    }
    if (max !== null && max < min) {
      return { valid: false, bands: [], error: 'scale_interval_invalid' };
    }
    if (r === null || g === null || b === null || a === null) {
      return { valid: false, bands: [], error: 'scale_rgba_invalid' };
    }

    bands.push({ min, max, rgba: [r, g, b, a] });
  }

  if (bands.length < 2) return { valid: false, bands: [], error: 'scale_bands_missing' };

  const seen = new Set<string>();
  for (const band of bands) {
    const key = band.rgba.join(',');
    if (seen.has(key)) return { valid: false, bands: [], error: 'scale_duplicate_rgba' };
    seen.add(key);
  }

  return { valid: true, bands, error: null };
}
