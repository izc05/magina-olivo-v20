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

const deploymentOnlyKeys = new Set(['POSTGRES_USER', 'POSTGRES_PASSWORD', 'POSTGRES_DB', 'WEB_PORT', 'API_PORT']);
const required = [
  'NODE_ENV',
  'POSTGRES_USER',
  'POSTGRES_PASSWORD',
  'POSTGRES_DB',
  'DATABASE_URL',
  'WEB_PORT',
  'API_PORT',
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
  'OCR_PROVIDER',
  'OCR_TESSERACT_LANGUAGES',
  'OCR_MAX_BYTES',
  'OCR_MAX_PDF_PAGES',
  'OCR_TESSERACT_TIMEOUT_MS',
  'OCR_TESSERACT_DPI',
  'OCR_TESSERACT_PSM',
  'TESSERACT_BIN',
  'PDFINFO_BIN',
  'PDFTOPPM_BIN',
  'VAPID_PUBLIC_KEY',
  'VAPID_PRIVATE_KEY',
  'VAPID_SUBJECT',
  'AEMET_API_KEY',
];

for (const key of required) {
  if (!staging.has(key) || staging.get(key) === '') failures.push(`staging debe declarar ${key} con un valor de ejemplo no vacío`);
}

for (const key of staging.keys()) {
  if (!root.has(key) && !deploymentOnlyKeys.has(key)) {
    failures.push(`${key} existe en staging pero no está documentada en .env.example ni declarada como variable exclusiva de despliegue`);
  }
}

const exactValues = new Map([
  ['NODE_ENV', 'production'],
  ['POSTGRES_USER', 'magina_staging'],
  ['POSTGRES_DB', 'magina_staging'],
  ['WEB_PORT', '8080'],
  ['API_PORT', '3001'],
  ['HOST', '0.0.0.0'],
  ['PORT', '3001'],
  ['NEXT_PUBLIC_PREVIEW_MODE', 'false'],
  ['AUTH_COOKIE_SECURE', 'true'],
  ['ALLOW_DEV_AUTH_HEADERS', 'false'],
  ['WORKER_MODULES', 'ocr,radar,notifications'],
  ['OCR_PROVIDER', 'tesseract'],
  ['TESSERACT_BIN', 'tesseract'],
  ['PDFINFO_BIN', 'pdfinfo'],
  ['PDFTOPPM_BIN', 'pdftoppm'],
]);
for (const [key, expected] of exactValues) {
  if (staging.get(key) !== expected) failures.push(`${key} debe ser ${expected} en staging; valor actual: ${staging.get(key) ?? '(ausente)'}`);
}

const expectedDatabaseUrl = `postgresql://${staging.get('POSTGRES_USER')}:${staging.get('POSTGRES_PASSWORD')}@postgres:5432/${staging.get('POSTGRES_DB')}`;
if (staging.get('DATABASE_URL') !== expectedDatabaseUrl) {
  failures.push('DATABASE_URL debe usar exactamente POSTGRES_USER/POSTGRES_PASSWORD/POSTGRES_DB y el host interno postgres:5432');
}

function requireHttpsValue(key, value) {
  if (!value || !/^https:\/\/[^/\s]+/i.test(value)) {
    failures.push(`${key} debe usar https:// en staging`);
    return;
  }
  if (/localhost|127\.0\.0\.1|0\.0\.0\.0/i.test(value)) failures.push(`${key} no puede apuntar a una dirección local en staging`);
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
  (staging.get('WORKER_MODULES') ?? '').split(',').map((module) => module.trim()).filter(Boolean),
);
for (const requiredModule of ['ocr', 'radar', 'notifications']) {
  if (!workerModules.has(requiredModule)) failures.push(`WORKER_MODULES debe habilitar ${requiredModule} en staging`);
}
for (const module of workerModules) {
  if (!['ocr', 'radar', 'notifications'].includes(module)) failures.push(`WORKER_MODULES contiene un módulo no soportado: ${module}`);
}

for (const staleKey of ['GOOGLE_CLIENT_SECRET', 'SESSION_SECRET', 'PUBLIC_WEB_ORIGIN', 'OCR_PROCESSOR_MODE']) {
  if (staging.has(staleKey)) failures.push(`${staleKey} no debe declararse en el contrato de staging actual`);
}

const languages = staging.get('OCR_TESSERACT_LANGUAGES') ?? '';
if (!/^[A-Za-z0-9_+.-]+$/.test(languages) || !languages.split('+').includes('spa')) {
  failures.push('OCR_TESSERACT_LANGUAGES debe usar una lista Tesseract válida e incluir spa');
}

function requireIntegerRange(key, minimum, maximum = Number.MAX_SAFE_INTEGER) {
  const value = Number(staging.get(key));
  if (!Number.isInteger(value) || value < minimum || value > maximum) failures.push(`${key} debe ser un entero entre ${minimum} y ${maximum}`);
}

requireIntegerRange('WEB_PORT', 1, 65535);
requireIntegerRange('API_PORT', 1, 65535);
requireIntegerRange('PORT', 1, 65535);
if (staging.get('WEB_PORT') === staging.get('API_PORT')) failures.push('WEB_PORT y API_PORT deben ser distintos en staging');
requireIntegerRange('S3_UPLOAD_TTL_SECONDS', 1);
requireIntegerRange('S3_READ_TTL_SECONDS', 1);
requireIntegerRange('OCR_MAX_BYTES', 1048576, 104857600);
requireIntegerRange('OCR_MAX_PDF_PAGES', 1, 100);
requireIntegerRange('OCR_TESSERACT_TIMEOUT_MS', 5000, 600000);
requireIntegerRange('OCR_TESSERACT_DPI', 100, 400);
requireIntegerRange('OCR_TESSERACT_PSM', 0, 13);

const placeholderPattern = /(change[_-]?me|replace[_-]?me)/i;
for (const key of [
  'POSTGRES_PASSWORD',
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
  if (!placeholderPattern.test(value)) failures.push(`${key} debe conservar un placeholder CHANGE_ME/replace-me en el archivo versionado; nunca una credencial real`);
}

const vapidSubject = staging.get('VAPID_SUBJECT') ?? '';
if (!vapidSubject.startsWith('mailto:')) failures.push('VAPID_SUBJECT debe usar formato mailto:');
if (!placeholderPattern.test(vapidSubject) && !/@example\.(com|invalid)$/i.test(vapidSubject)) {
  failures.push('VAPID_SUBJECT debe conservar una dirección de ejemplo en el archivo versionado');
}

if (failures.length) {
  console.error('Contrato de staging V20 inválido:');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(
  `Staging contract OK: ${staging.size} variables (${deploymentOnlyKeys.size} deploy-only), production safety defaults enforced, ${corsOrigins.length} HTTPS CORS origin(s), dev auth/preview disabled, Tesseract OCR enabled with bounded runtime limits, Compose database credentials aligned, sensitive values kept as placeholders.`,
);
