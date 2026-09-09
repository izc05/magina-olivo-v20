import {
  GetObjectCommand,
  HeadObjectCommand,
  PutObjectCommand,
  S3Client,
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import type {
  ReserveUploadInput,
  StoragePort,
  StoredObjectInfo,
  UploadReservation,
} from './port.js';

export type S3StorageConfig = {
  endpoint: string;
  region: string;
  bucket: string;
  accessKeyId: string;
  secretAccessKey: string;
  forcePathStyle?: boolean;
  prefix?: string;
  uploadTtlSeconds?: number;
  readTtlSeconds?: number;
};

function objectKey(prefix: string, input: ReserveUploadInput) {
  return [prefix, input.workspaceId, input.documentId, input.versionId]
    .filter(Boolean)
    .join('/');
}

function isNotFound(error: unknown) {
  if (!error || typeof error !== 'object') return false;
  const candidate = error as { name?: string; $metadata?: { httpStatusCode?: number } };
  return candidate.name === 'NotFound' || candidate.name === 'NoSuchKey' || candidate.$metadata?.httpStatusCode === 404;
}

export class S3CompatibleStorage implements StoragePort {
  private readonly client: S3Client;
  private readonly bucket: string;
  private readonly prefix: string;
  private readonly uploadTtlSeconds: number;
  private readonly readTtlSeconds: number;

  constructor(config: S3StorageConfig) {
    this.bucket = config.bucket;
    this.prefix = config.prefix?.replace(/^\/+|\/+$/g, '') ?? 'private-documents';
    this.uploadTtlSeconds = config.uploadTtlSeconds ?? 15 * 60;
    this.readTtlSeconds = config.readTtlSeconds ?? 10 * 60;
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

  async reserveUpload(input: ReserveUploadInput): Promise<UploadReservation> {
    const storageKey = objectKey(this.prefix, input);
    const checksumSha256 = Buffer.from(input.sha256, 'hex').toString('base64');
    const command = new PutObjectCommand({
      Bucket: this.bucket,
      Key: storageKey,
      ContentType: input.mimeType,
      ChecksumSHA256: checksumSha256,
      Metadata: {
        documentid: input.documentId,
        versionid: input.versionId,
      },
    });
    const uploadUrl = await getSignedUrl(this.client, command, { expiresIn: this.uploadTtlSeconds });
    return {
      storageKey,
      uploadUrl,
      method: 'PUT',
      headers: {
        'content-type': input.mimeType,
        'x-amz-checksum-sha256': checksumSha256,
      },
      expiresAt: new Date(Date.now() + this.uploadTtlSeconds * 1000).toISOString(),
    };
  }

  async headObject(storageKey: string): Promise<StoredObjectInfo> {
    try {
      const response = await this.client.send(new HeadObjectCommand({
        Bucket: this.bucket,
        Key: storageKey,
        ChecksumMode: 'ENABLED',
      }));
      return {
        exists: true,
        byteSize: response.ContentLength,
        mimeType: response.ContentType,
        etag: response.ETag?.replace(/^"|"$/g, ''),
        checksumSha256: response.ChecksumSHA256,
      };
    } catch (error) {
      if (isNotFound(error)) return { exists: false };
      throw error;
    }
  }

  async createReadUrl(storageKey: string, expiresInSeconds = this.readTtlSeconds): Promise<string> {
    return getSignedUrl(
      this.client,
      new GetObjectCommand({ Bucket: this.bucket, Key: storageKey }),
      { expiresIn: expiresInSeconds },
    );
  }
}

export function createS3StorageFromEnv(): S3CompatibleStorage | null {
  const endpoint = process.env.S3_ENDPOINT;
  const bucket = process.env.S3_BUCKET;
  const accessKeyId = process.env.S3_ACCESS_KEY_ID;
  const secretAccessKey = process.env.S3_SECRET_ACCESS_KEY;
  if (!endpoint || !bucket || !accessKeyId || !secretAccessKey) return null;

  return new S3CompatibleStorage({
    endpoint,
    bucket,
    accessKeyId,
    secretAccessKey,
    region: process.env.S3_REGION ?? 'auto',
    forcePathStyle: process.env.S3_FORCE_PATH_STYLE === 'true',
    prefix: process.env.S3_PREFIX ?? 'private-documents',
    uploadTtlSeconds: Number(process.env.S3_UPLOAD_TTL_SECONDS ?? 900),
    readTtlSeconds: Number(process.env.S3_READ_TTL_SECONDS ?? 600),
  });
}
