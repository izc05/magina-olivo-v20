import { GetObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { createHash } from 'node:crypto';
import { execFile } from 'node:child_process';
import { mkdtemp, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { OcrJobPayload } from '@magina/contracts';
import type { OcrProcessorPort, OcrProcessorResult } from './processor.js';

type CommandResult = { stdout: string; stderr: string };

type TesseractCliConfig = {
  endpoint: string;
  region: string;
  bucket: string;
  accessKeyId: string;
  secretAccessKey: string;
  forcePathStyle: boolean;
  languages: string;
  maxBytes: number;
  maxPdfPages: number;
  timeoutMs: number;
  dpi: number;
  pageSegmentationMode: number;
  tesseractBin: string;
  pdfInfoBin: string;
  pdfToPpmBin: string;
};

const DEFAULT_MAX_BYTES = 20 * 1024 * 1024;
const DEFAULT_MAX_PDF_PAGES = 12;
const DEFAULT_TIMEOUT_MS = 120_000;

function positiveInteger(value: string | undefined, fallback: number, min: number, max: number) {
  const parsed = Number(value ?? fallback);
  if (!Number.isInteger(parsed) || parsed < min || parsed > max) return fallback;
  return parsed;
}

function requiredEnv(name: string) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required for the Tesseract OCR processor.`);
  return value;
}

function sourceExtension(mimeType: string) {
  switch (mimeType.toLowerCase()) {
    case 'application/pdf': return '.pdf';
    case 'image/png': return '.png';
    case 'image/jpeg':
    case 'image/jpg': return '.jpg';
    case 'image/tiff': return '.tiff';
    case 'image/bmp': return '.bmp';
    case 'image/webp': return '.webp';
    default: throw new Error(`Unsupported OCR mime type: ${mimeType}`);
  }
}

function runCommand(file: string, args: string[], timeoutMs: number, maxBuffer = 16 * 1024 * 1024): Promise<CommandResult> {
  return new Promise((resolve, reject) => {
    execFile(file, args, {
      encoding: 'utf8',
      timeout: timeoutMs,
      maxBuffer,
      env: { ...process.env, LC_ALL: 'C', LANG: 'C' },
    }, (error, stdout, stderr) => {
      if (error) {
        const detail = typeof stderr === 'string' && stderr.trim() ? `: ${stderr.trim().slice(0, 1000)}` : '';
        reject(new Error(`${file} failed${detail}`, { cause: error }));
        return;
      }
      resolve({ stdout: stdout ?? '', stderr: stderr ?? '' });
    });
  });
}

export class TesseractCliOcrProcessor implements OcrProcessorPort {
  private readonly client: S3Client;

  constructor(private readonly config: TesseractCliConfig) {
    this.client = new S3Client({
      endpoint: config.endpoint,
      region: config.region,
      forcePathStyle: config.forcePathStyle,
      credentials: {
        accessKeyId: config.accessKeyId,
        secretAccessKey: config.secretAccessKey,
      },
    });
  }

  private async readObject(job: OcrJobPayload) {
    const response = await this.client.send(new GetObjectCommand({
      Bucket: this.config.bucket,
      Key: job.storage_key,
    }));

    if (response.ContentLength != null && response.ContentLength > this.config.maxBytes) {
      throw new Error(`OCR input exceeds ${this.config.maxBytes} bytes.`);
    }
    if (!response.Body) throw new Error('OCR storage object has no body.');

    const bytes = Buffer.from(await response.Body.transformToByteArray());
    if (bytes.byteLength > this.config.maxBytes) throw new Error(`OCR input exceeds ${this.config.maxBytes} bytes.`);

    const sha256 = createHash('sha256').update(bytes).digest('hex');
    if (sha256 !== job.expected_sha256_hex.toLowerCase()) {
      throw new Error('Downloaded OCR object SHA-256 does not match the queued document version.');
    }
    return bytes;
  }

  private async pdfImages(sourcePath: string, workDir: string) {
    const info = await runCommand(this.config.pdfInfoBin, [sourcePath], this.config.timeoutMs);
    const pagesMatch = /^Pages:\s+(\d+)$/m.exec(info.stdout);
    if (!pagesMatch) throw new Error('Unable to determine PDF page count before OCR.');
    const pages = Number(pagesMatch[1]);
    if (pages < 1) throw new Error('PDF contains no pages.');
    if (pages > this.config.maxPdfPages) {
      throw new Error(`PDF has ${pages} pages; OCR limit is ${this.config.maxPdfPages}.`);
    }

    const prefix = join(workDir, 'page');
    await runCommand(this.config.pdfToPpmBin, [
      '-png',
      '-r', String(this.config.dpi),
      '-f', '1',
      '-l', String(pages),
      sourcePath,
      prefix,
    ], this.config.timeoutMs, 4 * 1024 * 1024);

    const files = (await readdir(workDir))
      .filter((name) => /^page-\d+\.png$/.test(name))
      .sort((left, right) => left.localeCompare(right, undefined, { numeric: true }))
      .map((name) => join(workDir, name));
    if (files.length !== pages) throw new Error(`PDF conversion produced ${files.length} pages; expected ${pages}.`);
    return files;
  }

  private async version() {
    const result = await runCommand(this.config.tesseractBin, ['--version'], 15_000, 1024 * 1024);
    return result.stdout.split(/\r?\n/, 1)[0]?.trim() || null;
  }

  async process(job: OcrJobPayload): Promise<OcrProcessorResult> {
    if (job.preferred_provider !== 'auto' && job.preferred_provider !== 'tesseract') {
      throw new Error(`OCR job requests ${job.preferred_provider}, but this worker is configured for tesseract.`);
    }

    const extension = sourceExtension(job.mime_type);
    const workDir = await mkdtemp(join(tmpdir(), 'magina-ocr-'));

    try {
      const bytes = await this.readObject(job);
      const sourcePath = join(workDir, `source${extension}`);
      await writeFile(sourcePath, bytes, { flag: 'wx' });

      const inputs = extension === '.pdf'
        ? await this.pdfImages(sourcePath, workDir)
        : [sourcePath];

      const chunks: string[] = [];
      for (const input of inputs) {
        const result = await runCommand(this.config.tesseractBin, [
          input,
          'stdout',
          '-l', this.config.languages,
          '--psm', String(this.config.pageSegmentationMode),
        ], this.config.timeoutMs);
        const text = result.stdout.trim();
        if (text) chunks.push(text);
      }

      const rawText = chunks.join('\n\n').trim();
      if (!rawText) throw new Error('Tesseract produced no readable text.');

      return {
        rawText,
        confidence: null,
        provider: 'tesseract',
        providerVersion: await this.version(),
      };
    } finally {
      await rm(workDir, { recursive: true, force: true });
    }
  }
}

export function createTesseractCliOcrProcessorFromEnv(): TesseractCliOcrProcessor | null {
  const provider = process.env.OCR_PROVIDER?.trim().toLowerCase();
  if (provider !== 'tesseract') return null;

  return new TesseractCliOcrProcessor({
    endpoint: requiredEnv('S3_ENDPOINT'),
    region: process.env.S3_REGION?.trim() || 'auto',
    bucket: requiredEnv('S3_BUCKET'),
    accessKeyId: requiredEnv('S3_ACCESS_KEY_ID'),
    secretAccessKey: requiredEnv('S3_SECRET_ACCESS_KEY'),
    forcePathStyle: process.env.S3_FORCE_PATH_STYLE === 'true',
    languages: process.env.OCR_TESSERACT_LANGUAGES?.trim() || 'spa+eng',
    maxBytes: positiveInteger(process.env.OCR_MAX_BYTES, DEFAULT_MAX_BYTES, 1_048_576, 100 * 1024 * 1024),
    maxPdfPages: positiveInteger(process.env.OCR_MAX_PDF_PAGES, DEFAULT_MAX_PDF_PAGES, 1, 100),
    timeoutMs: positiveInteger(process.env.OCR_TESSERACT_TIMEOUT_MS, DEFAULT_TIMEOUT_MS, 5_000, 600_000),
    dpi: positiveInteger(process.env.OCR_TESSERACT_DPI, 200, 100, 400),
    pageSegmentationMode: positiveInteger(process.env.OCR_TESSERACT_PSM, 3, 0, 13),
    tesseractBin: process.env.TESSERACT_BIN?.trim() || 'tesseract',
    pdfInfoBin: process.env.PDFINFO_BIN?.trim() || 'pdfinfo',
    pdfToPpmBin: process.env.PDFTOPPM_BIN?.trim() || 'pdftoppm',
  });
}
