import { apiFetch, apiFetchBlob } from './api-client';

export type AdminAnalyticsPoint = {
  day: string;
  users_new: number;
  workspaces_new: number;
  works: number;
  harvest_kg: number;
  expenses_eur: number;
  invoiced_eur: number;
  admin_actions: number;
};

export type AdminAnalyticsSnapshot = {
  days: 30 | 90;
  generated_at: string;
  summary: Omit<AdminAnalyticsPoint, 'day'>;
  points: AdminAnalyticsPoint[];
};

export const adminAnalyticsApi = {
  timeseries: (days: 30 | 90) => apiFetch<AdminAnalyticsSnapshot>(`/api/v1/admin/analytics/timeseries?days=${days}`),
  exportCsv: (days: 30 | 90) => apiFetchBlob(`/api/v1/admin/analytics/export.csv?days=${days}`),
};
