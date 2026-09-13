'use client';

import Link from 'next/link';
import { publicRouteMediaUrl, type PublicRouteDetail, type PublicRoutePoint } from '../../../lib/public-routes-source';
import styles from '../routes-public.module.css';

function km(value: number | null) { return value == null ? '—' : `${(value / 1000).toFixed(1)} km`; }
function minutes(value: number | null) { if (value == null) return '—'; const h = Math.floor(value / 60); const m = value % 60; return h ? `${h} h${m ? ` ${m} min` : ''}` : `${m} min`; }
function label(value: string | null) { return value ? value.replaceAll('_', ' ') : 'Sin dato verificado'; }

function focusPoint(point: PublicRoutePoint) {
  window.dispatchEvent(new CustomEvent('magina:route-elevation-focus', { detail: {
    latitude: Number(point.latitude), longitude: Number(point.longitude), distance_m: Number(point.distance_m ?? 0),
    elevation_m: Number(point.elevation_m ?? 0), grade_percent: null,
  } }));
}

function focusDistance(detail: PublicRouteDetail, distance: number | null) {
  if (distance == null || !detail.elevation.length) return;
  let best = detail.elevation[0];
  let delta = Number.POSITIVE_INFINITY;
  for (const sample of detail.elevation) {
    if (sample.latitude == null || sample.longitude == null) continue;
    const next = Math.abs(Number(sample.distance_m) - distance);
    if (next < delta) { delta = next; best = sample; }
  }
  if (best?.latitude == null || best?.longitude == null) return;
  window.dispatchEvent(new CustomEvent('magina:route-elevation-focus', { detail: {
    latitude: Number(best.latitude), longitude: Number(best.longitude), distance_m: Number(best.distance_m),
    elevation_m: Number(best.elevation_m), grade_percent: best.grade_percent == null ? null : Number(best.grade_percent),
  } }));
}

