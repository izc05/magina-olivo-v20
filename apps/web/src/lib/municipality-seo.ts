import type { Metadata } from 'next';
import type { PublicMunicipalityContent, PublicMunicipalityDirectory } from '@/lib/public-territory-source';

// Canonical municipality identity source validated by QA 16/16 and the integration handoff.
export const MUNICIPALITY_STATIC_CATALOG = [
  { slug: 'albanchez-de-magina', name: 'Albanchez de Mágina' },
  { slug: 'bedmar-y-garciez', name: 'Bedmar y Garcíez' },
  { slug: 'belmez-de-la-moraleda', name: 'Bélmez de la Moraleda' },
  { slug: 'cabra-del-santo-cristo', name: 'Cabra del Santo Cristo' },
  { slug: 'cambil', name: 'Cambil' },
  { slug: 'campillo-de-arenas', name: 'Campillo de Arenas' },
  { slug: 'carcheles', name: 'Cárcheles' },
  { slug: 'la-guardia-de-jaen', name: 'La Guardia de Jaén' },
  { slug: 'huelma', name: 'Huelma' },
  { slug: 'jimena', name: 'Jimena' },
  { slug: 'jodar', name: 'Jódar' },
  { slug: 'larva', name: 'Larva' },
  { slug: 'mancha-real', name: 'Mancha Real' },
  { slug: 'noalejo', name: 'Noalejo' },
  { slug: 'pegalajar', name: 'Pegalajar' },
  { slug: 'torres', name: 'Torres' },
] as const;

export function municipalityStaticName(slug: string) {
  return MUNICIPALITY_STATIC_CATALOG.find((item) => item.slug === slug)?.name ?? null;
}

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

export function municipalityRole(entry: PublicMunicipalityContent) {
  const role = asObject(entry.content_json).municipality_role;
  return typeof role === 'string' ? role : '';
}

export function municipalityProfile(municipality: PublicMunicipalityDirectory | null | undefined) {
  return municipality?.related_content?.find((entry) => entry.type === 'place' && municipalityRole(entry) === 'profile') ?? null;
}

function validAbsoluteImage(value: string | null | undefined) {
  const candidate = value?.trim();
  if (!candidate) return null;
  try {
    const parsed = new URL(candidate);
    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') return null;
    return parsed.toString();
  } catch {
    return null;
  }
}

export function municipalityMetadata(slug: string, municipality?: PublicMunicipalityDirectory | null): Metadata {
  const name = municipality?.name || municipalityStaticName(slug) || 'Municipio de Sierra Mágina';
  const profile = municipalityProfile(municipality);
  const description = profile?.summary?.trim()
    || `Información municipal de ${name} en Sierra Mágina: ayuntamiento, territorio y contenidos locales publicados en Mágina Olivo.`;
  const image = validAbsoluteImage(profile?.media_url)
    || municipality?.related_content?.map((entry) => validAbsoluteImage(entry.media_url)).find(Boolean)
    || null;
  const title = `${name} · Sierra Mágina | Mágina Olivo`;
  const knownMunicipality = Boolean(municipalityStaticName(slug));

  return {
    title,
    description,
    robots: {
      index: knownMunicipality,
      follow: knownMunicipality,
    },
    openGraph: {
      type: 'website',
      locale: 'es_ES',
      siteName: 'Mágina Olivo',
      title,
      description,
      ...(image ? { images: [{ url: image, alt: `${name} · Sierra Mágina` }] } : {}),
    },
    twitter: {
      card: image ? 'summary_large_image' : 'summary',
      title,
      description,
      ...(image ? { images: [image] } : {}),
    },
  };
}
