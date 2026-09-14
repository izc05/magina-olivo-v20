'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadFollowedTowns, type FollowedTown } from '@/lib/my-towns-source';
import { loadTerritorialFeed, type TerritorialFeedItem } from '@/lib/territorial-feed-source';
import { findMaginaTown, townModuleHref } from '@/lib/towns';
import styles from './home-my-towns.module.css';

function feedKindLabel(item: TerritorialFeedItem) {
  if (item.kind === 'notice') {
    if (item.noticePriority === 'urgent') return 'Aviso urgente';
    if (item.noticePriority === 'important') return 'Aviso importante';
    return 'Aviso municipal';
  }
  if (item.kind === 'news') return 'Noticia';
  if (item.kind === 'event') return 'Evento';
  return 'Empresa';
}

function feedDate(value: string | null) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short' }).format(date);
}

export function HomeMyTowns() {
  const { status, preferences, profile } = useAuth();
  const [towns, setTowns] = useState<FollowedTown[]>([]);
  const [feed, setFeed] = useState<TerritorialFeedItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [feedLoading, setFeedLoading] = useState(false);
  const [feedError, setFeedError] = useState(false);

  const primaryTown = useMemo(
    () => findMaginaTown(preferences?.preferred_municipality ?? profile?.municipality),
    [preferences?.preferred_municipality, profile?.municipality],
  );

  useEffect(() => {
    if (status !== 'authenticated') {
      setTowns([]);
      setFeed([]);
      setLoading(false);
      setError(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(false);
    loadFollowedTowns()
      .then((rows) => {
        if (!cancelled) setTowns(rows);
      })
      .catch((cause) => {
        console.warn('Unable to load followed towns on home', cause);
        if (!cancelled) {
          setTowns([]);
          setError(true);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [status]);

  useEffect(() => {
    if (status !== 'authenticated' || towns.length === 0) {
      setFeed([]);
      setFeedLoading(false);
      setFeedError(false);
      return;
    }

    let cancelled = false;
    setFeedLoading(true);
    setFeedError(false);
    loadTerritorialFeed(towns.map((town) => town.slug), primaryTown?.slug ?? null)
      .then((items) => {
        if (!cancelled) setFeed(items);
      })
      .catch((cause) => {
        console.warn('Unable to load territorial feed on home', cause);
        if (!cancelled) {
          setFeed([]);
          setFeedError(true);
        }
      })
      .finally(() => {
        if (!cancelled) setFeedLoading(false);
      });

    return () => { cancelled = true; };
  }, [status, towns, primaryTown?.slug]);

  if (status !== 'authenticated') return null;

  const visibleTowns = towns.slice(0, 4);
  const remaining = Math.max(0, towns.length - visibleTowns.length);

  return <section className={`section card ${styles.card}`} aria-labelledby="home-my-towns-title">
    <div className={styles.header}>
      <div>
        <span>MI MÁGINA</span>
        <h2 id="home-my-towns-title">Tus pueblos</h2>
        <p>Acceso rápido y actualidad local de los municipios que has decidido seguir.</p>
      </div>
      <Link href="/mis-pueblos">Gestionar →</Link>
    </div>

    {loading ? <p className={styles.state}>Cargando tus pueblos…</p> : null}
    {error ? <p className={styles.state}>No hemos podido cargar tus pueblos ahora mismo.</p> : null}

    {!loading && !error && towns.length === 0 ? <div className={styles.empty}>
      <strong>Personaliza tu Mágina</strong>
      <p>Elige un pueblo principal y sigue otros municipios para tenerlos siempre a mano.</p>
      <Link href="/mis-pueblos">Elegir mis pueblos</Link>
    </div> : null}

    {!loading && !error && visibleTowns.length > 0 ? <div className={styles.grid}>
      {visibleTowns.map((town) => {
        const canonical = findMaginaTown(town.slug) ?? findMaginaTown(town.name);
        const isPrimary = primaryTown?.slug === town.slug;
        return <article className={styles.town} key={town.id}>
          <div className={styles.meta}>
            <span>{isPrimary ? 'Principal' : 'Siguiendo'}</span>
            <small>{canonical?.inNaturalPark ? 'Parque Natural' : 'Sierra Mágina'}</small>
          </div>
          <h3>{town.name}</h3>
          {canonical ? <div className={styles.actions}>
            <Link href={townModuleHref('/noticias', canonical)}>Noticias</Link>
            <Link href={townModuleHref('/eventos', canonical)}>Eventos</Link>
            <Link href={townModuleHref('/empresas', canonical)}>Empresas</Link>
            <Link href={townModuleHref('/explorar', canonical)}>Explorar</Link>
          </div> : null}
        </article>;
      })}
    </div> : null}

    {remaining > 0 ? <Link className={styles.more} href="/mis-pueblos">+{remaining} pueblo{remaining === 1 ? '' : 's'} más</Link> : null}

    {!loading && !error && towns.length > 0 ? <div className={styles.feedSection}>
      <div className={styles.feedHeader}>
        <div>
          <span>PARA TI EN SIERRA MÁGINA</span>
          <h3>Lo más relevante para ti</h3>
        </div>
        <Link href="/noticias">Ver actualidad →</Link>
      </div>

      {feedLoading ? <p className={styles.state}>Priorizando avisos, noticias, eventos y empresas de tus pueblos…</p> : null}
      {feedError ? <p className={styles.state}>Parte de la actualidad local no está disponible ahora mismo.</p> : null}
      {!feedLoading && !feedError && feed.length === 0 ? <div className={styles.feedEmpty}>Todavía no hay contenido publicado asociado a tus pueblos.</div> : null}

      {!feedLoading && feed.length > 0 ? <div className={styles.feedList}>
        {feed.map((item) => {
          const isPrimary = item.townSlug === primaryTown?.slug;
          const date = feedDate(item.timestamp);
          return <Link className={styles.feedItem} href={item.href} key={item.id}>
            <div className={styles.feedMeta}>
              <span>{feedKindLabel(item)}{item.featured ? ' · Destacado' : ''}</span>
              <small>{item.townName}{isPrimary ? ' · Principal' : ''}{date ? ` · ${date}` : ''}</small>
            </div>
            <strong>{item.title}</strong>
            {item.summary ? <p>{item.summary}</p> : null}
          </Link>;
        })}
      </div> : null}
    </div> : null}
  </section>;
}