export function RouteExperience({ detail }: { detail: PublicRouteDetail }) {
  const route = detail.route;
  const media = detail.media.filter((item) => item.kind !== 'hero_image' && item.kind !== 'thumbnail').slice(0, 12);
  const seasons = Array.isArray(route.recommended_seasons) ? route.recommended_seasons : [];

  return <>
    <section className={styles.communitySection}>
      <div className={styles.communityHeader}><div><span className={styles.eyebrow}>Antes de salir</span><h2>Prepara la ruta</h2></div><p>Datos editoriales y técnicos publicados para esta ruta. No sustituyen avisos oficiales ni tu propia valoración de seguridad.</p></div>
      <div className={styles.conditionGrid}>
        <article className={styles.conditionCard}><strong>Dificultad</strong><p>{label(route.difficulty)}</p></article>
        <article className={styles.conditionCard}><strong>Sombra</strong><p>{label(route.shade_level)}</p></article>
        <article className={styles.conditionCard}><strong>Cobertura móvil</strong><p>{label(route.mobile_coverage)}</p></article>
        <article className={styles.conditionCard}><strong>Agua</strong><p>{route.water_notes ?? 'Sin información verificada sobre puntos de agua.'}</p></article>
        <article className={styles.conditionCard}><strong>Temporadas</strong><p>{seasons.length ? seasons.join(' · ') : 'Sin temporada recomendada publicada.'}</p></article>
        <article className={styles.conditionCard}><strong>Restricciones</strong><p>{route.restrictions ?? 'Sin restricciones adicionales publicadas en esta ficha.'}</p></article>
      </div>
    </section>

    <section className={styles.communitySection}>
      <div className={styles.communityHeader}><div><span className={styles.eyebrow}>Paso a paso</span><h2>Itinerario por tramos</h2></div><p>{detail.segments.length ? `${detail.segments.length} tramos editoriales` : 'Aún no hay tramos editoriales publicados.'}</p></div>
      <div className={styles.reviewGrid}>
        {detail.segments.length ? detail.segments.map((segment, index) => <article className={styles.reviewCard} key={segment.id}>
          <div className={styles.reviewMeta}><strong>Tramo {index + 1}</strong><span>{segment.difficulty ? label(segment.difficulty) : ''}</span></div>
          <h3>{segment.title}</h3>
          {segment.description ? <p>{segment.description}</p> : null}
          <small>{km(segment.start_distance_m)} → {km(segment.end_distance_m)}{segment.duration_minutes ? ` · ${minutes(segment.duration_minutes)}` : ''}</small>
          {segment.start_distance_m != null ? <div className={styles.actionRow} style={{ marginTop: 12 }}><button className={styles.primaryAction} type="button" onClick={() => focusDistance(detail, segment.start_distance_m)}>Ver inicio en mapa</button></div> : null}
        </article>) : <p>El track está disponible aunque todavía no se haya redactado un itinerario por tramos.</p>}
      </div>
    </section>

    <section className={styles.communitySection}>
      <div className={styles.communityHeader}><div><span className={styles.eyebrow}>Qué encontrarás</span><h2>Puntos destacados</h2></div><p>Miradores, fuentes, patrimonio, áreas recreativas y otros POI vinculados al recorrido.</p></div>
      <div className={styles.conditionGrid}>
        {detail.points.length ? detail.points.map((point) => <article className={styles.conditionCard} key={point.id}>
          <strong>{point.name}</strong><span>{label(point.kind)}{point.distance_m != null ? ` · ${km(point.distance_m)}` : ''}</span>
          {point.description ? <p>{point.description}</p> : null}
          {point.safety_note ? <p><b>Atención:</b> {point.safety_note}</p> : null}
          <button className={styles.primaryAction} type="button" onClick={() => focusPoint(point)}>Ver en mapa</button>
        </article>) : <p>Aún no hay puntos destacados publicados.</p>}
      </div>
    </section>

    {media.length ? <section className={styles.communitySection}>
      <div className={styles.communityHeader}><div><span className={styles.eyebrow}>Multimedia</span><h2>La ruta en imágenes y vídeo</h2></div><p>El origen del material se mantiene visible; el contenido generado con IA debe declararse como tal.</p></div>
      <div className={styles.communityPhotos}>
        {media.map((item) => <figure key={item.id}>
          {item.kind.includes('video') ? <video controls preload="metadata" poster={item.poster_url ? publicRouteMediaUrl(item.poster_url) : undefined} style={{ width: '100%', aspectRatio: '4/3', objectFit: 'cover' }}><source src={publicRouteMediaUrl(item.url)} /></video> : <img loading="lazy" src={publicRouteMediaUrl(item.url)} alt={item.alt_text ?? item.caption ?? 'Imagen de la ruta'} />}
          <figcaption>{item.caption ?? item.credit ?? 'Contenido de la ruta'}{item.ai_generated || item.origin === 'ai_generated' ? ` · ${item.ai_disclosure ?? 'Recreación visual generada con IA'}` : ''}</figcaption>
        </figure>)}
      </div>
    </section> : null}

    <section className={styles.communitySection}>
      <div className={styles.communityHeader}><div><span className={styles.eyebrow}>Descubre más</span><h2>Rutas relacionadas</h2></div><p>Priorizadas por municipio y tipología entre rutas publicadas con track validado.</p></div>
      <div className={styles.reviewGrid}>
        {detail.related.length ? detail.related.map((related) => <article className={styles.reviewCard} key={related.id}>
          <div className={styles.reviewMeta}><strong>{related.municipality_name ?? 'Sierra Mágina'}</strong><span>{label(related.route_type)}</span></div>
          <h3>{related.name}</h3>
          {related.short_description ? <p>{related.short_description}</p> : null}
          <small>{km(related.distance_m)} · {minutes(related.duration_minutes)}{related.elevation_gain_m != null ? ` · +${related.elevation_gain_m} m` : ''}</small>
          <div className={styles.actionRow} style={{ marginTop: 12 }}><Link className={styles.primaryAction} href={`/rutas/detalle?slug=${encodeURIComponent(related.slug)}`}>Ver ruta</Link></div>
        </article>) : <p>No hay otras rutas publicadas relacionadas todavía.</p>}
      </div>
    </section>
  </>;
}
