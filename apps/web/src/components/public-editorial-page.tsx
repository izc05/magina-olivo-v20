'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { BottomNav } from './bottom-nav';
import { Topbar } from './topbar';
import { editorialDetails, loadPublicEditorial, type PublicEditorialEntry, type PublicEditorialType } from '../lib/public-editorial-source';
import styles from './public-editorial.module.css';

type Props = {
  type: PublicEditorialType;
};

function routeFor(type: PublicEditorialType) {
  return type === 'news' ? '/noticias' : '/eventos';
}

function formatDate(value: string | null | undefined) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat('es-ES', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function safeExternalUrl(value: string | null) {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || url.protocol === 'http:' ? value : null;
  } catch {
    return null;
  }
}

function copyFor(type: PublicEditorialType) {
  if (type === 'news') {
    return {
      eyebrow: 'ACTUALIDAD DE SIERRA MÁGINA',
      title: 'Noticias de Mágina',
      intro: 'Campo, pueblos, cooperativas y vida local. Solo contenido publicado desde la administración de Mágina Olivo.',
      search: 'Buscar noticias…',
      empty: 'Todavía no hay noticias publicadas.',
      icon: '📰',
    };
  }
  return {
    eyebrow: 'AGENDA DEL TERRITORIO',
    title: 'Eventos en Mágina',
    intro: 'Ferias, jornadas, encuentros, cultura y actividades del territorio reunidas en una agenda sencilla.',
    search: 'Buscar eventos…',
    empty: 'Todavía no hay eventos publicados.',
    icon: '📅',
  };
}

function searchableText(entry: PublicEditorialEntry) {
  const details = editorialDetails(entry);
  return [entry.title, entry.summary ?? '', details.body, details.location, details.town, details.address]
    .join(' ')
    .toLocaleLowerCase('es');
}

function Card({ entry, type }: { entry: PublicEditorialEntry; type: PublicEditorialType }) {
  const details = editorialDetails(entry);
  const date = type === 'event'
    ? formatDate(details.eventStart)
    : formatDate(entry.published_at);
  const location = details.town || details.location;
  const href = `${routeFor(type)}?slug=${encodeURIComponent(entry.slug)}`;

  return <article className={styles.card}>
    <Link className={styles.cardLink} href={href}>
      {entry.media_url
        ? <img className={styles.media} src={entry.media_url} alt="" loading="lazy" />
        : <div className={styles.mediaPlaceholder} aria-hidden>{type === 'news' ? '📰' : '📅'}</div>}
      <div className={styles.cardBody}>
        <div className={styles.meta}>
          {entry.featured ? <span className={styles.badge}>Destacado</span> : null}
          {date ? <span>{date}</span> : null}
          {location ? <span>· {location}</span> : null}
        </div>
        <h2>{entry.title}</h2>
        {entry.summary ? <p>{entry.summary}</p> : null}
      </div>
    </Link>
  </article>;
}

function Detail({ entry, type }: { entry: PublicEditorialEntry; type: PublicEditorialType }) {
  const details = editorialDetails(entry);
  const eventStart = type === 'event' ? formatDate(details.eventStart) : null;
  const eventEnd = type === 'event' ? formatDate(details.eventEnd) : null;
  const published = type === 'news' ? formatDate(entry.published_at) : null;
  const externalUrl = safeExternalUrl(entry.external_url);
  const paragraphs = details.body.split(/\n{2,}/).map((paragraph) => paragraph.trim()).filter(Boolean);

  return <article className={styles.detail}>
    {entry.media_url ? <img className={styles.detailMedia} src={entry.media_url} alt="" /> : null}
    <div className={styles.detailBody}>
      <Link className={styles.back} href={routeFor(type)}>← Volver a {type === 'news' ? 'Noticias' : 'Eventos'}</Link>
      <div className={styles.meta}>
        {entry.featured ? <span className={styles.badge}>Destacado</span> : null}
        {published ? <span>{published}</span> : null}
      </div>
      <h1>{entry.title}</h1>
      {entry.summary ? <p className={styles.summary}>{entry.summary}</p> : null}

      {type === 'event' && (eventStart || eventEnd || details.location || details.town || details.address) ? <div className={styles.infoBox}>
        {eventStart ? <strong>{eventEnd && eventEnd !== eventStart ? `${eventStart} → ${eventEnd}` : eventStart}</strong> : null}
        {details.location ? <span>{details.location}</span> : null}
        {details.town ? <span>{details.town}</span> : null}
        {details.address ? <span>{details.address}</span> : null}
      </div> : null}

      {paragraphs.length ? <div className={styles.bodyCopy}>{paragraphs.map((paragraph, index) => <p key={`${entry.id}-${index}`}>{paragraph}</p>)}</div> : null}
      {externalUrl ? <a className={styles.external} href={externalUrl} target="_blank" rel="noreferrer">Más información ↗</a> : null}
    </div>
  </article>;
}

export function PublicEditorialPage({ type }: Props) {
  const copy = copyFor(type);
  const [entries, setEntries] = useState<PublicEditorialEntry[]>([]);
  const [query, setQuery] = useState('');
  const [slug, setSlug] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    setSlug(params.get('slug'));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    void loadPublicEditorial(type)
      .then((items) => {
        if (!cancelled) setEntries(items);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [type, reloadKey]);

  const selected = useMemo(() => slug ? entries.find((entry) => entry.slug === slug) ?? null : null, [entries, slug]);
  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('es');
    if (!normalized) return entries;
    return entries.filter((entry) => searchableText(entry).includes(normalized));
  }, [entries, query]);

  return <main className={styles.shell}>
    <Topbar />
    <div className={styles.page}>
      <section className={styles.hero}>
        <span className={styles.eyebrow}>{copy.eyebrow}</span>
        <h1>{copy.title}</h1>
        <p>{copy.intro}</p>
      </section>

      {loading ? <div className={styles.state}><strong>Cargando contenido…</strong><span>Consultando la publicación más reciente.</span></div> : null}

      {!loading && error ? <div className={styles.state} role="status"><strong>No se ha podido cargar ahora mismo.</strong><span>La página no mostrará contenido inventado ni antiguo como si fuera actual.</span><button className={styles.retry} type="button" onClick={() => setReloadKey((value) => value + 1)}>Reintentar</button></div> : null}

      {!loading && !error && slug ? (
        selected ? <Detail entry={selected} type={type} /> : <div className={styles.state}><strong>Este contenido no está disponible.</strong><span>Puede haberse retirado, archivado o haber cambiado de enlace.</span><br /><Link className={styles.back} href={routeFor(type)}>Volver al listado</Link></div>
      ) : null}

      {!loading && !error && !slug ? <>
        <div className={styles.toolbar}>
          <input className={styles.search} type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={copy.search} aria-label={copy.search} />
          <span className={styles.count}>{filtered.length} {filtered.length === 1 ? 'resultado' : 'resultados'}</span>
        </div>
        {filtered.length ? <section className={styles.grid} aria-label={copy.title}>{filtered.map((entry) => <Card key={entry.id} entry={entry} type={type} />)}</section> : <div className={styles.state}><strong>{query ? 'No hay coincidencias.' : copy.empty}</strong><span>{query ? 'Prueba con otro término.' : 'Cuando se publique contenido desde Administración aparecerá aquí automáticamente.'}</span></div>}
      </> : null}
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
