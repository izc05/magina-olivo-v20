import type { RadarIngestJobPayload } from '@magina/contracts';

export interface RadarIngestQueuePort {
  enqueue(payload: RadarIngestJobPayload): Promise<string>;
}

export class UnavailableRadarIngestQueue implements RadarIngestQueuePort {
  async enqueue(): Promise<string> {
    throw new Error('radar_ingest_queue_unavailable');
  }
}
