import { readFile } from 'node:fs/promises';
import process from 'node:process';

const rootEnvPath = new URL('../.env.example', import.meta.url);
const stagingEnvPath = new URL('../deploy/staging/.env.example', import.meta.url);

function parseEnv(source, label) {
  const values = new Map();
  const failures = [];

  source.split(/\r?\n/).forEach((rawLine, index) => {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) return;

    const match = rawLine.match(/^([A-Z][A-Z0-9_]*)=(.*)$/);
    if (!match) {
      failures.push(`${label}:${index + 1} no respeta KEY=value`);
      return;
    }

    const [, key, value] = match;
    if (values.has(key)) {
      failures.push(`${label}:${index + 1} duplica ${key}`);
      return;
    }
    values.set(key, value.trim());
  });

  return { values, failures };
}

const rootParsed = parseEnv(await readFile(rootEnvPath, 'utf8'), '.env.example');
const stagingParsed = parseEnv(await readFile(stagingEnvPath, 'utf8'), 'deploy/staging/.env.example');
const root = rootParsed.values;
const staging = stagingParsed.values;
const failures = [...rootParsed.failures, ...stagingParsed.failures];

const required = [
  'NODE_ENV',
  'DATABASE_URL',
  'HOST',
  'PORT',
  'CORS_ALLOWED_ORIGINS',
  'NEXT_PUBLIC_API_URL',
  'NEXT_PUBLIC_PREVIEW_MODE',
  'GOOGLE_CLIENT_ID',
  'NEXT_PUBLIC_GOOGLE_CLIENT_ID',
  'AUTH_COOKIE_SECURE',
  'ALLOW_DEV_AUTH_HEADERS',
  'S3_ENDPOINT',
  'S3_REGION',
  'S3_BUCKET',
  'S3_ACCESS_KEY_ID',
  'S3_SECRET_ACCESS_KEY',
  'S3_FORCE_PATH_STYLE',
  'S3_PREFIX',
  'S3_UPLOAD_TTL_SECONDS',
  'S3_READ_TTL_SECONDS',
  'RADAR_S3_PREFIX',
  'WORKER_MODULES',
  'VAPID_PUBLIC_KEY',
  'VAPID_PRIVATE_KEY',
  'VAPID_SUBJECT',
  'AEMET_API_KEY',
];

for (const key of required) {
  if (!staging.has(key) || staging.get(key) === '') {
    failures.push(`staging debe declarar ${key} con un valor de ejemplo no vacío`);
  }
}

for (const key of staging.keys()) {
  if (!root.has(key)) {
    failures.push(`${key} existe en staging pero no está documentada en .env.example`);
  }
}

const exactSecurityValues = new Map([
  ['NODE_ENV', 'production'],
  ['NEXT_PUBLIC_PREVIEW_MODE', 'false'],
  ['AUTH_COOKIE_SECURE', 'true'],
  ['ALLOW_DEV_AUTH_HEADERS', 'false'],
]);
for (const [key, expected] of exactSecurityValues) {
  if (staging.get(key) !== expected) {
    failures.push(`${key} debe ser ${expected} en staging; valor actual: ${staging.get(key) ?? '(ausente)'}`);
  }
}

function requireHttpsValue(key, value) {
  if (!value || !/^https:\/\/[^/\s]+/i.test(value)) {
    failures.push(`${key} debe usar https:// en staging`);
    return;
  }
  if (/localhost|127\.0\.0\.1|0\.0\.0\.0/i.test(value)) {
    failures.push(`${key} no puede apuntar a una dirección local en staging`);
  }
}

requireHttpsValue('NEXT_PUBLIC_API_URL', staging.get('NEXT_PUBLIC_API_URL'));
requireHttpsValue('S3_ENDPOINT', staging.get('S3_ENDPOINT'));

const corsOrigins = (staging.get('CORS_ALLOWED_ORIGINS') ?? '')
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);
if (!corsOrigins.length) failures.push('CORS_ALLOWED_ORIGINS debe declarar al menos un origen');
for (const origin of corsOrigins) {
  if (origin === '*') {
    failures.push('CORS_ALLOWED_ORIGINS no puede usar * en staging');
    continue;
  }
  requireHttpsValue('CORS_ALLOWED_ORIGINS', origin);
}

if (staging.get('GOOGLE_CLIENT_ID') !== staging.get('NEXT_PUBLIC_GOOGLE_CLIENT_ID')) {
  failures.push('GOOGLE_CLIENT_ID y NEXT_PUBLIC_GOOGLE_CLIENT_ID deben coincidir en el flujo actual de Google ID-token');
}

const workerModules = new Set(
  (staging.get('WORKER_MODULES') ?? '')
    .split(',')
    .map((module) => module.trim())
    .filter(Boolean),
);
if (workerModules.has('ocr')) {
  failures.push('WORKER_MODULES no puede habilitar ocr en NODE_ENV=production mientras no exista un procesador OCR de producción');
}

for (const staleKey of ['GOOGLE_CLIENT_SECRET', 'SESSION_SECRET', 'OCR_PROVIDER', 'OCR_PROCESSOR_MODE']) {
  if (staging.has(staleKey)) failures.push(`${staleKey} no debe declararse en el contrato de staging actual`);
}

const placeholderPattern = /(change[_-]?me|replace[_-]?me)/i;
for (const key of [
  'DATABASE_URL',
  'GOOGLE_CLIENT_ID',
  'NEXT_PUBLIC_GOOGLE_CLIENT_ID',
  'S3_ACCESS_KEY_ID',
  'S3_SECRET_ACCESS_KEY',
  'VAPID_PUBLIC_KEY',
  'VAPID_PRIVATE_KEY',
  'AEMET_API_KEY',
]) {
  const value = staging.get(key) ?? '';
  if (!placeholderPattern.test(value)) {
    failures.push(`${key} debe conservar un placeholder CHANGE_ME/replace-me en el archivo versionado; nunca una credencial real`);
  }
}

const vapidSubject = staging.get('VAPID_SUBJECT') ?? '';
if (!vapidSubject.startsWith('mailto:')) {
  failures.push('VAPID_SUBJECT debe usar formato mailto:');
}
if (!placeholderPattern.test(vapidSubject) && !/@example\.(com|invalid)$/i.test(vapidSubject)) {
  failures.push('VAPID_SUBJECT debe conservar una dirección de ejemplo en el archivo versionado');
}

for (const key of ['PORT', 'S3_UPLOAD_TTL_SECONDS', 'S3_READ_TTL_SECONDS']) {
  const number = Number(staging.get(key));
  if (!Number.isInteger(number) || number <= 0) failures.push(`${key} debe ser un entero positivo`);
}

if (failures.length) {
  console.error('Contrato de staging V20 inválido:');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(
  `Staging contract OK: ${staging.size} variables, production safety defaults enforced, ${corsOrigins.length} HTTPS CORS origin(s), dev auth/preview disabled, sensitive values kept as placeholders.`,
);
