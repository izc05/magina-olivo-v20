import type { RadarBinaryAsset } from '@magina/weather';

export interface RadarSourcePort {
  fetchNationalReflectivity(): Promise<RadarBinaryAsset>;
}

export interface RadarObjectStoragePort {
  putObject(input: {
    key: string;
    bytes: Uint8Array;
    contentType: string;
    sha256Hex: string;
  }): Promise<void>;
}

export class RadarStorageNotConfiguredError extends Error {
  constructor() {
    super('Radar object storage is not configured.');
    this.name = 'RadarStorageNotConfiguredError';
  }
}
