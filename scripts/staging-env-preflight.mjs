import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const envPath = resolve(process.argv[2] || 'deploy/staging/.env');
const allowReservedHosts = process.env.STAGING_PREFLIGHT_ALLOW_RESERVED_HOSTS === 'true';

function fail(message) {
  throw new Error(`Staging env preflight failed: ${message}`);
}

function parseEnv(source) {
  const values = new Map();
  for (const [index, rawLine] of source.split(/\r?\n/).entries()) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const separator = line.indexOf('=');
    if (separator < 1) fail(`invalid line ${index + 1}`);
    const key = line.slice(0, separator).trim();
    let value = line.slice(separator + 1).trim();
    if (!/^[A-Z][A-Z0-9_]*$/.test(key)) fail(`invalid variable name ${key} on line ${index + 1}`);
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (values.has(key)) fail(`duplicate variable ${key}`);
    values.set(key, value);
  }
  return values;
}

function valueOf(values, key) {
  const value = values.get(key)?.trim();
  if (!value) fail(`${key} is required`);
  if (/(change[_-]?me|replace[_-]?me)/i.test(value)) fail(`${key} still contains a placeholder`);
  return value;
}

function expectExact(values, key, expected) {
  const value = valueOf(values, key);
  if (value !== expected) fail(`${key} must be ${expected}`);
  return value;
}

function requireHttps(value, key) {
  let url;
  try {
    url = new URL(value);
  } catch {
    fail(`${key} is not a valid URL`);
  }
  if (url.protocol !== 'https:') fail(`${key} must use https`);
  if (['localhost', '127.0.0.1', '0.0.0.0'].includes(url.hostname)) fail(`${key} must not point to localhost`);
  if (!allowReservedHosts && /(^|\.)(example|invalid|test)$/i.test(url.hostname.split('.').at(-1) ?? '')) {
    fail(`${key} must not use a reserved example/test hostname`);
  }
  if (/^example\.(com|org|net)$/i.test(url.hostname)) fail(`${key} still points to an example hostname`);
  return url;
}

function requireInteger(values, key, min, max) {
  const parsed = Number(valueOf(values, key));
  if (!Number.isInteger(parsed) || parsed < min || parsed > max) {
    fail(`${key} must be an integer between ${min} and ${max}`);
  }
  return parsed;
}

const source = await readFile(envPath, 'utf8').catch((error) => {
  throw new Error(`Unable to read staging env file ${envPath}`, { cause: error });
});
const values = parseEnv(source);

expectExact(values, 'NODE_ENV', 'production');
expectExact(values, 'NEXT_PUBLIC_PREVIEW_MODE', 'false');
expectExact(values, 'AUTH_COOKIE_SECURE', 'true');
expectExact(values, 'ALLOW_DEV_AUTH_HEADERS', 'false');
expectExact(values, 'OCR_PROVIDER', 'tesseract');
expectExact(values, 'HOST', '0.0.0.0');
expectExact(values, 'PORT', '3001');

for (const staleKey of ['GOOGLE_CLIENT_SECRET', 'SESSION_SECRET', 'PUBLIC_WEB_ORIGIN', 'OCR_PROCESSOR_MODE']) {
  if (values.has(staleKey)) fail(`${staleKey} is obsolete and must not be present`);
}

