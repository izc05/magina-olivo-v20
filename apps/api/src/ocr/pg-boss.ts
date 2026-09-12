import { enqueueOcrJob, type JobBoss } from '@magina/jobs';
import type { OcrJob, OcrQueuePort } from './port.js';

export class PgBossOcrQueue implements OcrQueuePort {
  constructor(private readonly boss: JobBoss) {}

  async enqueue(job: OcrJob): Promise<{ jobId: string }> {
    const jobId = await enqueueOcrJob(this.boss, job);
    return { jobId };
  }
}
