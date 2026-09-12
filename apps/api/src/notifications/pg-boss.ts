import type { NotificationDispatchJobPayload } from '@magina/contracts';
import { enqueueNotificationDispatchJob, type JobBoss } from '@magina/jobs';
import type { NotificationDispatchQueuePort } from './port.js';

export class PgBossNotificationDispatchQueue implements NotificationDispatchQueuePort {
  constructor(private readonly boss: JobBoss) {}

  enqueue(payload: NotificationDispatchJobPayload) {
    return enqueueNotificationDispatchJob(this.boss, payload);
  }
}
