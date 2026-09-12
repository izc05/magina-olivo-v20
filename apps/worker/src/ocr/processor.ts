import type { OcrJobPayload, OcrProviderName } from '@magina/contracts';

export type OcrProcessorResult = {
  rawText: string;
  confidence: number | null;
  provider: OcrProviderName;
  providerVersion?: string | null;
};

export interface OcrProcessorPort {
  process(job: OcrJobPayload): Promise<OcrProcessorResult>;
}

export class OcrProcessorNotConfiguredError extends Error {
  constructor() {
    super('OCR processor is not configured.');
    this.name = 'OcrProcessorNotConfiguredError';
  }
}

export class UnavailableOcrProcessor implements OcrProcessorPort {
  async process(): Promise<OcrProcessorResult> {
    throw new OcrProcessorNotConfiguredError();
  }
}

/** Test-only deterministic OCR processor. Never enable in production. */
export class DeterministicTestOcrProcessor implements OcrProcessorPort {
  async process(job: OcrJobPayload): Promise<OcrProcessorResult> {
    return {
      rawText: `TEST OCR ${job.document_version_id} PESO NETO 1842 KG`,
      confidence: 0.99,
      provider: 'tesseract',
      providerVersion: 'test-double',
    };
  }
}
