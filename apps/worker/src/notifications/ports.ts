export type PushSubscriptionDelivery = {
  id: string;
  endpoint: string;
  expirationTime: number | null;
  p256dh: string;
  auth: string;
};

export type PushNotificationMessage = {
  title: string;
  body: string;
  path: string;
  tag: string;
};

export type PushSendResult = {
  ok: boolean;
  statusCode: number | null;
  permanentFailure: boolean;
  errorCode: string | null;
  retryAfterSeconds: number | null;
};

export interface PushSenderPort {
  send(subscription: PushSubscriptionDelivery, message: PushNotificationMessage): Promise<PushSendResult>;
}
