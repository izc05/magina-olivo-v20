import type { OcrJobPayload, OcrProviderName } from '@magina/contracts';

export type { OcrProviderName };
export type OcrJob = OcrJobPayload;

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
