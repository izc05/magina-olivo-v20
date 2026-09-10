import type { RadarScaleBand } from './radar-escala.js';

export type Rgb = readonly [number, number, number];

export type RadarPaletteIndexBand = {
  index: number;
  min: number;
  max: number | null;
  rgba: [number, number, number, number];
};

export type AemetPaletteResolution = {
  valid: boolean;
  source: 'aemet-national-reflectivity-palette-v1' | null;
  bands: RadarScaleBand[];
  indexBands: RadarPaletteIndexBand[];
  noCoverageIndexes: number[];
  clearIndexes: number[];
  error: string | null;
};

// AEMET's public radar legend states the reflectivity thresholds in dBZ.
// The current national GeoTIFF distribution is an 8-bit paletted raster whose
// ColorMap contains these stable colors, while palette indexes may be reassigned
// between snapshots. Therefore matching MUST be RGB-based, never index-based.
//
// Thresholds: 12, 18, 24, 30, 36, 42, 48, 54, 60, 66, 72, 150 dBZ.
// Colors below are taken from the live AEMET national GeoTIFF ColorMap and are
// guarded by tests/live probes. Any palette drift fails closed.
export const AEMET_NATIONAL_REFLECTIVITY_BANDS_V1: ReadonlyArray<{
  min: number;
  max: number | null;
  rgb: Rgb;
}> = [
  { min: 12, max: 18, rgb: [0, 0, 252] },
  { min: 18, max: 24, rgb: [0, 148, 252] },
  { min: 24, max: 30, rgb: [0, 252, 252] },
  { min: 30, max: 36, rgb: [67, 131, 35] },
  { min: 36, max: 42, rgb: [0, 192, 0] },
  { min: 42, max: 48, rgb: [0, 255, 0] },
  { min: 48, max: 54, rgb: [255, 255, 0] },
  { min: 54, max: 60, rgb: [255, 187, 0] },
  { min: 60, max: 66, rgb: [255, 127, 0] },
  { min: 66, max: 72, rgb: [255, 0, 0] },
  { min: 72, max: 150, rgb: [200, 0, 90] },
] as const;

// These two colors are stable in the live national product. We deliberately
// keep the semantics conservative: white is outside/no-data coverage and the
// very-light grey is covered with no >=12 dBZ echo. If either disappears from
// a future palette, automatic analysis is disabled until revalidated.
const NO_COVERAGE_RGB: Rgb = [255, 255, 255];
const CLEAR_RGB: Rgb = [239, 242, 249];

function normalizeColorMapValue(value: number) {
  return Math.max(0, Math.min(255, Math.round(value / 257)));
}

function rgbKey(rgb: Rgb | [number, number, number]) {
  return `${rgb[0]},${rgb[1]},${rgb[2]}`;
}

export function decodeTiffColorMap(colorMap: ArrayLike<number>): Array<{ index: number; rgb: [number, number, number] }> {
  const values = Array.from(colorMap, Number);
  if (values.length === 0 || values.length % 3 !== 0) return [];
  const entries = values.length / 3;
  return Array.from({ length: entries }, (_, index) => ({
    index,
    rgb: [
      normalizeColorMapValue(values[index] ?? 0),
      normalizeColorMapValue(values[index + entries] ?? 0),
      normalizeColorMapValue(values[index + entries * 2] ?? 0),
    ],
  }));
}

export function resolveAemetNationalReflectivityPalette(
  colorMap: ArrayLike<number> | null | undefined,
): AemetPaletteResolution {
  if (!colorMap) {
    return {
      valid: false,
      source: null,
      bands: [],
      indexBands: [],
      noCoverageIndexes: [],
      clearIndexes: [],
      error: 'palette_missing',
    };
  }

  const palette = decodeTiffColorMap(colorMap);
  if (palette.length !== 256) {
    return {
      valid: false,
      source: null,
      bands: [],
      indexBands: [],
      noCoverageIndexes: [],
      clearIndexes: [],
      error: 'palette_shape_unrecognized',
    };
  }

  const indexesByColor = new Map<string, number[]>();
  for (const entry of palette) {
    const key = rgbKey(entry.rgb);
    const indexes = indexesByColor.get(key) ?? [];
    indexes.push(entry.index);
    indexesByColor.set(key, indexes);
  }

  const indexBands: RadarPaletteIndexBand[] = [];
  const bands: RadarScaleBand[] = [];

  for (const expected of AEMET_NATIONAL_REFLECTIVITY_BANDS_V1) {
    const indexes = indexesByColor.get(rgbKey(expected.rgb)) ?? [];
    if (indexes.length !== 1) {
      return {
        valid: false,
        source: null,
        bands: [],
        indexBands: [],
        noCoverageIndexes: [],
        clearIndexes: [],
        error: indexes.length === 0 ? 'palette_echo_color_missing' : 'palette_echo_color_ambiguous',
      };
    }

    const rgba: [number, number, number, number] = [expected.rgb[0], expected.rgb[1], expected.rgb[2], 255];
    bands.push({ min: expected.min, max: expected.max, rgba });
    indexBands.push({ index: indexes[0]!, min: expected.min, max: expected.max, rgba });
  }

  const noCoverageIndexes = indexesByColor.get(rgbKey(NO_COVERAGE_RGB)) ?? [];
  const clearIndexes = indexesByColor.get(rgbKey(CLEAR_RGB)) ?? [];
  if (noCoverageIndexes.length !== 1 || clearIndexes.length !== 1) {
    return {
      valid: false,
      source: null,
      bands: [],
      indexBands: [],
      noCoverageIndexes: [],
      clearIndexes: [],
      error: 'palette_background_semantics_unrecognized',
    };
  }

  return {
    valid: true,
    source: 'aemet-national-reflectivity-palette-v1',
    bands,
    indexBands: indexBands.sort((a, b) => a.index - b.index),
    noCoverageIndexes,
    clearIndexes,
    error: null,
  };
}
