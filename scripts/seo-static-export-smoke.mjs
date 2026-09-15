import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import http from 'node:http';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const host = '127.0.0.1';
const port = 43119;
const buildApiUrl = `http://${host}:${port}`;

const server = http.createServer((request, response) => {
  if (request.url !== '/api/v1/public/site-settings') {
    response.writeHead(404).end();
    return;
  }

  response.setHeader('content-type', 'application/json; charset=utf-8');
  response.end(JSON.stringify({
    settings: {
      'site.seo': {
        title: 'Mágina SEO CI',
        description: 'SEO inicial gestionado',
        og_image: 'https://cdn.magina.invalid/seo-ci.jpg',
        robots_index: false,
      },
    },
  }));
});

function listen() {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, host, resolve);
  });
}

function close() {
  return new Promise((resolve) => server.close(resolve));
}

function runBuild() {
  return new Promise((resolve, reject) => {
    const child = spawn('pnpm', ['--filter', '@magina/web', 'build'], {
      cwd: root,
      env: {
        ...process.env,
        MAGINA_BUILD_API_URL: buildApiUrl,
        NEXT_PUBLIC_API_URL: 'https://api-staging.example.test',
        NEXT_PUBLIC_GOOGLE_CLIENT_ID: 'staging-ci.apps.googleusercontent.com',
        NEXT_PUBLIC_PREVIEW_MODE: 'false',
        GITHUB_PAGES: 'false',
      },
      stdio: 'inherit',
    });
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (code === 0) resolve();
      else reject(new Error(`Static export build failed with code=${code ?? 'null'} signal=${signal ?? 'none'}`));
    });
  });
}

await listen();
try {
  await runBuild();
} finally {
  await close();
}

const html = await readFile(new URL('../apps/web/out/index.html', import.meta.url), 'utf8');
assert.match(html, /<title>Mágina SEO CI<\/title>/);
assert.match(html, /<meta name="description" content="SEO inicial gestionado"/);
assert.match(html, /<meta name="robots" content="noindex, nofollow"/);
assert.match(html, /<meta property="og:title" content="Mágina SEO CI"/);
assert.match(html, /<meta property="og:description" content="SEO inicial gestionado"/);
assert.match(html, /<meta property="og:image" content="https:\/\/cdn\.magina\.invalid\/seo-ci\.jpg"/);

console.log('SEO_STATIC_EXPORT_SMOKE_OK');
