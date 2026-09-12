import { fromArrayBuffer } from 'geotiff';
import { inspectRadarGeoTiff, type RadarGeoTiffInspection } from './radar-geotiff-inspection.js';
import type { RadarGrid } from './radar-spatial-analysis.js';

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

export type PreparedAemetRadarGrid = {
  inspection: RadarGeoTiffInspection;
  grid: RadarGrid | null;
};

export async function prepareAemetNationalRadarGrid(bytes: Uint8Array): Promise<PreparedAemetRadarGrid> {
  const inspection = await inspectRadarGeoTiff(bytes);
  if (
    !inspection.analysisReady
    || inspection.scaleSource !== 'aemet-national-reflectivity-palette-v1'
    || inspection.width == null
    || inspection.height == null
    || inspection.bbox == null
  ) {
    return { inspection, grid: null };
  }

  const tiff = await fromArrayBuffer(toArrayBuffer(bytes));
  const image = await tiff.getImage();
  const raster = await image.readRasters({ interleave: true });
  const values = raster as unknown as ArrayLike<number>;

  if (values.length !== inspection.width * inspection.height) {
    return {
      inspection: {
        ...inspection,
        analysisReady: false,
        validationErrors: [...inspection.validationErrors, 'raster_size_mismatch'],
      },
      grid: null,
    };
  }

  return {
    inspection,
    grid: {
      width: inspection.width,
      height: inspection.height,
      bbox: inspection.bbox,
      values,
      indexBands: inspection.paletteIndexBands,
      noCoverageIndexes: inspection.noCoverageIndexes,
      clearIndexes: inspection.clearIndexes,
    },
  };
}
