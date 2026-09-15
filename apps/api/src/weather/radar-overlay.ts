import { deflateSync } from 'node:zlib';
import type { RadarGrid } from '@magina/weather';

const PNG_SIGNATURE = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

function crc32(buffer: Buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type: string, data: Buffer) {
  const typeBuffer = Buffer.from(type, 'ascii');
  const length = Buffer.allocUnsafe(4);
  length.writeUInt32BE(data.length, 0);
  const checksum = Buffer.allocUnsafe(4);
  checksum.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])), 0);
  return Buffer.concat([length, typeBuffer, data, checksum]);
}

function ihdr(width: number, height: number) {
  const data = Buffer.alloc(13);
  data.writeUInt32BE(width, 0);
  data.writeUInt32BE(height, 4);
  data[8] = 8;
  data[9] = 6;
  data[10] = 0;
  data[11] = 0;
  data[12] = 0;
  return data;
}

export function renderRadarOverlayPng(grid: RadarGrid): Buffer {
  const pixels = grid.width * grid.height;
  if (grid.width <= 0 || grid.height <= 0 || grid.values.length !== pixels) {
    throw new Error('invalid_radar_grid');
  }

  const bandByIndex = new Map(grid.indexBands.map((band) => [band.index, band.rgba] as const));
  const transparentIndexes = new Set([...grid.noCoverageIndexes, ...grid.clearIndexes]);
  const stride = grid.width * 4 + 1;
  const raw = Buffer.alloc(stride * grid.height);

  for (let y = 0; y < grid.height; y += 1) {
    const rowOffset = y * stride;
    raw[rowOffset] = 0;
    for (let x = 0; x < grid.width; x += 1) {
      const sourceIndex = y * grid.width + x;
      const paletteIndex = Number(grid.values[sourceIndex]);
      const target = rowOffset + 1 + x * 4;
      if (transparentIndexes.has(paletteIndex)) continue;
      const rgba = bandByIndex.get(paletteIndex);
      if (!rgba) continue;
      raw[target] = rgba[0];
      raw[target + 1] = rgba[1];
      raw[target + 2] = rgba[2];
      raw[target + 3] = 190;
    }
  }

  return Buffer.concat([
    PNG_SIGNATURE,
    chunk('IHDR', ihdr(grid.width, grid.height)),
    chunk('IDAT', deflateSync(raw, { level: 6 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}
