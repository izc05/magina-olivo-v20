import { createHash } from 'node:crypto';
import { createServer } from 'node:http';

const port = Number(process.env.E2E_S3_PORT ?? 9000);
const allowedOrigin = process.env.E2E_S3_ALLOWED_ORIGIN ?? 'http://127.0.0.1:3000';
const objects = new Map();

function applyCors(request, response) {
  response.setHeader('access-control-allow-origin', request.headers.origin ?? allowedOrigin);
  response.setHeader('access-control-allow-methods', 'PUT, GET, HEAD, OPTIONS');
  response.setHeader('access-control-allow-headers', request.headers['access-control-request-headers'] ?? 'content-type,x-amz-checksum-sha256');
  response.setHeader('access-control-expose-headers', 'etag');
}

function requestUrl(request) {
  return new URL(request.url ?? '/', `http://127.0.0.1:${port}`);
}

function objectKey(request) {
  return decodeURIComponent(requestUrl(request).pathname);
}

function objectMetadata(request) {
  const metadata = {};
  const url = requestUrl(request);

  for (const [name, value] of url.searchParams.entries()) {
    const normalizedName = name.toLowerCase();
    if (normalizedName.startsWith('x-amz-meta-')) {
      metadata[normalizedName.slice('x-amz-meta-'.length)] = value;
    }
  }

  for (const [name, value] of Object.entries(request.headers)) {
    if (!name.startsWith('x-amz-meta-') || typeof value !== 'string') continue;
    metadata[name.slice('x-amz-meta-'.length)] = value;
  }

  return metadata;
}

const server = createServer((request, response) => {
  applyCors(request, response);
  const key = objectKey(request);

  if (key === '/__health') {
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ ok: true, service: 'magina-e2e-s3' }));
    return;
  }

  if (request.method === 'OPTIONS') {
    response.writeHead(204);
    response.end();
    return;
  }

  if (request.method === 'PUT') {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(Buffer.from(chunk)));
    request.on('end', () => {
      const body = Buffer.concat(chunks);
      const checksum = createHash('sha256').update(body).digest('base64');
      const expectedChecksum = request.headers['x-amz-checksum-sha256'];
      if (typeof expectedChecksum === 'string' && expectedChecksum !== checksum) {
        response.writeHead(400, { 'content-type': 'application/xml' });
        response.end('<Error><Code>BadDigest</Code></Error>');
        return;
      }

      const etag = createHash('md5').update(body).digest('hex');
      objects.set(key, {
        body,
        checksum,
        etag,
        metadata: objectMetadata(request),
        contentType: request.headers['content-type'] ?? 'application/octet-stream',
      });
      response.writeHead(200, {
        etag: `"${etag}"`,
        'x-amz-checksum-sha256': checksum,
      });
      response.end();
    });
    return;
  }

  const stored = objects.get(key);
  if (!stored) {
    response.writeHead(404, { 'content-type': 'application/xml' });
    response.end('<Error><Code>NoSuchKey</Code></Error>');
    return;
  }

  const headers = {
    'content-length': String(stored.body.length),
    'content-type': stored.contentType,
    etag: `"${stored.etag}"`,
  };
  for (const [name, value] of Object.entries(stored.metadata)) {
    headers[`x-amz-meta-${name}`] = value;
  }

  if (request.method === 'HEAD') {
    response.writeHead(200, headers);
    response.end();
    return;
  }

  if (request.method === 'GET') {
    response.writeHead(200, { ...headers, 'x-amz-checksum-sha256': stored.checksum });
    response.end(stored.body);
    return;
  }

  response.writeHead(405, { allow: 'PUT, GET, HEAD, OPTIONS' });
  response.end();
});

server.listen(port, '127.0.0.1', () => {
  console.log(`Mágina E2E S3 fixture listening on http://127.0.0.1:${port}`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
