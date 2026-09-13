import { MunicipalityDetailClient } from './municipality-detail-client';

const municipalitySlugs = [
  'albanchez-de-magina',
  'bedmar-y-garciez',
  'belmez-de-la-moraleda',
  'cabra-del-santo-cristo',
  'cambil',
  'campillo-de-arenas',
  'carcheles',
  'la-guardia-de-jaen',
  'huelma',
  'jimena',
  'jodar',
  'larva',
  'mancha-real',
  'noalejo',
  'pegalajar',
  'torres',
] as const;

export function generateStaticParams() {
  return municipalitySlugs.map((slug) => ({ slug }));
}

export default async function MunicipalityDetailPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  return <MunicipalityDetailClient slug={slug} />;
}
