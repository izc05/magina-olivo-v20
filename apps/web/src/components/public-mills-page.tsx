'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { loadPublicMills, type PublicMill } from '@/lib/public-mills-source';
import styles from './public-mills.module.css';

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
  if (value.startsWith('/')) return value;
  return safeExternalUrl(value);
}

function phoneHref(phone: string | null): string | null {
  if (!phone) return null;
  const normalized = phone.replace(/[^+\d]/g, '');
  return normalized.length >= 6 ? `tel:${normalized}` : null;
}

function searchableText(item: PublicMill) {
  return [item.title, item.summary, item.town, item.location, item.address].filter(Boolean).join(' ').toLocaleLowerCase('es');
}

function MillCard({ item, basePath }: { item: PublicMill; basePath: string }) {
  const image = safeMediaUrl(item.mediaUrl);
  return <article className={styles.card}>
    {image ? <img className={styles.cardImage} src={image} alt="" loading="lazy" /> : <div className={styles.cardPlaceholder} aria-hidden="true">🫒</div>}
    <div className={styles.cardBody}>
      <div className={styles.metaRow}>
        <span>{item.featured ? 'Destacada' : 'Cooperativa / almazara'}</span>
        {item.town ? <span>{item.town}</span> : null}
      </div>
      <h2>{item.title}</h2>
      {item.summary ? <p>{item.summary}</p> : null}
      <div className={styles.locationLine}>{[item.location, item.address].filter(Boolean).join(' · ') || 'Información de ubicación pendiente'}</div>
      <Link className={styles.primaryLink} href={`${basePath}?slug=${encodeURIComponent(item.slug)}`}>Ver ficha →</Link>
    </div>
  </article>;
}

function MillDetail({ item, basePath }: { item: PublicMill; basePath: string }) {
  const image = safeMediaUrl(item.mediaUrl);
  const externalUrl = safeExternalUrl(item.externalUrl);
  const callHref = phoneHref(item.phone);
  return <>
    <Link className={styles.backLink} href={basePath}>← Cooperativas y almazaras</Link>
    <article className={styles.detail}>
      {image ? <img className={styles.heroImage} src={image} alt="" /> : <div className={styles.heroPlaceholder} aria-hidden="true">🫒</div>}
      <div className={styles.detailBody}>
        <div className={styles.metaRow}><span>Cooperativa / almazara</span>{item.town ? <span>{item.town}</span> : null}</div>
        <h1>{item.title}</h1>
        {item.summary ? <p className={styles.lead}>{item.summary}</p> : null}
        {item.body ? <p className={styles.bodyText}>{item.body}</p> : null}

        <dl className={styles.contactGrid}>
          {item.town ? <div><dt>Pueblo</dt><dd>{item.town}</dd></div> : null}
          {item.location ? <div><dt>Ubicación</dt><dd>{item.location}</dd></div> : null}
          {item.address ? <div><dt>Dirección</dt><dd>{item.address}</dd></div> : null}
          {item.phone ? <div><dt>Teléfono</dt><dd>{item.phone}</dd></div> : null}
        </dl>

        <div className={styles.actions}>
          {callHref ? <a className={styles.primaryLink} href={callHref}>Llamar</a> : null}
          {externalUrl ? <a className={styles.secondaryLink} href={externalUrl} target="_blank" rel="noopener noreferrer">{item.ctaLabel || 'Abrir enlace'} ↗</a> : null}
        </div>
      </div>
    </article>
  </>;
}

export function PublicMillsPage({ basePath = '/cooperativas' }: { basePath?: '/cooperativas' | '/almazaras' }) {
  const params = useSearchParams();
  const slug = params.get('slug')?.trim() || null;
  const [items, setItems] = useState<PublicMill[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    loadPublicMills()
      .then((rows) => { if (!cancelled) setItems(rows); })
      .catch((cause) => {
        console.error('Unable to load public mills', cause);
        if (!cancelled) { setItems([]); setError(true); }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const selected = useMemo(() => slug ? items.find((item) => item.slug === slug) ?? null : null, [items, slug]);
  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es');
    return needle ? items.filter((item) => searchableText(item).includes(needle)) : items;
  }, [items, query]);

  if (loading) return <main className={styles.page}><section className={styles.stateCard} aria-live="polite"><strong>Cargando cooperativas y almazaras…</strong><p>Consultando el directorio público de Mágina.</p></section></main>;

  if (error) return <main className={styles.page}>
    <header className={styles.header}><span>SIERRA MÁGINA</span><h1>Cooperativas y almazaras</h1></header>
    <section className={styles.stateCard} role="alert"><strong>Directorio no disponible ahora</strong><p>No mostramos establecimientos inventados. Vuelve a intentarlo cuando el servicio público esté disponible.</p><button type="button" onClick={() => window.location.reload()}>Reintentar</button></section>
  </main>;

  if (slug && !selected) return <main className={styles.page}>
    <header className={styles.header}><span>SIERRA MÁGINA</span><h1>Cooperativas y almazaras</h1></header>
    <section className={styles.stateCard}><strong>No encontramos esta ficha</strong><p>Puede haber sido retirada o su enlace haber cambiado.</p><Link href={basePath}>Volver al directorio</Link></section>
  </main>;

  if (selected) return <main className={styles.page}><MillDetail item={selected} basePath={basePath} /></main>;

  return <main className={styles.page}>
    <header className={styles.header}>
      <span>SIERRA MÁGINA · ACEITE</span>
      <h1>Cooperativas y almazaras</h1>
      <p>Directorio público de entidades publicadas y mantenidas desde Mágina Olivo. Consulta ubicación y datos de contacto sin necesidad de iniciar sesión.</p>
    </header>

    <section className={styles.toolbar} aria-label="Buscar en el directorio">
      <label htmlFor="mill-search">Buscar por nombre, pueblo o dirección</label>
      <div className={styles.searchRow}>
        <input id="mill-search" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Ej. Bedmar, cooperativa…" />
        {query ? <button type="button" onClick={() => setQuery('')}>Limpiar</button> : null}
      </div>
      <small>{filtered.length} {filtered.length === 1 ? 'resultado' : 'resultados'}</small>
    </section>

    {!items.length ? <section className={styles.stateCard}><strong>Todavía no hay entidades publicadas</strong><p>El directorio aparecerá aquí cuando el equipo publique cooperativas o almazaras desde Administración.</p></section> : null}
    {items.length && !filtered.length ? <section className={styles.stateCard}><strong>Sin coincidencias</strong><p>Prueba con otro nombre o pueblo.</p><button type="button" onClick={() => setQuery('')}>Ver todas</button></section> : null}

    <section className={styles.grid} aria-label="Cooperativas y almazaras publicadas">
      {filtered.map((item) => <MillCard key={item.id} item={item} basePath={basePath} />)}
    </section>
  </main>;
}
