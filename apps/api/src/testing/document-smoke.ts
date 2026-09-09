import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import { FakeOcrQueue, FakeStorage } from './fakes.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');

const db = createDatabase(databaseUrl);
const storage = new FakeStorage();
const ocrQueue = new FakeOcrQueue();
const app = buildApp({ db, storage, ocrQueue });

const headers = {
  'content-type': 'application/json',
  'x-workspace-id': '11111111-1111-4111-8111-111111111111',
  'x-user-id': '33333333-3333-4333-8333-333333333333',
};

async function main() {
  const payload = {
    client_operation_id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    entity_id: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
    version_id: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    field_id: '55555555-5555-4555-8555-555555555555',
    kind: 'delivery_ticket',
    title: 'Albarán CI',
    original_filename: 'albaran-ci.jpg',
    mime_type: 'image/jpeg',
    byte_size: 12345,
    sha256: 'a'.repeat(64),
  };

  const created = await app.inject({
    method: 'POST',
    url: '/api/v1/documents',
    headers,
    payload,
  });
  if (created.statusCode !== 201) throw new Error(`Document create failed: ${created.statusCode} ${created.body}`);
  const body = created.json();
  if (body.document.id !== payload.entity_id) throw new Error('Unexpected document id');
  if (body.version.id !== payload.version_id) throw new Error('Unexpected version id');
  if (!body.upload?.uploadUrl) throw new Error('Missing upload reservation');

  const replay = await app.inject({ method: 'POST', url: '/api/v1/documents', headers, payload });
  if (replay.statusCode !== 200 || replay.json().replayed !== true) throw new Error(`Document replay failed: ${replay.statusCode} ${replay.body}`);

  const list = await app.inject({
    method: 'GET',
    url: `/api/v1/fields/${payload.field_id}/documents`,
    headers,
  });
  if (list.statusCode !== 200) throw new Error(`Document list failed: ${list.statusCode} ${list.body}`);
  if (list.json().documents.length !== 1) throw new Error(`Expected one field document, got ${list.json().documents.length}`);

  const ocr = await app.inject({
    method: 'POST',
    url: `/api/v1/documents/${payload.entity_id}/ocr`,
    headers,
    payload: { document_version_id: payload.version_id, preferred_provider: 'auto' },
  });
  if (ocr.statusCode !== 202) throw new Error(`OCR enqueue failed: ${ocr.statusCode} ${ocr.body}`);
  if (ocrQueue.jobs.length !== 1) throw new Error(`Expected one OCR job, got ${ocrQueue.jobs.length}`);

  const documentCount = await db.selectFrom('documents').select(({ fn }) => fn.countAll<number>().as('count'))
    .where('id', '=', payload.entity_id).executeTakeFirstOrThrow();
  if (Number(documentCount.count) !== 1) throw new Error('Document idempotency failed');

  const versionCount = await db.selectFrom('document_versions').select(({ fn }) => fn.countAll<number>().as('count'))
    .where('document_id', '=', payload.entity_id).executeTakeFirstOrThrow();
  if (Number(versionCount.count) !== 1) throw new Error('Document version idempotency failed');

  const ocrCount = await db.selectFrom('ocr_runs').select(({ fn }) => fn.countAll<number>().as('count'))
    .where('document_version_id', '=', payload.version_id).executeTakeFirstOrThrow();
  if (Number(ocrCount.count) !== 1) throw new Error('OCR run was not persisted');

  console.log('Document/OCR smoke test passed');
}

try {
  await main();
} finally {
  await app.close();
  await db.destroy();
}
