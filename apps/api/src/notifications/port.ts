import type { NotificationDispatchJobPayload } from '@magina/contracts';

export interface NotificationDispatchQueuePort {
  enqueue(payload: NotificationDispatchJobPayload): Promise<string>;
}

export class UnavailableNotificationDispatchQueue implements NotificationDispatchQueuePort {
  async enqueue(): Promise<string> {
    throw new Error('notification_dispatch_queue_unavailable');
  }
}
