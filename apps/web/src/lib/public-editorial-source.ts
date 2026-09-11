import { apiFetch } from './api-client';

export type PublicEditorialType = 'news' | 'event';

export type PublicEditorialEntry = {
  id: string;
  type: PublicEditorialType;
  slug: string;
  title: string;
  summary: string | null;
  content_json: unknown;
  featured: boolean;
  starts_at: string | null;
  ends_at: string | null;
  media_url: string | null;
  external_url: string | null;
  sort_order: number;
  published_at: string | null;
};

type PublicEditorialPayload = {
  entries: PublicEditorialEntry[];
};

export type EditorialDetails = {
  body: string;
  location: string;
  town: string;
  address: string;
  eventStart: string | null;
  eventEnd: string | null;
};

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function stringValue(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : '';
}

function nullableDate(value: unknown) {
  const candidate = stringValue(value);
  if (!candidate) return null;
  const date = new Date(candidate);
  return Number.isNaN(date.getTime()) ? null : candidate;
}

export function editorialDetails(entry: PublicEditorialEntry): EditorialDetails {
  const content = asObject(entry.content_json);
  return {
    body: stringValue(content.body),
    location: stringValue(content.location),
    town: stringValue(content.town),
    address: stringValue(content.address),
    eventStart: nullableDate(content.event_start) ?? entry.starts_at,
    eventEnd: nullableDate(content.event_end) ?? entry.ends_at,
  };
}

export async function loadPublicEditorial(type: PublicEditorialType) {
  const payload = await apiFetch<PublicEditorialPayload>(`/api/v1/public/content?type=${type}`);
  return payload.entries.filter((entry) => entry.type === type);
}
