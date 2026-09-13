'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { loadBusinesses, type BusinessDirectoryItem } from '@/lib/business-directory-source';
import { getPublishedContent } from '@/lib/public-content-source';
import { loadPublicMills, type PublicMill } from '@/lib/public-mills-source';
import { loadPublicRoutesByTerritory, type PublicRouteSummary } from '@/lib/public-routes-source';
import { loadPublicTerritoryPlace, type PublicTerritoryPlace } from '@/lib/public-territory-source';
import styles from './town-hub.module.css';

type EditorialEntry = Awaited<ReturnType<typeof getPublishedContent>>['entries'][number];

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function text(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function normalize(value: string | null | undefined) {
  return (value ?? '').trim().toLocaleLowerCase('es-ES');
}

function entryTown(entry: EditorialEntry) {
  return text(record(entry.content_json).town);
}

function belongsToTerritory(town: string | null, place: PublicTerritoryPlace) {
  const value = normalize(town);
  return Boolean(value && (value === normalize(place.name) || value === normalize(place.municipality_name)));
}

function contextualHref(path: string, place: PublicTerritoryPlace) {
  const join = path.includes('?') ? '&' : '?';
  return `${path}${join}fromPlace=${encodeURIComponent(place.slug)}&fromName=${encodeURIComponent(place.name)}`;
}

export function TownHubClient() {
  const params = useSearchParams();
  const slug = params.get('slug')?.trim() ?? '';
  const [place, setPlace] = useState<PublicTerritoryPlace | null>(null);
  const [routes, setRoutes] = useState<PublicRouteSummary[]>([]);
  const [businesses, setBusinesses] = useState<BusinessDirectoryItem[]>([]);
  const [news, setNews] = useState<EditorialEntry[]>([]);
  const [events, setEvents] = useState<EditorialEntry[]>([]);
  const [mills, setMills] = useState<PublicMill[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!slug) { setLoading(false); setError(true); return; }
    let cancelled = false;
    setLoading(true);
    setError(false);
    loadPublicTerritoryPlace(slug)
      .then(async (territory) => {
        const useMunicipality = territory.kind === 'municipal_seat';
        const [routeRows, businessPayload, newsPayload, eventPayload, millRows] = await Promise.all([
          loadPublicRoutesByTerritory({ placeId: useMunicipality ? null : territory.id, municipalityId: territory.municipality_id, limit: 8 }),
          loadBusinesses(useMunicipality ? { municipalityId: territory.municipality_id, limit: 8 } : { placeId: territory.id, limit: 8 }),
          getPublishedContent('news'),
          getPublishedContent('event'),
          loadPublicMills(),
        ]);
        if (cancelled) return;
        setPlace(territory);
        setRoutes(routeRows);
        setBusinesses(businessPayload.businesses);
        setNews(newsPayload.entries.filter((entry) => belongsToTerritory(entryTown(entry), territory)).slice(0, 6));
        setEvents(eventPayload.entries.filter((entry) => belongsToTerritory(entryTown(entry), territory)).slice(0, 6));
        setMills(millRows.filter((item) => belongsToTerritory(item.town, territory)).slice(0, 6));
      })
      .catch((cause) => {
        console.error('Unable to load town hub', cause);
        if (!cancelled) setError(true);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [slug]);

  const subtitle = useMemo(() => {
    if (!place) return '';
    return place.name === place.municipality_name
      ? `Municipio de ${place.province_name}`
      : `${place.name} · municipio de ${place.municipality_name}`;
  }, [place]);

  if (loading) return <main className={styles.page}><section className={styles.state}><strong>Cargando pueblo…</strong><p>Conectando rutas, empresas y contenido territorial publicado.</p></section></main>;
  if (error || !place) return <main className={styles.page}><Link className={styles.back} href="/explorar">← Volver a Explorar</Link><section className={styles.state}><strong>No podemos mostrar este pueblo.</strong><p>Puede no estar publicado o el catálogo territorial estar temporalmente no disponible.</p></section></main>;

  return <main className={styles.page}>
    <Link className={styles.back} href="/explorar">← Explorar Sierra Mágina</Link>
    <header className={styles.hero}>
      <span className={styles.eyebrow}>PUEBLO · TERRITORIO CONECTADO</span>
      <h1>{place.name}</h1>
      <p>{subtitle}. Esta ficha reúne únicamente contenido público relacionado con este territorio; no muestra datos privados de fincas.</p>
      <div className={styles.meta}><span>{place.kind === 'municipal_seat' ? 'Cabecera municipal' : 'Localidad'}</span><span>INE {place.ine_code}</span>{place.aemet_code ? <span>AEMET disponible</span> : null}</div>
      <div className={styles.actions}><a className={`${styles.action} ${styles.primary}`} href="#rutas">Ver rutas</a><a className={styles.action} href="#empresas">Ver empresas</a><Link className={styles.action} href="/radar">Tiempo y radar</Link></div>
    </header>

    <section id="rutas" className={styles.section}>
      <div className={styles.sectionHead}><div><h2>Rutas y experiencias</h2><p>Recorridos publicados con track validado relacionados con {place.name}.</p></div><Link href="/rutas">Todas las rutas →</Link></div>
      {routes.length ? <div className={styles.grid}>{routes.map((route) => <Link className={styles.card} key={route.id} href={contextualHref(`/rutas/detalle?slug=${encodeURIComponent(route.slug)}`, place)}><small>{route.route_type} · {route.difficulty ?? 'sin dificultad publicada'}</small><h3>{route.name}</h3><p>{route.short_description ?? 'Ruta publicada de Sierra Mágina.'}</p><strong>{route.distance_m === null ? 'Distancia no publicada' : `${(route.distance_m / 1000).toFixed(1)} km`}</strong></Link>)}</div> : <div className={styles.empty}>No hay rutas publicadas y validadas vinculadas a este territorio.</div>}
    </section>

    <section id="empresas" className={styles.section}>
      <div className={styles.sectionHead}><div><h2>Empresas y servicios</h2><p>Negocios publicados del pueblo o de su municipio según el nivel territorial de la ficha.</p></div><Link href="/explorar/empresas">Directorio completo →</Link></div>
      {businesses.length ? <div className={styles.grid}>{businesses.map((business) => {
        const categoryText = business.categories.map((category) => category.name).slice(0, 3).join(' · ');
        const description = business.shortDescription ?? (categoryText || 'Empresa publicada en Mágina Olivo.');
        return <Link className={styles.card} key={business.id} href={contextualHref(`/empresas?slug=${encodeURIComponent(business.slug)}`, place)}><small>{business.placement.label ?? (business.verified ? 'Ficha verificada' : 'Servicio local')}</small><h3>{business.name}</h3><p>{description}</p></Link>;
      })}</div> : <div className={styles.empty}>No hay empresas publicadas vinculadas a este territorio.</div>}
    </section>

    <section id="actualidad" className={styles.section}>
      <div className={styles.sectionHead}><div><h2>Agenda y actualidad</h2><p>Noticias y eventos publicados cuyo territorio coincide con {place.name} o su municipio.</p></div></div>
      {(news.length || events.length) ? <div className={styles.grid}>
        {events.map((entry) => <Link className={styles.card} key={`event-${entry.id}`} href={contextualHref(`/eventos?slug=${encodeURIComponent(entry.slug)}`, place)}><small>Evento</small><h3>{entry.title}</h3>{entry.summary ? <p>{entry.summary}</p> : null}</Link>)}
        {news.map((entry) => <Link className={styles.card} key={`news-${entry.id}`} href={contextualHref(`/noticias?slug=${encodeURIComponent(entry.slug)}`, place)}><small>Noticia</small><h3>{entry.title}</h3>{entry.summary ? <p>{entry.summary}</p> : null}</Link>)}
      </div> : <div className={styles.empty}>No hay noticias o eventos territoriales publicados ahora mismo.</div>}
    </section>

    <section id="cooperativas" className={styles.section}>
      <div className={styles.sectionHead}><div><h2>Cooperativas y almazaras</h2><p>Entidades del aceite publicadas para este territorio.</p></div><Link href="/cooperativas">Directorio completo →</Link></div>
      {mills.length ? <div className={styles.grid}>{mills.map((mill) => <Link className={styles.card} key={mill.id} href={contextualHref(`/cooperativas?slug=${encodeURIComponent(mill.slug)}`, place)}><small>Cooperativa / almazara</small><h3>{mill.title}</h3>{mill.summary ? <p>{mill.summary}</p> : null}</Link>)}</div> : <div className={styles.empty}>No hay cooperativas o almazaras publicadas con este pueblo asignado.</div>}
    </section>
  </main>;
}
