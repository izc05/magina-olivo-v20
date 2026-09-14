'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { BusinessDirectoryMap } from './business-directory-map';
import {
  loadBusinessCategories,
  loadBusinesses,
  type BusinessCategory,
  type BusinessDirectoryItem,
} from '@/lib/business-directory-source';
import styles from './business-directory.module.css';

function mediaUrl(value: string | null) {
  if (!value) return null;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.toString() : null;
  } catch {
    return null;
  }
}

function BusinessCard({ business }: { business: BusinessDirectoryItem }) {
  const image = mediaUrl(business.coverImageUrl ?? business.logoUrl);
  const place = business.territory.placeName ?? business.territory.municipalityName ?? 'Sierra Mágina';
  return <article className={styles.card}>
    <div className={styles.cardMedia}>
      {image
        ? <img src={image} alt="" loading="lazy" />
        : <div className={styles.cardPlaceholder} aria-hidden="true">◉</div>}
      {business.placement.label ? <span className={styles.placement}>{business.placement.label}</span> : null}
    </div>
    <div className={styles.cardBody}>
      <div className={styles.cardTitleRow}>
        <h2>{business.name}</h2>
        {business.verified ? <span className={styles.verified} title="Ficha verificada" aria-label="Ficha verificada">✓</span> : null}
      </div>
      <div className={styles.meta}>
        <span>{place}</span>
        {business.distanceKm !== null ? <span>{business.distanceKm.toFixed(1)} km</span> : null}
      </div>
      {business.shortDescription ? <p>{business.shortDescription}</p> : null}
      {business.categories.length ? <ul className={styles.categoryList} aria-label={`Categorías de ${business.name}`}>
        {business.categories.slice(0, 3).map((category) => <li key={category.slug}>{category.name}</li>)}
      </ul> : null}
      <Link className={styles.cardLink} href={`/empresas?slug=${encodeURIComponent(business.slug)}`}>Ver ficha completa →</Link>
    </div>
  </article>;
}

