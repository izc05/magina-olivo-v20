'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import {
  loadPublicAdventures,
  loadPublicRouteCommunity,
  publicRouteMediaUrl,
  type PublicAdventureSummary,
  type PublicRouteCommunity,
} from '../../lib/public-routes-source';
import styles from './adventure-community.module.css';

const conditionLabels: Record<string, string> = {
  clear: 'Ruta despejada',
  muddy: 'Barro',
  wet: 'Terreno mojado',
  snow: 'Nieve',
  ice: 'Hielo',
  blocked: 'Paso bloqueado',
  damaged: 'Sendero dañado',
  closed: 'Posible cierre',
  fire_risk: 'Riesgo de incendio',
  flooded: 'Inundado',
  other: 'Aviso reciente',
};

function stars(value: number) {
  const rounded = Math.max(0, Math.min(5, Math.round(value)));
  return `${'★'.repeat(rounded)}${'☆'.repeat(5 - rounded)}`;
}

export function AdventureCommunityShowcase() {
  const [featured, setFeatured] = useState<PublicAdventureSummary | null>(null);
  const [community, setCommunity] = useState<PublicRouteCommunity | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    loadPublicAdventures()
      .then(async (hub) => {
        if (cancelled) return;
        const next = hub.adventures[0] ?? null;
        setFeatured(next);
        if (!next) return;
        const value = await loadPublicRouteCommunity(next.slug);
        if (!cancelled) setCommunity(value);
      })
      .catch(() => {
        if (!cancelled) {
          setFeatured(null);
          setCommunity(null);
        }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const rating = useMemo(() => Number(community?.summary.rating_average ?? 0), [community]);
  const photos = community?.photos.slice(0, 3) ?? [];
  const reviews = community?.reviews.slice(0, 2) ?? [];
  const conditions = community?.conditions.slice(0, 2) ?? [];

  if (!loading && !featured) return null;

  return <section id="comunidad" className={styles.section} aria-labelledby="adventure-community-title">
    <div className={styles.heading}>
      <div>
        <span className={styles.eyebrow}>COMUNIDAD MÁGINA</span>
        <h2 id="adventure-community-title">Gente que explora, cuida y comparte</h2>
        <p>Experiencias, fotografías y avisos aprobados de la comunidad sobre rutas reales de Sierra Mágina.</p>
      </div>
      {featured ? <Link href={`/rutas/detalle?slug=${encodeURIComponent(featured.slug)}#comunidad`} className={styles.link}>Abrir comunidad de la ruta <span>→</span></Link> : null}
    </div>

    <div className={styles.layout}>
      <article className={styles.summaryCard}>
        <span className={styles.routeLabel}>RUTA DESTACADA</span>
        <h3>{featured?.title ?? community?.route.name ?? 'Sierra Mágina'}</h3>
        {loading ? <p>Cargando actividad real…</p> : <>
          <div className={styles.ratingRow}><strong>{rating.toFixed(1)}</strong><span>{stars(rating)}</span></div>
          <div className={styles.stats}>
            <div><strong>{community?.summary.review_count ?? 0}</strong><span>reseñas</span></div>
            <div><strong>{community?.photos.length ?? 0}</strong><span>fotos</span></div>
            <div><strong>{community?.conditions.length ?? 0}</strong><span>avisos</span></div>
          </div>
          <small>Solo contenido aprobado. Los avisos comunitarios nunca sustituyen cierres ni información oficial.</small>
        </>}
      </article>

      <div className={styles.contentColumn}>
        {photos.length > 0 ? <div className={styles.photoGrid} aria-label="Fotos recientes de la comunidad">
          {photos.map((photo) => <figure key={photo.id}>
            <img loading="lazy" src={publicRouteMediaUrl(photo.url)} alt={photo.caption ?? 'Foto aprobada de la comunidad'} />
            {photo.caption ? <figcaption>{photo.caption}</figcaption> : null}
          </figure>)}
        </div> : <div className={styles.empty}><span>▣</span><div><strong>Aún no hay fotografías publicadas</strong><p>La galería se llenará solo con aportaciones reales aprobadas.</p></div></div>}

        <div className={styles.feedGrid}>
          <article className={styles.feedCard}>
            <div className={styles.feedTitle}><span>◎</span><strong>Experiencias recientes</strong></div>
            {reviews.length > 0 ? reviews.map((review) => <div className={styles.feedItem} key={review.id}>
              <div className={styles.avatar}>{(review.display_name || 'M').slice(0, 1).toUpperCase()}</div>
              <div><strong>{review.display_name || 'Usuario de Mágina'}</strong><span>{stars(Number(review.rating))}</span>{review.body ? <p>{review.body}</p> : review.title ? <p>{review.title}</p> : null}</div>
            </div>) : <p className={styles.muted}>Todavía no hay experiencias aprobadas en esta ruta.</p>}
          </article>

          <article className={styles.feedCard}>
            <div className={styles.feedTitle}><span>!</span><strong>Estado del sendero</strong></div>
            {conditions.length > 0 ? conditions.map((condition) => <div className={styles.condition} key={condition.id}>
              <span className={styles.conditionDot} />
              <div><strong>{conditionLabels[condition.condition_kind] ?? condition.condition_kind}</strong><small>{new Date(condition.observed_at).toLocaleDateString('es-ES')}</small>{condition.note ? <p>{condition.note}</p> : null}</div>
            </div>) : <p className={styles.muted}>Sin avisos comunitarios recientes aprobados.</p>}
          </article>
        </div>
      </div>
    </div>
  </section>;
}
