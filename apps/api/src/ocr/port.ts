export type OcrProviderName = 'tesseract' | 'paddleocr' | 'doctr';

export type OcrJob = {
  ocrRunId: string;
  documentVersionId: string;
  storageKey: string;
  mimeType: string;
  expectedSha256Hex: string;
  preferredProvider: 'auto' | OcrProviderName;
};

export interface OcrQueuePort {
  enqueue(job: OcrJob): Promise<{ jobId: string }>;
}

export class OcrQueueNotConfiguredError extends Error {
  constructor() {
    super('OCR queue is not configured.');
    this.name = 'OcrQueueNotConfiguredError';
  }
}

export class UnavailableOcrQueue implements OcrQueuePort {
  async enqueue(): Promise<{ jobId: string }> {
    throw new OcrQueueNotConfiguredError();
  }
}
