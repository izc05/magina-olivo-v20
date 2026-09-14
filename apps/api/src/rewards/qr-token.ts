import { createHash, createHmac, timingSafeEqual } from 'node:crypto';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const TOKEN_PREFIX = 'reward-v1';
const DEVELOPMENT_SECRET = 'magina-olivo-development-reward-qr-secret';

function signingSecret() {
  const configured = process.env.REWARD_QR_SECRET?.trim();
  if (configured) return configured;
  if (process.env.NODE_ENV === 'production') return null;
  return DEVELOPMENT_SECRET;
}

function signatureFor(code: string, secret: string) {
  return createHmac('sha256', secret)
    .update(`${TOKEN_PREFIX}:${code}`)
    .digest('base64url');
}

function tokenHash(token: string) {
  return createHash('sha256').update(token).digest('hex');
}

export type RewardQrToken = {
  token: string;
  hash: string;
  code: string;
};

export function createRewardQrToken(code: string): RewardQrToken | null {
  if (!UUID_PATTERN.test(code)) return null;
  const secret = signingSecret();
  if (!secret) return null;
  const signature = signatureFor(code, secret);
  const token = `${code}.${signature}`;
  return { token, hash: tokenHash(token), code };
}

export function verifyRewardQrToken(token: string): RewardQrToken | null {
  const separator = token.indexOf('.');
  if (separator <= 0 || separator !== token.lastIndexOf('.')) return null;

  const code = token.slice(0, separator);
  const provided = token.slice(separator + 1);
  if (!UUID_PATTERN.test(code) || provided.length < 32 || provided.length > 128) return null;

  const secret = signingSecret();
  if (!secret) return null;
  const expected = signatureFor(code, secret);
  const providedBuffer = Buffer.from(provided);
  const expectedBuffer = Buffer.from(expected);
  if (providedBuffer.length !== expectedBuffer.length) return null;
  if (!timingSafeEqual(providedBuffer, expectedBuffer)) return null;

  return { token, hash: tokenHash(token), code };
}

export function rewardQrConfigured() {
  return Boolean(signingSecret());
}
