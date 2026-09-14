'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { loadFollowedTowns, type FollowedTown } from '@/lib/my-towns-source';
import { findMaginaTown, townModuleHref } from '@/lib/towns';
import styles from './home-my-towns.module.css';

export function HomeMyTowns() {
  const { status, preferences, profile } = useAuth();
  const [towns, setTowns] = useState<FollowedTown[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const primaryTown = useMemo(
    () => findMaginaTown(preferences?.preferred_municipality ?? profile?.municipality),
    [preferences?.preferred_municipality, profile?.municipality],
  );

  useEffect(() => {
    if (status !== 'authenticated') {
      setTowns([]);
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

  if (status !== 'authenticated') return null;

  const visibleTowns = towns.slice(0, 4);
  const remaining = Math.max(0, towns.length - visibleTowns.length);

  return <section className={`section card ${styles.card}`} aria-labelledby="home-my-towns-title">
    <div className={styles.header}>
      <div>
        <span>MI MÁGINA</span>
        <h2 id="home-my-towns-title">Tus pueblos</h2>
        <p>Acceso rápido a la información local que has decidido seguir.</p>
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
  </section>;
}
