export const MUNICIPALITY_QUERY_KEY = 'municipio';

export function readMunicipalitySlug(): string {
  if (typeof window === 'undefined') return '';
  const params = new URLSearchParams(window.location.search);
  return params.get(MUNICIPALITY_QUERY_KEY)?.trim() || window.location.hash.replace(/^#/, '').trim();
}

export function municipalityAdminHref(path: string, slug?: string | null) {
  if (!slug) return path;
  const separator = path.includes('?') ? '&' : '?';
  return `${path}${separator}${MUNICIPALITY_QUERY_KEY}=${encodeURIComponent(slug)}`;
}

export function publicMunicipalityHref(slug?: string | null) {
  return slug ? `/ayuntamientos/${encodeURIComponent(slug)}` : '/ayuntamientos';
}

export function replaceMunicipalityContext(slug?: string | null) {
  if (typeof window === 'undefined') return;
  const url = new URL(window.location.href);
  if (slug) url.searchParams.set(MUNICIPALITY_QUERY_KEY, slug);
  else url.searchParams.delete(MUNICIPALITY_QUERY_KEY);
  url.hash = '';
  window.history.replaceState(null, '', `${url.pathname}${url.search}`);
}
