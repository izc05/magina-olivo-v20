import { apiFetch } from './api-client';

export type AdminWeatherRefreshResult = {
  source: 'aemet_forecast';
  processed: number;
  fresh: number;
  refreshed: number;
  stale: number;
  failed: number;
  limit: number;
  completed_at: string;
};

export type AdminQueuedSourceOperation = {
  source: 'aemet_radar' | 'notifications';
  job_id: string;
  queued_at: string;
  limit?: number;
};

export const adminSourceOperationsApi = {
  refreshWeather: () => apiFetch<AdminWeatherRefreshResult>('/api/v1/admin/sources/aemet_forecast/refresh', {
    method: 'POST',
  }),
  ingestRadar: () => apiFetch<AdminQueuedSourceOperation>('/api/v1/admin/sources/aemet_radar/ingest', {
    method: 'POST',
  }),
  dispatchNotifications: () => apiFetch<AdminQueuedSourceOperation>('/api/v1/admin/sources/notifications/dispatch', {
    method: 'POST',
  }),
};
