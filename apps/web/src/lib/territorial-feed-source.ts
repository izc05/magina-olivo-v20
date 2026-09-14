import { loadBusinesses, type BusinessDirectoryItem } from './business-directory-source';
import { editorialDetails, loadPublicEditorial, type PublicEditorialEntry } from './public-editorial-source';
import { findMaginaTown } from './towns';

export type TerritorialFeedItem = {
  id: string;
  kind: 'news' | 'event' | 'business';
  townSlug: string;
  townName: string;
  title: string;
  summary: string | null;
  href: string;
  timestamp: string | null;
  featured: boolean;
};

function editorialTown(entry: PublicEditorialEntry) {
  const details = editorialDetails(entry);
  return findMaginaTown(details.town || details.location);
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
    href: `/empresas/${encodeURIComponent(business.slug)}`,
    timestamp: null,
    featured: business.placement.featured || business.placement.sponsored,
  };
}

export async function loadTerritorialFeed(followedTownSlugs: string[]): Promise<TerritorialFeedItem[]> {
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
      editorial.push({
        id: `${entry.type}:${entry.id}`,
        kind: entry.type,
        townSlug: town.slug,
        townName: town.name,
        title: entry.title,
        summary: entry.summary,
        href: editorialHref(entry),
        timestamp: editorialTimestamp(entry),
        featured: entry.featured,
      });
    }
  }

  const businesses = businessesResult.status === 'fulfilled'
    ? businessesResult.value.businesses
      .map(businessFeedItem)
      .filter((item): item is TerritorialFeedItem => Boolean(item && allowed.has(item.townSlug)))
    : [];

  const now = Date.now();
  const sortedEditorial = editorial.sort((a, b) => {
    const aTime = a.timestamp ? new Date(a.timestamp).getTime() : 0;
    const bTime = b.timestamp ? new Date(b.timestamp).getTime() : 0;
    const aUpcoming = a.kind === 'event' && aTime >= now ? 1 : 0;
    const bUpcoming = b.kind === 'event' && bTime >= now ? 1 : 0;
    if (aUpcoming !== bUpcoming) return bUpcoming - aUpcoming;
    if (a.featured !== b.featured) return Number(b.featured) - Number(a.featured);
    return bTime - aTime;
  });

  return [...sortedEditorial, ...businesses].slice(0, 8);
}
