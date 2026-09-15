import type { RadarIngestJobPayload } from '@magina/contracts';
import { enqueueRadarIngestJob, type JobBoss } from '@magina/jobs';
import type { RadarIngestQueuePort } from './port.js';

export class PgBossRadarIngestQueue implements RadarIngestQueuePort {
  constructor(private readonly boss: JobBoss) {}

  enqueue(payload: RadarIngestJobPayload) {
    return enqueueRadarIngestJob(this.boss, payload);
  }
}
