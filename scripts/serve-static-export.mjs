import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

const args = process.argv.slice(2);
const option = (long, short, fallback) => {
  const index = args.findIndex((arg) => arg === long || arg === short);
  return index >= 0 ? args[index + 1] : fallback;
};
const hostname = option('--hostname', '-H', '127.0.0.1');
const port = Number(option('--port', '-p', '3000'));
const root = new URL('../apps/web/out/', import.meta.url).pathname;
const mime = { '.css': 'text/css', '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.svg': 'image/svg+xml' };

createServer(async (request, response) => {
  const pathname = decodeURIComponent(new URL(request.url ?? '/', `http://${request.headers.host ?? 'localhost'}`).pathname);
  const relative = normalize(pathname).replace(/^(\.\.(\/|\\|$))+/, '').replace(/^[/\\]+/, '');
  let file = join(root, relative);
  try {
    const info = await stat(file);
    if (info.isDirectory()) file = join(file, 'index.html');
    await stat(file);
    response.setHeader('Content-Type', mime[extname(file)] ?? 'application/octet-stream');
    createReadStream(file).pipe(response);
  } catch {
    response.statusCode = 404;
    response.end('Not found');
  }
}).listen(port, hostname, () => console.log(`Static export listening on http://${hostname}:${port}`));
