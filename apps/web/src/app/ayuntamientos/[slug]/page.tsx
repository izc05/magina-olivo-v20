import type { Metadata } from 'next';
import { loadPublicMunicipality } from '@/lib/public-territory-source';
import { MUNICIPALITY_STATIC_CATALOG, municipalityMetadata } from '@/lib/municipality-seo';
import { MunicipalityDetailClient } from './municipality-detail-client';

export function generateStaticParams() {
  return MUNICIPALITY_STATIC_CATALOG.map(({ slug }) => ({ slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  try {
    const municipality = await loadPublicMunicipality(slug);
    return municipalityMetadata(slug, municipality);
  } catch {
    return municipalityMetadata(slug, null);
  }
}

export default async function MunicipalityDetailPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  return <MunicipalityDetailClient slug={slug} />;
}