export function BusinessDirectoryPage() {
  const [categories, setCategories] = useState<BusinessCategory[]>([]);
  const [businesses, setBusinesses] = useState<BusinessDirectoryItem[]>([]);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('');
  const [municipality, setMunicipality] = useState('');
  const [coordinates, setCoordinates] = useState<{ lat: number; lng: number } | null>(null);
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    loadBusinessCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setLoading(true);
      setError(false);
      void loadBusinesses({
        q: query || undefined,
        category: category || undefined,
        lat: coordinates?.lat,
        lng: coordinates?.lng,
        radiusKm: coordinates ? 50 : undefined,
        limit: 100,
      }).then((payload) => {
        if (!cancelled) setBusinesses(payload.businesses);
      }).catch((cause) => {
        console.error('Unable to load business directory', cause);
        if (!cancelled) {
          setBusinesses([]);
          setError(true);
        }
      }).finally(() => {
        if (!cancelled) setLoading(false);
      });
    }, 220);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [query, category, coordinates]);

  const municipalities = useMemo(() => {
    const values = new Map<string, string>();
    for (const business of businesses) {
      const id = business.territory.municipalityId;
      const name = business.territory.municipalityName;
      if (id && name) values.set(id, name);
    }
    return [...values.entries()].sort((a, b) => a[1].localeCompare(b[1], 'es'));
  }, [businesses]);

  const visibleBusinesses = useMemo(
    () => municipality
      ? businesses.filter((business) => business.territory.municipalityId === municipality)
      : businesses,
    [businesses, municipality],
  );

  const locate = () => {
    if (!navigator.geolocation) {
      setLocationError('Tu navegador no permite obtener la ubicación.');
      return;
    }
    setLocating(true);
    setLocationError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setCoordinates({ lat: position.coords.latitude, lng: position.coords.longitude });
        setLocating(false);
      },
      () => {
        setLocationError('No hemos podido usar tu ubicación. Puedes seguir buscando por categoría o municipio.');
        setLocating(false);
      },
      { enableHighAccuracy: false, timeout: 8_000, maximumAge: 300_000 },
    );
  };

  return <main className={styles.page}>
    <section className={styles.hero}>
      <div className={styles.heroMain}>
        <p className={styles.eyebrow}>SIERRA MÁGINA · EMPRESAS Y SERVICIOS</p>
        <h1>Lo que necesitas, más cerca.</h1>
        <p>Descubre empresas, productores, comercios, alojamientos, restauración y profesionales del territorio. Las promociones comerciales se identifican siempre de forma visible.</p>
      </div>
      <aside className={styles.heroAside}>
        <div><strong>{businesses.length}</strong><p>fichas disponibles con los filtros actuales</p></div>
        <div><strong>{categories.filter((item) => item.business_count > 0).length}</strong><p>categorías con actividad publicada</p></div>
      </aside>
    </section>

    <section className={styles.toolbar} aria-label="Buscar y filtrar empresas">
      <div className={styles.searchRow}>
        <input
          type="search"
          aria-label="Buscar empresa o servicio"
          placeholder="Buscar empresa, taller, alojamiento, AOVE…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <button className={styles.secondaryButton} type="button" onClick={coordinates ? () => setCoordinates(null) : locate} disabled={locating}>
          {locating ? 'Localizando…' : coordinates ? 'Quitar cercanía' : 'Cerca de mí'}
        </button>
      </div>

      <div className={styles.filterRow}>
        <select className={styles.select} value={category} onChange={(event) => setCategory(event.target.value)} aria-label="Categoría">
          <option value="">Todas las categorías</option>
          {categories.map((item) => <option key={item.id} value={item.slug}>{item.name} ({item.business_count})</option>)}
        </select>
        <select className={styles.select} value={municipality} onChange={(event) => setMunicipality(event.target.value)} aria-label="Municipio">
          <option value="">Todos los municipios</option>
          {municipalities.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
        </select>
        <ul className={styles.chips} aria-label="Filtros rápidos">
          <li><button className={!category ? styles.activeChip : styles.chip} type="button" onClick={() => setCategory('')}>Todo</button></li>
          {categories.filter((item) => item.business_count > 0).slice(0, 4).map((item) => <li key={item.id}><button className={category === item.slug ? styles.activeChip : styles.chip} type="button" onClick={() => setCategory(item.slug)}>{item.name}</button></li>)}
        </ul>
      </div>
      {locationError ? <div className={styles.error}>{locationError}</div> : null}
    </section>

    <div className={styles.summaryBar}>
      <span>{loading ? 'Actualizando resultados…' : `${visibleBusinesses.length} ${visibleBusinesses.length === 1 ? 'empresa' : 'empresas'}`}</span>
      <span>Patrocinado y Destacado siempre aparecen identificados.</span>
    </div>

    {error ? <section className={styles.state} role="alert"><strong>El directorio no está disponible ahora.</strong><p>No mostramos fichas inventadas ni datos de respaldo desactualizados. Reintenta más tarde.</p><button className={styles.secondaryButton} type="button" onClick={() => window.location.reload()}>Reintentar</button></section> : null}
    {!error && !loading && visibleBusinesses.length === 0 ? <section className={styles.state}><strong>No hay coincidencias.</strong><p>Cambia la búsqueda o los filtros para explorar otras empresas del territorio.</p><button className={styles.secondaryButton} type="button" onClick={() => { setQuery(''); setCategory(''); setMunicipality(''); setCoordinates(null); }}>Limpiar filtros</button></section> : null}

    {!error && visibleBusinesses.length > 0 ? <section className={styles.contentGrid}>
      <div className={styles.cards} aria-label="Empresas del territorio">
        {visibleBusinesses.map((business) => <BusinessCard key={business.id} business={business} />)}
      </div>
      <BusinessDirectoryMap businesses={visibleBusinesses} />
    </section> : null}
  </main>;
}
