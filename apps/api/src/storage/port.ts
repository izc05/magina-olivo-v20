export type UploadReservation = {
  storageKey: string;
  uploadUrl: string;
  method: 'PUT';
  headers: Record<string, string>;
  expiresAt: string;
};

export type ReserveUploadInput = {
  workspaceId: string;
  documentId: string;
  versionId: string;
  originalFilename: string;
  mimeType: string;
  byteSize: number;
  sha256: string;
};

export interface StoragePort {
  reserveUpload(input: ReserveUploadInput): Promise<UploadReservation>;
  objectExists(storageKey: string): Promise<boolean>;
  createReadUrl(storageKey: string, expiresInSeconds?: number): Promise<string>;
}

export class StorageNotConfiguredError extends Error {
  constructor() {
    super('Document storage is not configured.');
    this.name = 'StorageNotConfiguredError';
  }
}

export class UnavailableStorage implements StoragePort {
  async reserveUpload(): Promise<UploadReservation> { throw new StorageNotConfiguredError(); }
  async objectExists(): Promise<boolean> { throw new StorageNotConfiguredError(); }
  async createReadUrl(): Promise<string> { throw new StorageNotConfiguredError(); }
}
