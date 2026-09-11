import { createHash } from 'node:crypto';
import { createServer } from 'node:http';

const port = Number(process.env.E2E_S3_PORT ?? 9000);
const allowedOrigin = process.env.E2E_S3_ALLOWED_ORIGIN ?? 'http://127.0.0.1:3000';
const objects = new Map();

function applyCors(request, response) {
  response.setHeader('access-control-allow-origin', request.headers.origin ?? allowedOrigin);
  response.setHeader('access-control-allow-methods', 'PUT, GET, HEAD, OPTIONS');
  response.setHeader('access-control-allow-headers', request.headers['access-control-request-headers'] ?? 'content-type,x-amz-checksum-sha256');
  response.setHeader('access-control-expose-headers', 'etag,x-amz-checksum-sha256');
}

function objectKey(request) {
  const url = new URL(request.url ?? '/', `http://127.0.0.1:${port}`);
  return decodeURIComponent(url.pathname);
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
    'x-amz-checksum-sha256': stored.checksum,
  };

  if (request.method === 'HEAD') {
    response.writeHead(200, headers);
    response.end();
    return;
  }

  if (request.method === 'GET') {
    response.writeHead(200, headers);
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
