import { apiFetch } from './api-client';

type CmsDirectoryEntry = {
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

export type PublicService = {
  id: string;
  slug: string;
  title: string;
  summary: string | null;
  body: string | null;
  placeId: string | null;
  placeName: string | null;
  placeSlug: string | null;
  municipalityId: string | null;
  municipalityName: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  services: string[];
  openingHours: string | null;
  instagram: string | null;
  facebook: string | null;
  campaignNotes: string | null;
  coordinates: { latitude: number; longitude: number } | null;
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

function stringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is string => typeof item === 'string')
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 30);
}

function coordinates(value: unknown): PublicService['coordinates'] {
  const record = asRecord(value);
  const latitude = typeof record.latitude === 'number' ? record.latitude : Number.NaN;
  const longitude = typeof record.longitude === 'number' ? record.longitude : Number.NaN;
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;
  return { latitude, longitude };
}

function toPublicService(entry: CmsDirectoryEntry): PublicService {
  const content = asRecord(entry.content_json);
  return {
    id: entry.id,
    slug: entry.slug,
    title: entry.title,
    summary: entry.summary,
    body: text(content.body),
    placeId: text(content.territory_place_id),
    placeName: text(content.territory_place_name),
    placeSlug: text(content.territory_place_slug),
    municipalityId: text(content.municipality_id),
    municipalityName: text(content.municipality_name),
    phone: text(content.phone),
    email: text(content.email),
    address: text(content.address),
    services: stringList(content.services),
    openingHours: text(content.opening_hours),
    instagram: text(content.instagram),
    facebook: text(content.facebook),
    campaignNotes: text(content.campaign_notes),
    coordinates: coordinates(content.coordinates),
    ctaLabel: text(content.cta_label),
    mediaUrl: entry.media_url,
    externalUrl: entry.external_url,
    featured: Boolean(entry.featured),
    sortOrder: Number(entry.sort_order) || 0,
    updatedAt: entry.updated_at,
    publishedAt: entry.published_at,
  };
}

export async function loadPublicServices(): Promise<PublicService[]> {
  const payload = await apiFetch<{ entries: CmsDirectoryEntry[] }>('/api/v1/public/content?type=directory');
  return payload.entries
    .filter((entry) => entry.type === 'directory' && entry.slug && entry.title)
    .map(toPublicService)
    .sort((a, b) => Number(b.featured) - Number(a.featured) || a.sortOrder - b.sortOrder || a.title.localeCompare(b.title, 'es'));
}