const postgresUser = valueOf(values, 'POSTGRES_USER');
const postgresPassword = valueOf(values, 'POSTGRES_PASSWORD');
const postgresDb = valueOf(values, 'POSTGRES_DB');
const databaseUrl = valueOf(values, 'DATABASE_URL');
let parsedDatabaseUrl;
try {
  parsedDatabaseUrl = new URL(databaseUrl);
} catch {
  fail('DATABASE_URL is not a valid PostgreSQL URL');
}
if (!['postgres:', 'postgresql:'].includes(parsedDatabaseUrl.protocol)) fail('DATABASE_URL must use postgres/postgresql');
if (parsedDatabaseUrl.hostname !== 'postgres') fail('DATABASE_URL hostname must be the compose service `postgres`');
if (decodeURIComponent(parsedDatabaseUrl.username) !== postgresUser) fail('DATABASE_URL user must match POSTGRES_USER');
if (decodeURIComponent(parsedDatabaseUrl.password) !== postgresPassword) fail('DATABASE_URL password must match POSTGRES_PASSWORD');
if (decodeURIComponent(parsedDatabaseUrl.pathname.replace(/^\//, '')) !== postgresDb) fail('DATABASE_URL database must match POSTGRES_DB');

requireInteger(values, 'API_PORT', 1, 65535);

const corsOrigins = valueOf(values, 'CORS_ALLOWED_ORIGINS').split(',').map((item) => item.trim()).filter(Boolean);
if (!corsOrigins.length) fail('CORS_ALLOWED_ORIGINS must contain at least one origin');
if (corsOrigins.includes('*')) fail('CORS_ALLOWED_ORIGINS must not contain *');
for (const origin of corsOrigins) requireHttps(origin, 'CORS_ALLOWED_ORIGINS');
requireHttps(valueOf(values, 'NEXT_PUBLIC_API_URL'), 'NEXT_PUBLIC_API_URL');
requireHttps(valueOf(values, 'S3_ENDPOINT'), 'S3_ENDPOINT');

const googleClientId = valueOf(values, 'GOOGLE_CLIENT_ID');
const publicGoogleClientId = valueOf(values, 'NEXT_PUBLIC_GOOGLE_CLIENT_ID');
if (googleClientId !== publicGoogleClientId) {
  fail('GOOGLE_CLIENT_ID and NEXT_PUBLIC_GOOGLE_CLIENT_ID must match for the Google ID-token flow');
}
if (!googleClientId.endsWith('.apps.googleusercontent.com')) {
  fail('GOOGLE_CLIENT_ID must be an OAuth Web Client ID ending in .apps.googleusercontent.com');
}

const bucket = valueOf(values, 'S3_BUCKET');
if (!/staging/i.test(bucket)) fail('S3_BUCKET must clearly identify an isolated staging bucket');
if (/prod(uction)?/i.test(bucket)) fail('S3_BUCKET looks like a production bucket; staging must use an isolated bucket');
valueOf(values, 'S3_REGION');
valueOf(values, 'S3_ACCESS_KEY_ID');
valueOf(values, 'S3_SECRET_ACCESS_KEY');
expectExact(values, 'S3_FORCE_PATH_STYLE', values.get('S3_FORCE_PATH_STYLE') === 'true' ? 'true' : 'false');
valueOf(values, 'S3_PREFIX');
valueOf(values, 'RADAR_S3_PREFIX');
requireInteger(values, 'S3_UPLOAD_TTL_SECONDS', 60, 86_400);
requireInteger(values, 'S3_READ_TTL_SECONDS', 60, 86_400);

const modules = new Set(valueOf(values, 'WORKER_MODULES').split(',').map((item) => item.trim()).filter(Boolean));
for (const required of ['ocr', 'radar', 'notifications']) {
  if (!modules.has(required)) fail(`WORKER_MODULES must include ${required}`);
}

const languages = valueOf(values, 'OCR_TESSERACT_LANGUAGES').split('+');
for (const required of ['spa', 'eng']) {
  if (!languages.includes(required)) fail(`OCR_TESSERACT_LANGUAGES must include ${required}`);
}
requireInteger(values, 'OCR_MAX_BYTES', 1_048_576, 104_857_600);
requireInteger(values, 'OCR_MAX_PDF_PAGES', 1, 100);
requireInteger(values, 'OCR_TESSERACT_TIMEOUT_MS', 5_000, 600_000);
requireInteger(values, 'OCR_TESSERACT_DPI', 100, 400);
requireInteger(values, 'OCR_TESSERACT_PSM', 0, 13);
for (const binary of ['TESSERACT_BIN', 'PDFINFO_BIN', 'PDFTOPPM_BIN']) valueOf(values, binary);

valueOf(values, 'VAPID_PUBLIC_KEY');
valueOf(values, 'VAPID_PRIVATE_KEY');
const vapidSubject = valueOf(values, 'VAPID_SUBJECT');
if (!/^mailto:[^@\s]+@[^@\s]+$/.test(vapidSubject)) fail('VAPID_SUBJECT must be a mailto address');
valueOf(values, 'AEMET_API_KEY');

console.log(`Staging env preflight passed for ${envPath}. Production flags, database, HTTPS/CORS, Google ID-token, isolated storage, OCR and provider settings are coherent.`);
