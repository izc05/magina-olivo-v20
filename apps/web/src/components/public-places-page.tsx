'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { loadPublicPlaces, type PublicPlace } from '@/lib/public-places-source';
import styles from './public-places.module.css';

function safeExternalUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
  } catch {
    return null;
  }
}

function safeMediaUrl(value: string | null): string | null {
  if (!value) return null;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  return safeExternalUrl(value);
}

function searchableText(item: PublicPlace) {
  return [item.title, item.summary, item.town, item.location, item.address]
    .filter(Boolean)
    .join(' ')
    .toLocaleLowerCase('es');
}

const townModules = [
  { href: '/explorar', icon: '🥾', title: 'Explorar y rutas', description: 'Naturaleza, lugares y experiencias para descubrir el territorio.' },
  { href: '/eventos', icon: '📅', title: 'Agenda y eventos', description: 'Fiestas, actividades y citas publicadas en Mágina Olivo.' },
  { href: '/noticias', icon: '📰', title: 'Noticias', description: 'Actualidad local y contenidos del territorio.' },
  { href: '/empresas', icon: '🏪', title: 'Empresas y servicios', description: 'Negocios, profesionales, comer, dormir y servicios cercanos.' },
  { href: '/almazaras', icon: '🫒', title: 'Almazaras y AOVE', description: 'Cooperativas, almazaras, aceite y cultura del olivar.' },
  { href: '/ayuntamientos', icon: '🏛️', title: 'Ayuntamiento', description: 'Información municipal y acceso a los recursos oficiales disponibles.' },
] as const;

function PlaceCard({ item }: { item: PublicPlace }) {
  const image = safeMediaUrl(item.mediaUrl);
  return <article className={styles.card}>
    {image
      ? <img className={styles.cardImage} src={image} alt="" loading="lazy" />
      : <div className={styles.cardPlaceholder} aria-hidden="true">⛰️</div>}
    <div className={styles.cardBody}>
      <div className={styles.metaRow}>
        <span>{item.featured ? 'Destacado' : 'Pueblo / lugar'}</span>
        {item.town && item.town !== item.title ? <span>{item.town}</span> : null}
      </div>
      <h2>{item.title}</h2>
      {item.summary ? <p>{item.summary}</p> : null}
      <div className={styles.locationLine}>{[item.location, item.address].filter(Boolean).join(' · ') || 'Información territorial pendiente'}</div>
      <Link className={styles.primaryLink} href={`/pueblos?slug=${encodeURIComponent(item.slug)}`}>Descubrir →</Link>
    </div>
  </article>;
}

function PlaceDetail({ item }: { item: PublicPlace }) {
  const image = safeMediaUrl(item.mediaUrl);
  const externalUrl = safeExternalUrl(item.externalUrl);
  return <>
    <Link className={styles.backLink} href="/pueblos">← Pueblos de Sierra Mágina</Link>
    <article className={styles.detail}>
      {image
        ? <img className={styles.heroImage} src={image} alt="" />
        : <div className={styles.heroPlaceholder} aria-hidden="true">⛰️</div>}
      <div className={styles.detailBody}>
        <div className={styles.metaRow}><span>Pueblo / lugar</span>{item.town && item.town !== item.title ? <span>{item.town}</span> : null}</div>
        <h1>{item.title}</h1>
        {item.summary ? <p className={styles.lead}>{item.summary}</p> : null}
        {item.body ? <p className={styles.bodyText}>{item.body}</p> : null}

        {item.town || item.location || item.address ? <dl className={styles.infoGrid}>
          {item.town ? <div><dt>Localidad</dt><dd>{item.town}</dd></div> : null}
          {item.location ? <div><dt>Zona</dt><dd>{item.location}</dd></div> : null}
          {item.address ? <div><dt>Referencia</dt><dd>{item.address}</dd></div> : null}
        </dl> : null}

        {externalUrl ? <div className={styles.actions}>
          <a className={styles.primaryLink} href={externalUrl} target="_blank" rel="noopener noreferrer">{item.ctaLabel || 'Web oficial / más información'} ↗</a>
        </div> : null}
      </div>
    </article>

    <section className={styles.townHub} aria-labelledby="town-hub-title">
      <div className={styles.sectionHeading}>
        <span>TODO EN UN MISMO PUEBLO</span>
        <h2 id="town-hub-title">Descubre {item.title}</h2>
        <p>Esta ficha funciona como puerta de entrada al resto de Mágina Olivo. Cada bloque reutiliza su módulo original para evitar información duplicada o desactualizada.</p>
      </div>
      <div className={styles.moduleGrid}>
        {townModules.map((module) => <Link key={module.href} className={styles.moduleCard} href={module.href}>
          <span className={styles.moduleIcon} aria-hidden="true">{module.icon}</span>
          <span className={styles.moduleCopy}>
            <strong>{module.title}</strong>
            <small>{module.description}</small>
          </span>
          <span className={styles.moduleArrow} aria-hidden="true">→</span>
        </Link>)}
      </div>
    </section>
  </>;
}

