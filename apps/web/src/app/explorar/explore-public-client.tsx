'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { MapPinIcon, RainIcon } from '@/components/icons';
import {
  loadPublicTerritoryPlaces,
  type PublicTerritoryPlace,
} from '@/lib/public-territory-source';
import styles from './explore-public.module.css';

const areas = [
  { icon: '📰', title: 'Noticias', text: 'Actualidad local con fuente y fecha verificadas.', href: '/noticias', badge: 'Disponible' },
  { icon: '📅', title: 'Eventos', text: 'Agenda con organizador, fechas y estado de verificación.', href: '/eventos', badge: 'Disponible' },
  { icon: '🫒', title: 'Aceite y mercado', text: 'Precios y campaña manteniendo siempre fuente, unidad y fecha.', href: '/mercado', badge: 'Disponible' },
  { icon: '🏭', title: 'Almazaras y cooperativas', text: 'Directorio público con información verificada.', href: '/cooperativas', badge: 'Disponible' },
  { icon: '🏪', title: 'Servicios', text: 'Negocios y profesionales con patrocinio claramente identificado.', href: '/servicios', badge: 'Disponible' },
  { icon: '🌿', title: 'Consejos del campo', text: 'Guías prácticas de observación, manejo y seguridad en el olivar.', href: '/consejos', badge: 'Disponible' },
  { icon: '🧭', title: 'Rutas y experiencias', text: 'Contenidos territoriales y oleoturismo.', href: null, badge: 'En preparación' },
] as const;

function placeKindLabel(kind: string) {
  if (kind === 'municipal_seat') return 'Cabecera municipal';
  if (kind === 'locality') return 'Localidad';
  if (kind === 'hamlet') return 'Núcleo';
  return 'Lugar';
}

export function ExplorePublicClient() {
  const [places, setPlaces] = useState<PublicTerritoryPlace[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedSlug, setSelectedSlug] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    loadPublicTerritoryPlaces()
      .then((items) => {
        if (cancelled) return;
        setPlaces(items);
        setSelectedSlug((current) => current ?? items[0]?.slug ?? null);
        setError(false);
      })
      .catch((cause) => {
        console.warn('Unable to load public territory', cause);
        if (!cancelled) setError(true);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const filteredPlaces = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('es-ES');
    if (!normalized) return places;
    return places.filter((place) => `${place.name} ${place.municipality_name} ${place.province_name}`.toLocaleLowerCase('es-ES').includes(normalized));
  }, [places, query]);

  const selectedPlace = places.find((place) => place.slug === selectedSlug) ?? null;

  return <>
    <section className={`explore-hero ${styles.hero}`}>
      <div className={styles.heroTop}>
        <div><span className="eyebrow">DESCUBRE EL TERRITORIO</span><h1>Sierra Mágina,<br/>en tu mano</h1><p>Explora el territorio público sin mezclarlo con la ubicación ni los datos privados de tus fincas.</p></div>
        <span className={styles.contextPill}><MapPinIcon/> Sierra Mágina · Jaén</span>
      </div>
      <label>
        <span className="sr-only">Buscar pueblo o localidad</span>
        <input className={styles.search} type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar pueblo o localidad…" />
      </label>
    </section>

    <section className="section">
      <div className={styles.sectionIntro}><div><h2>Pueblos y localidades</h2></div><p>Catálogo territorial publicado por Mágina Olivo. Solo aparecen lugares habilitados públicamente en la base de datos.</p></div>
      {loading ? <div className={styles.stateCard} aria-live="polite"><h3>Cargando territorio…</h3><p>Consultando el catálogo público.</p></div> : null}
      {!loading && error ? <div className={styles.stateCard}><h3>El catálogo territorial no está disponible</h3><p>No mostramos datos de sustitución inventados. Puedes seguir consultando el radar público y volver a intentarlo más tarde.</p></div> : null}
      {!loading && !error && filteredPlaces.length === 0 ? <div className={styles.stateCard}><h3>No hay coincidencias</h3><p>No encontramos un lugar público con “{query.trim()}”.</p></div> : null}
      {!loading && !error && filteredPlaces.length > 0 ? <div className={styles.placeGrid}>{filteredPlaces.map((place) => <button key={place.id} type="button" className={`${styles.placeButton} ${selectedPlace?.id === place.id ? styles.placeButtonActive : ''}`} onClick={() => setSelectedSlug(place.slug)} aria-pressed={selectedPlace?.id === place.id}><strong>{place.name}</strong><span>{placeKindLabel(place.kind)} · {place.province_name}</span></button>)}</div> : null}

      {selectedPlace && !error ? <div className={styles.detail}>
        <div><span className="eyebrow dark">CONTEXTO TERRITORIAL</span><h3>{selectedPlace.name}</h3><p>{selectedPlace.name === selectedPlace.municipality_name ? `Municipio oficial de ${selectedPlace.province_name}.` : `${selectedPlace.name} pertenece al municipio oficial de ${selectedPlace.municipality_name}.`} Este contexto es público y no identifica ninguna finca.</p></div>
        <div className={styles.detailMeta}><span>{placeKindLabel(selectedPlace.kind)}</span><span>Municipio: {selectedPlace.municipality_name}</span><span>INE: {selectedPlace.ine_code}</span>{selectedPlace.aemet_code ? <span>AEMET: disponible</span> : null}</div>
      </div> : null}
    </section>

    <section className="section">
      <div className={styles.sectionIntro}><div><h2>Explorar por temas</h2></div><p>Accede a las áreas públicas que ya están disponibles en V20. Los módulos que todavía no tienen una fuente verificada permanecen claramente marcados como pendientes.</p></div>
      <div className={styles.areaGrid}>
        {areas.map((area) => {
          const content = <>
            <span className={styles.areaIcon} aria-hidden="true">{area.icon}</span>
            <div><h3>{area.title}</h3><p>{area.text}</p><span className={`${styles.badge} ${area.href ? styles.badgeLive : ''}`}>{area.badge}</span></div>
          </>;
          return area.href
            ? <Link className={`${styles.areaCard} ${styles.areaCardLink}`} href={area.href} key={area.title}>{content}</Link>
            : <article className={styles.areaCard} key={area.title}>{content}</article>;
        })}
      </div>
    </section>

    <section className="section"><div className="section-head"><h2>Tiempo en Mágina</h2><Link href="/radar">Abrir radar</Link></div><Link href="/radar" className="card weather-feature"><span className="weather-feature-icon"><RainIcon/></span><div><strong>Radar y avisos de lluvia</strong><small>Consulta la información meteorológica disponible. La capa pública no revela tus fincas.</small></div></Link></section>
  </>;
}
