import { loadBusinesses, type BusinessDirectoryItem } from './business-directory-source';
import { editorialDetails, loadPublicEditorial, type MunicipalNoticePriority, type PublicEditorialEntry } from './public-editorial-source';
import { findMaginaTown } from './towns';

export type TerritorialFeedItem = {
  id: string;
  kind: 'notice' | 'news' | 'event' | 'business';
  townSlug: string;
  townName: string;
  title: string;
  summary: string | null;
  href: string;
  timestamp: string | null;
  featured: boolean;
  noticePriority: MunicipalNoticePriority | null;
};

function editorialTown(entry: PublicEditorialEntry) {
  const details = editorialDetails(entry);
  return findMaginaTown(details.municipalitySlug || details.town || details.location);
}

function editorialTimestamp(entry: PublicEditorialEntry) {
  const details = editorialDetails(entry);
  return entry.type === 'event'
    ? details.eventStart ?? entry.starts_at
    : entry.published_at;
}

function editorialHref(entry: PublicEditorialEntry) {
  const base = entry.type === 'event' ? '/eventos' : '/noticias';
  return `${base}?slug=${encodeURIComponent(entry.slug)}`;
}

function businessFeedItem(business: BusinessDirectoryItem): TerritorialFeedItem | null {
  const town = findMaginaTown(business.territory.municipalitySlug ?? business.territory.municipalityName);
  if (!town) return null;
  return {
    id: `business:${business.id}`,
    kind: 'business',
    townSlug: town.slug,
    townName: town.name,
    title: business.name,
    summary: business.shortDescription,
    href: `/empresas?slug=${encodeURIComponent(business.slug)}`,
    timestamp: null,
    featured: business.placement.featured || business.placement.sponsored,
    noticePriority: null,
  };
}

function timestampValue(item: TerritorialFeedItem) {
  if (!item.timestamp) return 0;
  const value = new Date(item.timestamp).getTime();
  return Number.isNaN(value) ? 0 : value;
}

function noticeWeight(priority: MunicipalNoticePriority | null) {
  if (priority === 'urgent') return 90;
  if (priority === 'important') return 55;
  if (priority === 'normal') return 30;
  return 0;
}

function priorityScore(item: TerritorialFeedItem, primaryTownSlug: string | null, now: number) {
  const timestamp = timestampValue(item);
  let score = 0;

  if (primaryTownSlug && item.townSlug === primaryTownSlug) score += 100;
  if (item.featured) score += 25;

  if (item.kind === 'notice') {
    score += 120 + noticeWeight(item.noticePriority);
    if (timestamp > 0) {
      const ageDays = Math.max(0, (now - timestamp) / 86_400_000);
      score += Math.max(0, 10 - Math.min(10, ageDays));
    }
  } else if (item.kind === 'event') {
    if (timestamp >= now) {
      score += 60;
      const daysAway = Math.max(0, (timestamp - now) / 86_400_000);
      score += Math.max(0, 14 - Math.min(14, daysAway));
    } else {
      score -= 20;
    }
  } else if (item.kind === 'news') {
    score += 15;
    if (timestamp > 0) {
      const ageDays = Math.max(0, (now - timestamp) / 86_400_000);
      score += Math.max(0, 12 - Math.min(12, ageDays));
    }
  } else {
    score += 5;
  }

  return score;
}

export async function loadTerritorialFeed(
  followedTownSlugs: string[],
  primaryTownSlug: string | null = null,
): Promise<TerritorialFeedItem[]> {
  const allowed = new Set(followedTownSlugs);
  if (allowed.size === 0) return [];

  const [newsResult, eventsResult, businessesResult] = await Promise.allSettled([
    loadPublicEditorial('news'),
    loadPublicEditorial('event'),
    loadBusinesses({ limit: 50 }),
  ]);

  const editorial: TerritorialFeedItem[] = [];
  for (const result of [newsResult, eventsResult]) {
    if (result.status !== 'fulfilled') continue;
    for (const entry of result.value) {
      const town = editorialTown(entry);
      if (!town || !allowed.has(town.slug)) continue;
      const details = editorialDetails(entry);
      editorial.push({
        id: `${entry.type}:${entry.id}`,
        kind: details.municipalNotice ? 'notice' : entry.type,
        townSlug: town.slug,
        townName: town.name,
        title: entry.title,
        summary: entry.summary,
        href: editorialHref(entry),
        timestamp: editorialTimestamp(entry),
        featured: entry.featured,
        noticePriority: details.municipalNotice ? details.noticePriority : null,
      });
    }
  }

  const businesses = businessesResult.status === 'fulfilled'
    ? businessesResult.value.businesses
      .map(businessFeedItem)
      .filter((item): item is TerritorialFeedItem => Boolean(item && allowed.has(item.townSlug)))
    : [];

  const now = Date.now();
  return [...editorial, ...businesses]
    .sort((a, b) => {
      const scoreDifference = priorityScore(b, primaryTownSlug, now) - priorityScore(a, primaryTownSlug, now);
      if (scoreDifference !== 0) return scoreDifference;
      return timestampValue(b) - timestampValue(a);
    })
    .slice(0, 8);
}
