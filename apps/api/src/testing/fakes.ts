import type { OcrJob, OcrQueuePort } from '../ocr/port.js';
import type { ReserveUploadInput, StoragePort, UploadReservation } from '../storage/port.js';

export class FakeStorage implements StoragePort {
  readonly reserved = new Map<string, ReserveUploadInput>();

  async reserveUpload(input: ReserveUploadInput): Promise<UploadReservation> {
    const storageKey = `test/${input.workspaceId}/${input.documentId}/${input.versionId}`;
    this.reserved.set(storageKey, input);
    return {
      storageKey,
      uploadUrl: `https://storage.invalid/${encodeURIComponent(storageKey)}`,
      method: 'PUT',
      headers: { 'content-type': input.mimeType },
      expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    };
  }

  async objectExists(storageKey: string): Promise<boolean> {
    return this.reserved.has(storageKey);
  }

  async createReadUrl(storageKey: string): Promise<string> {
    return `https://storage.invalid/read/${encodeURIComponent(storageKey)}`;
  }
}

export class FakeOcrQueue implements OcrQueuePort {
  readonly jobs: OcrJob[] = [];

  async enqueue(job: OcrJob): Promise<{ jobId: string }> {
    this.jobs.push(job);
    return { jobId: `fake-job-${this.jobs.length}` };
  }
}