export function PublicPlacesPage() {
  const params = useSearchParams();
  const slug = params.get('slug')?.trim() || null;
  const [items, setItems] = useState<PublicPlace[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    loadPublicPlaces()
      .then((rows) => { if (!cancelled) setItems(rows); })
      .catch((cause) => {
        console.error('Unable to load public places', cause);
        if (!cancelled) {
          setItems([]);
          setError(true);
        }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const selected = useMemo(
    () => slug ? items.find((item) => item.slug === slug) ?? null : null,
    [items, slug],
  );

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es');
    return needle ? items.filter((item) => searchableText(item).includes(needle)) : items;
  }, [items, query]);

  if (loading) return <main className={styles.page}>
    <section className={styles.stateCard} aria-live="polite"><strong>Cargando pueblos de Sierra Mágina…</strong><p>Consultando las fichas públicas mantenidas por Mágina Olivo.</p></section>
  </main>;

  if (error) return <main className={styles.page}>
    <header className={styles.header}><span>SIERRA MÁGINA</span><h1>Pueblos de Mágina</h1></header>
    <section className={styles.stateCard} role="alert"><strong>Las fichas territoriales no están disponibles ahora</strong><p>No mostramos pueblos ni descripciones inventadas. Vuelve a intentarlo cuando el servicio público esté disponible.</p><button type="button" onClick={() => window.location.reload()}>Reintentar</button></section>
  </main>;

  if (slug && !selected) return <main className={styles.page}>
    <header className={styles.header}><span>SIERRA MÁGINA</span><h1>Pueblos de Mágina</h1></header>
    <section className={styles.stateCard}><strong>No encontramos este pueblo o lugar</strong><p>La ficha puede haber sido retirada o el enlace haber cambiado.</p><Link href="/pueblos">Volver a pueblos</Link></section>
  </main>;

  if (selected) return <main className={styles.page}><PlaceDetail item={selected} /></main>;

  return <main className={styles.page}>
    <header className={styles.header}>
      <span>SIERRA MÁGINA · TERRITORIO</span>
      <h1>Pueblos de Mágina</h1>
      <p>Descubre cada municipio desde una única ficha territorial y entra desde ella a rutas, agenda, noticias, empresas, almazaras y recursos municipales de Mágina Olivo.</p>
    </header>

    <section className={styles.toolbar} aria-label="Buscar pueblos y lugares">
      <label htmlFor="place-search">Buscar por nombre, localidad o zona</label>
      <div className={styles.searchRow}>
        <input id="place-search" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Ej. Bedmar, Jimena…" />
        {query ? <button type="button" onClick={() => setQuery('')}>Limpiar</button> : null}
      </div>
      <small>{filtered.length} {filtered.length === 1 ? 'resultado' : 'resultados'}</small>
    </section>

    {!items.length ? <section className={styles.stateCard}><strong>Todavía no hay fichas publicadas</strong><p>Los pueblos y lugares aparecerán aquí cuando el equipo publique sus fichas desde Administración.</p></section> : null}
    {items.length > 0 && !filtered.length ? <section className={styles.stateCard}><strong>Sin coincidencias</strong><p>Prueba con otro nombre, localidad o zona.</p><button type="button" onClick={() => setQuery('')}>Ver todos</button></section> : null}

    <section className={styles.grid} aria-label="Pueblos y lugares publicados">
      {filtered.map((item) => <PlaceCard key={item.id} item={item} />)}
    </section>
  </main>;
}
