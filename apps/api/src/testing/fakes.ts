import type { OcrJob, OcrQueuePort } from '../ocr/port.js';
import type { ReserveUploadInput, StoragePort, StoredObjectInfo, UploadReservation } from '../storage/port.js';

export class FakeStorage implements StoragePort {
  readonly reserved = new Map<string, ReserveUploadInput>();

  async reserveUpload(input: ReserveUploadInput): Promise<UploadReservation> {
    const storageKey = `test/${input.workspaceId}/${input.documentId}/${input.versionId}`;
    this.reserved.set(storageKey, input);
    const checksum = Buffer.from(input.sha256, 'hex').toString('base64');
    return {
      storageKey,
      uploadUrl: `https://storage.invalid/${encodeURIComponent(storageKey)}`,
      method: 'PUT',
      headers: {
        'content-type': input.mimeType,
        'x-amz-checksum-sha256': checksum,
      },
      expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    };
  }

  async headObject(storageKey: string): Promise<StoredObjectInfo> {
    const input = this.reserved.get(storageKey);
    if (!input) return { exists: false };
    return {
      exists: true,
      byteSize: input.byteSize,
      mimeType: input.mimeType,
      etag: 'fake-etag',
      checksumSha256: Buffer.from(input.sha256, 'hex').toString('base64'),
    };
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
