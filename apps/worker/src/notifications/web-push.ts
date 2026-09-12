import { buildPushPayload } from '@block65/webcrypto-web-push';
import type { PushNotificationMessage, PushSendResult, PushSenderPort, PushSubscriptionDelivery } from './ports.js';

export type VapidConfig = {
  subject: string;
  publicKey: string;
  privateKey: string;
};

function retryAfterSeconds(value: string | null) {
  if (!value) return null;
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric >= 0) return Math.min(Math.round(numeric), 24 * 60 * 60);
  const date = Date.parse(value);
  if (Number.isNaN(date)) return null;
  return Math.max(0, Math.min(Math.ceil((date - Date.now()) / 1000), 24 * 60 * 60));
}

export class WebCryptoPushSender implements PushSenderPort {
  constructor(private readonly vapid: VapidConfig) {}

  async send(subscription: PushSubscriptionDelivery, message: PushNotificationMessage): Promise<PushSendResult> {
    const request = await buildPushPayload(
      {
        data: JSON.stringify(message),
        options: { ttl: 5 * 60 },
      },
      {
        endpoint: subscription.endpoint,
        expirationTime: subscription.expirationTime,
        keys: { p256dh: subscription.p256dh, auth: subscription.auth },
      },
      this.vapid,
    );

    const response = await fetch(subscription.endpoint, {
      ...request,
      redirect: 'error',
      signal: AbortSignal.timeout(10_000),
    });

    if (response.ok) {
      return { ok: true, statusCode: response.status, permanentFailure: false, errorCode: null, retryAfterSeconds: null };
    }

    const permanentFailure = response.status === 404 || response.status === 410 || (response.status >= 400 && response.status < 500 && response.status !== 429);
    return {
      ok: false,
      statusCode: response.status,
      permanentFailure,
      errorCode: response.status === 404 || response.status === 410 ? 'subscription_gone' : `push_http_${response.status}`,
      retryAfterSeconds: retryAfterSeconds(response.headers.get('retry-after')),
    };
  }
}

export function createPushSenderFromEnv() {
  const subject = process.env.VAPID_SUBJECT?.trim();
  const publicKey = process.env.VAPID_PUBLIC_KEY?.trim();
  const privateKey = process.env.VAPID_PRIVATE_KEY?.trim();
  if (!subject || !publicKey || !privateKey) return null;
  if (!(subject.startsWith('mailto:') || subject.startsWith('https://'))) {
    throw new Error('VAPID_SUBJECT must be a mailto: or https:// URI');
  }
  return new WebCryptoPushSender({ subject, publicKey, privateKey });
}
