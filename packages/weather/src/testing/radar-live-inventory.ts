import { Readable } from 'node:stream';
import { createGunzip } from 'node:zlib';
import * as tar from 'tar-stream';

const url = 'https://www.aemet.es/es/api-eltiempo/radar/download/compo';
const maxCompressedBytes = 50 * 1024 * 1024;
const maxEntries = 256;

const response = await fetch(url, {
  headers: {
    accept: 'application/tar+gzip, application/gzip, application/octet-stream',
    'user-agent': 'Magina-Olivo/20 (+one-shot-radar-inventory)',
  },
  signal: AbortSignal.timeout(15_000),
});
if (!response.ok) throw new Error(`AEMET_RADAR_INVENTORY_HTTP_${response.status}`);

const declared = Number(response.headers.get('content-length'));
if (Number.isFinite(declared) && declared > maxCompressedBytes) {
  throw new Error('AEMET_RADAR_INVENTORY_BUNDLE_TOO_LARGE');
}

const compressed = new Uint8Array(await response.arrayBuffer());
if (compressed.byteLength > maxCompressedBytes) throw new Error('AEMET_RADAR_INVENTORY_BUNDLE_TOO_LARGE');

const inventory = await new Promise<Array<{ name: string; size: number; type: string }>>((resolve, reject) => {
  const extractor = tar.extract();
  const gunzip = createGunzip();
  const entries: Array<{ name: string; size: number; type: string }> = [];
  let total = 0;
  let settled = false;

  const fail = (error: Error) => {
    if (settled) return;
    settled = true;
    gunzip.destroy();
    extractor.destroy();
    reject(error);
  };

  extractor.on('entry', (header, stream, next) => {
    total += 1;
    if (total > maxEntries) {
      stream.resume();
      stream.on('end', () => fail(new Error('AEMET_RADAR_INVENTORY_TOO_MANY_ENTRIES')));
      return;
    }
    entries.push({ name: header.name, size: Number(header.size ?? 0), type: String(header.type ?? '') });
    stream.resume();
    stream.on('end', next);
    stream.on('error', (error) => fail(error));
  });
  extractor.on('error', (error) => fail(error));
  gunzip.on('error', (error) => fail(error));
  extractor.on('finish', () => {
    if (settled) return;
    settled = true;
    resolve(entries);
  });

  Readable.from([Buffer.from(compressed)]).pipe(gunzip).pipe(extractor);
});

console.log('AEMET_RADAR_LIVE_INVENTORY');
console.log(JSON.stringify({
  fetchedAt: new Date().toISOString(),
  contentType: response.headers.get('content-type'),
  compressedBytes: compressed.byteLength,
  entryCount: inventory.length,
  entries: inventory,
}, null, 2));
