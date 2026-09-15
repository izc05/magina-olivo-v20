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
          <a className={styles.primaryLink} href={externalUrl} target="_blank" rel="noopener noreferrer">{item.ctaLabel || 'Más información'} ↗</a>
        </div> : null}
      </div>
    </article>
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
    <header className={styles.header}><span>SIERRA MÁGINA</span><h1>Pueblos y lugares</h1></header>
    <section className={styles.stateCard} role="alert"><strong>Las fichas territoriales no están disponibles ahora</strong><p>No mostramos pueblos ni descripciones inventadas. Vuelve a intentarlo cuando el servicio público esté disponible.</p><button type="button" onClick={() => window.location.reload()}>Reintentar</button></section>
  </main>;

  if (slug && !selected) return <main className={styles.page}>
    <header className={styles.header}><span>SIERRA MÁGINA</span><h1>Pueblos y lugares</h1></header>
    <section className={styles.stateCard}><strong>No encontramos este pueblo o lugar</strong><p>La ficha puede haber sido retirada o el enlace haber cambiado.</p><Link href="/pueblos">Volver a pueblos</Link></section>
  </main>;

  if (selected) return <main className={styles.page}><PlaceDetail item={selected} /></main>;

  return <main className={styles.page}>
    <header className={styles.header}>
      <span>SIERRA MÁGINA · TERRITORIO</span>
      <h1>Pueblos y lugares</h1>
      <p>Descubre las localidades y rincones publicados desde Mágina Olivo. Las fichas se muestran tal como han sido revisadas y publicadas desde Administración.</p>
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
