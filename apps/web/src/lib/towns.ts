export type MaginaTown = {
  slug: string;
  name: string;
  aliases: readonly string[];
  inNaturalPark: boolean;
};

/**
 * Catálogo territorial canónico de Sierra Mágina.
 *
 * Alcance de producto: 16 municipios de la comarca turística de Sierra Mágina.
 * `inNaturalPark` distingue los 9 municipios con término municipal dentro del
 * Parque Natural Sierra Mágina.
 */
export const MAGINA_TOWNS: readonly MaginaTown[] = [
  { slug: 'albanchez-de-magina', name: 'Albanchez de Mágina', aliases: ['Albanchez'], inNaturalPark: true },
  { slug: 'bedmar-y-garciez', name: 'Bedmar y Garcíez', aliases: ['Bedmar', 'Garcíez', 'Bedmar-Garcíez'], inNaturalPark: true },
  { slug: 'belmez-de-la-moraleda', name: 'Bélmez de la Moraleda', aliases: ['Bélmez'], inNaturalPark: true },
  { slug: 'cabra-del-santo-cristo', name: 'Cabra del Santo Cristo', aliases: [], inNaturalPark: false },
  { slug: 'cambil', name: 'Cambil', aliases: ['Arbuniel'], inNaturalPark: true },
  { slug: 'campillo-de-arenas', name: 'Campillo de Arenas', aliases: [], inNaturalPark: false },
  { slug: 'carcheles', name: 'Cárcheles', aliases: ['Cárchel', 'Carchelejo'], inNaturalPark: false },
  { slug: 'huelma', name: 'Huelma', aliases: ['Solera'], inNaturalPark: true },
  { slug: 'jimena', name: 'Jimena', aliases: [], inNaturalPark: true },
  { slug: 'jodar', name: 'Jódar', aliases: [], inNaturalPark: true },
  { slug: 'la-guardia-de-jaen', name: 'La Guardia de Jaén', aliases: ['La Guardia'], inNaturalPark: false },
  { slug: 'larva', name: 'Larva', aliases: [], inNaturalPark: false },
  { slug: 'mancha-real', name: 'Mancha Real', aliases: [], inNaturalPark: false },
  { slug: 'noalejo', name: 'Noalejo', aliases: ['La Hoya del Salobral'], inNaturalPark: false },
  { slug: 'pegalajar', name: 'Pegalajar', aliases: ['La Cerradura'], inNaturalPark: true },
  { slug: 'torres', name: 'Torres', aliases: [], inNaturalPark: true },
] as const;

function normalizeTownText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLocaleLowerCase('es')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

export function findMaginaTown(value: string | null | undefined): MaginaTown | null {
  if (!value) return null;
  const normalized = normalizeTownText(value);
  return MAGINA_TOWNS.find((town) =>
    town.slug === normalized ||
    normalizeTownText(town.name) === normalized ||
    town.aliases.some((alias) => normalizeTownText(alias) === normalized),
  ) ?? null;
}

export function townModuleHref(href: string, town: MaginaTown | null): string {
  if (!town) return href;
  const separator = href.includes('?') ? '&' : '?';
  return `${href}${separator}municipio=${encodeURIComponent(town.slug)}`;
}
