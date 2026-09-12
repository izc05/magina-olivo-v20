import { apiFetch } from './api-client';
import type { CmsEntry, CmsEntryType } from './admin-data-source';

export async function getPublishedContent(type?: CmsEntryType) {
  const suffix = type ? `?type=${encodeURIComponent(type)}` : '';
  return apiFetch<{ entries: CmsEntry[] }>(`/api/v1/public/content${suffix}`);
}

export async function getPublicSiteSettings() {
  return apiFetch<{ settings: Record<string, unknown> }>('/api/v1/public/site-settings');
}
