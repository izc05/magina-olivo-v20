import { PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { fetchAemetNationalRadarAsset } from '@magina/weather';
import type { RadarObjectStoragePort, RadarSourcePort } from './ports.js';

export const remoteAemetRadarSource: RadarSourcePort = {
  fetchNationalReflectivity: () => fetchAemetNationalRadarAsset(),
};

export type RadarS3Config = {
  endpoint: string;
  region: string;
  bucket: string;
  accessKeyId: string;
  secretAccessKey: string;
  forcePathStyle?: boolean;
  prefix?: string;
};

export class S3RadarObjectStorage implements RadarObjectStoragePort {
  private readonly client: S3Client;
  private readonly bucket: string;
  private readonly prefix: string;

  constructor(config: RadarS3Config) {
    this.bucket = config.bucket;
    this.prefix = config.prefix?.replace(/^\/+|\/+$/g, '') || 'weather/radar';
    this.client = new S3Client({
      endpoint: config.endpoint,
      region: config.region,
      forcePathStyle: config.forcePathStyle ?? false,
      credentials: {
        accessKeyId: config.accessKeyId,
        secretAccessKey: config.secretAccessKey,
      },
    });
  }

  async putObject(input: {
    key: string;
    bytes: Uint8Array;
    contentType: string;
    sha256Hex: string;
  }): Promise<{ storageKey: string }> {
    const checksumSha256 = Buffer.from(input.sha256Hex, 'hex').toString('base64');
    const storageKey = [this.prefix, input.key.replace(/^\/+/, '')].filter(Boolean).join('/');
    await this.client.send(new PutObjectCommand({
      Bucket: this.bucket,
      Key: storageKey,
      Body: input.bytes,
      ContentType: input.contentType,
      ChecksumSHA256: checksumSha256,
      Metadata: {
        sha256: input.sha256Hex,
        source: 'aemet-national-radar',
      },
    }));
    return { storageKey };
  }
}

export function createRadarS3StorageFromEnv(): S3RadarObjectStorage | null {
  const endpoint = process.env.S3_ENDPOINT;
  const bucket = process.env.S3_BUCKET;
  const accessKeyId = process.env.S3_ACCESS_KEY_ID;
  const secretAccessKey = process.env.S3_SECRET_ACCESS_KEY;
  if (!endpoint || !bucket || !accessKeyId || !secretAccessKey) return null;

  return new S3RadarObjectStorage({
    endpoint,
    bucket,
    accessKeyId,
    secretAccessKey,
    region: process.env.S3_REGION ?? 'auto',
    forcePathStyle: process.env.S3_FORCE_PATH_STYLE === 'true',
    prefix: process.env.RADAR_S3_PREFIX ?? 'weather/radar',
  });
}
