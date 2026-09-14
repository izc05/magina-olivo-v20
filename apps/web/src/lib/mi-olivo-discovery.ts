export type MiOlivoInteractionType =
  | 'content_read'
  | 'territory_viewed'
  | 'weather_checked'
  | 'learning_completed';

export type MiOlivoDiscoveryAction = {
  eventType: MiOlivoInteractionType;
  sourceId: string;
  delayMs: number;
};

export type MiOlivoDiscoveryWorld = {
  id: string;
  title: string;
  eyebrow: string;
  description: string;
  href: string;
  icon: string;
};

/**
 * The public worlds that make Mi Olivo a passport for the whole platform.
 * These links are intentionally independent from Mi Campo: a visitor can
 * grow their olive tree by learning about and exploring Sierra Mágina.
 */
export const MI_OLIVO_DISCOVERY_WORLDS = [
  {
    id: 'territory',
    title: 'Pueblos y patrimonio',
    eyebrow: 'Territorio',
    description: 'Conoce los municipios, su patrimonio y los lugares que dan identidad a Sierra Mágina.',
    href: '/ayuntamientos',
    icon: '🏘️',
  },
  {
    id: 'aove',
    title: 'Almazaras y AOVE',
    eyebrow: 'Cultura del aceite',
    description: 'Descubre dónde nace el aceite, quién lo produce y qué historias hay detrás de cada almazara.',
    href: '/almazaras',
    icon: '🫒',
  },
  {
    id: 'business',
    title: 'Empresas de Mágina',
    eyebrow: 'Economía local',
    description: 'Encuentra negocios y servicios del territorio y convierte cada descubrimiento en vínculo local.',
    href: '/explorar/empresas',
    icon: '🏪',
  },
  {
    id: 'experiences',
    title: 'Experiencias',
    eyebrow: 'Vive Mágina',
    description: 'Abre la puerta a visitas, actividades y planes que conectan el olivar con las personas.',
    href: '/experiencias',
    icon: '✨',
  },
  {
    id: 'explore',
    title: 'Explorar',
    eyebrow: 'Descubrimiento',
    description: 'Recorre la plataforma como un mapa vivo y encuentra nuevos motivos para volver a Mágina.',
    href: '/explorar',
    icon: '🧭',
  },
  {
    id: 'market',
    title: 'Mercado del aceite',
    eyebrow: 'Actualidad AOVE',
    description: 'Sigue precios y contexto del mercado para entender mejor lo que ocurre alrededor del olivar.',
    href: '/mercado',
    icon: '📈',
  },
  {
    id: 'news',
    title: 'Noticias y agenda',
    eyebrow: 'Qué está pasando',
    description: 'Lee historias y eventos del territorio. El contenido útil también deja huella en tu olivo.',
    href: '/noticias',
    icon: '📰',
  },
  {
    id: 'weather',
    title: 'Tiempo y radar',
    eyebrow: 'Mágina hoy',
    description: 'Consulta el cielo de la comarca y mantén tu olivo conectado con lo que ocurre cada día.',
    href: '/radar',
    icon: '🌦️',
  },
] as const satisfies readonly MiOlivoDiscoveryWorld[];

const SURFACE_SOURCES: Readonly<Record<string, string>> = {
  '/almazaras': 'pueblo:surface:almazaras',
  '/cooperativas': 'pueblo:surface:cooperativas',
  '/explorar': 'pueblo:surface:explorar',
  '/explorar/empresas': 'pueblo:surface:empresas',
  '/experiencias': 'pueblo:surface:experiencias',
  '/mercado': 'pueblo:surface:mercado',
  '/pueblos': 'pueblo:surface:pueblos',
  '/ayuntamientos': 'pueblo:surface:ayuntamientos',
  '/servicios': 'pueblo:surface:servicios',
};

function normalizePath(pathname: string) {
  if (!pathname || pathname === '/') return '/';
  return pathname.endsWith('/') ? pathname.slice(0, -1) : pathname;
}

function sourceToken(value: string | null | undefined) {
  if (!value) return null;
  const normalized = value
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80);
  return normalized || null;
}

/**
 * Maps navigation to the event vocabulary currently accepted by the V2 API.
 * Public discovery surfaces temporarily use `territory_viewed` with a
 * namespaced `pueblo:` source so they remain server-validated and idempotent.
 * A future V3 backend can promote these namespaces to explicit event types
 * without changing the navigation contract established here.
 */
export function miOlivoDiscoveryActionForRoute(pathname: string, query: string): MiOlivoDiscoveryAction | null {
  const path = normalizePath(pathname);
  const params = new URLSearchParams(query);
  const slug = sourceToken(params.get('slug'));

  if (path === '/noticias' && slug) {
    return { eventType: 'content_read', sourceId: `noticia:${slug}`, delayMs: 8_000 };
  }
  if (path === '/eventos' && slug) {
    return { eventType: 'content_read', sourceId: `evento:${slug}`, delayMs: 8_000 };
  }
  if (path === '/pueblos' && slug) {
    return { eventType: 'territory_viewed', sourceId: `pueblo:${slug}`, delayMs: 6_000 };
  }

  const municipalityMatch = path.match(/^\/ayuntamientos\/([^/]+)$/);
  const municipalitySlug = sourceToken(municipalityMatch?.[1]);
  if (municipalitySlug) {
    return { eventType: 'territory_viewed', sourceId: `pueblo:${municipalitySlug}`, delayMs: 6_000 };
  }

  if ((path === '/almazaras' || path === '/cooperativas') && slug) {
    const namespace = path === '/almazaras' ? 'almazara' : 'cooperativa';
    return { eventType: 'territory_viewed', sourceId: `pueblo:${namespace}:${slug}`, delayMs: 7_000 };
  }

  if (path === '/empresas' && slug) {
    return { eventType: 'territory_viewed', sourceId: `pueblo:empresa:${slug}`, delayMs: 7_000 };
  }

  const experienceBusiness = sourceToken(params.get('business'));
  if (path === '/experiencias' && experienceBusiness && slug) {
    return {
      eventType: 'territory_viewed',
      sourceId: `pueblo:experiencia:${experienceBusiness}:${slug}`,
      delayMs: 9_000,
    };
  }

  if (path === '/radar') {
    return { eventType: 'weather_checked', sourceId: 'radar', delayMs: 5_000 };
  }
  if (path === '/consejos' && slug) {
    return { eventType: 'learning_completed', sourceId: `consejo:${slug}`, delayMs: 10_000 };
  }

  const surfaceSource = SURFACE_SOURCES[path];
  if (surfaceSource) {
    return { eventType: 'territory_viewed', sourceId: surfaceSource, delayMs: 7_000 };
  }

  return null;
}
