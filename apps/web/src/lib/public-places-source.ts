import { apiFetch } from './api-client';

type CmsPlaceEntry = {
  id: string;
  type: string;
  slug: string;
  title: string;
  summary: string | null;
  content_json: unknown;
  featured: boolean;
  media_url: string | null;
  external_url: string | null;
  sort_order: number;
  updated_at: string;
  published_at: string | null;
};

export type PublicPlace = {
  id: string;
  slug: string;
  title: string;
  summary: string | null;
  body: string | null;
  location: string | null;
  town: string | null;
  address: string | null;
  ctaLabel: string | null;
  mediaUrl: string | null;
  externalUrl: string | null;
  featured: boolean;
  sortOrder: number;
  updatedAt: string;
  publishedAt: string | null;
};

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function toPublicPlace(entry: CmsPlaceEntry): PublicPlace {
  const content = asRecord(entry.content_json);
  return {
    id: entry.id,
    slug: entry.slug,
    title: entry.title,
    summary: entry.summary,
    body: text(content.body),
    location: text(content.location),
    town: text(content.town),
    address: text(content.address),
    ctaLabel: text(content.cta_label),
    mediaUrl: entry.media_url,
    externalUrl: entry.external_url,
    featured: Boolean(entry.featured),
    sortOrder: Number(entry.sort_order) || 0,
    updatedAt: entry.updated_at,
    publishedAt: entry.published_at,
  };
}

export async function loadPublicPlaces(): Promise<PublicPlace[]> {
  const payload = await apiFetch<{ entries: CmsPlaceEntry[] }>('/api/v1/public/content?type=place');
  return payload.entries
    .filter((entry) => entry.type === 'place' && entry.slug && entry.title)
    .map(toPublicPlace)
    .sort((a, b) => Number(b.featured) - Number(a.featured) || a.sortOrder - b.sortOrder || a.title.localeCompare(b.title, 'es'));
}
