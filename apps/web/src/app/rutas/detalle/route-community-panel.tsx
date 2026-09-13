'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../../../lib/api-client';
import {
  loadPublicRouteCommunity,
  publicRouteMediaUrl,
  routeGpxUrl,
  type PublicRouteCommunity,
} from '../../../lib/public-routes-source';
import styles from '../routes-public.module.css';

type Props = { routeId: string; slug: string };

type ReviewResponse = { review: { id: string }; moderation: 'pending' };
type ReserveResponse = {
  media: { id: string };
  upload: { uploadUrl: string; method: 'PUT'; headers: Record<string, string> };
};

const conditionLabels: Record<string, string> = {
  clear: 'Despejado / normal', muddy: 'Barro', wet: 'Mojado', snow: 'Nieve', ice: 'Hielo', blocked: 'Paso bloqueado',
  damaged: 'Sendero dañado', closed: 'Posible cierre', fire_risk: 'Riesgo de incendio', flooded: 'Inundado', other: 'Otro',
};

async function sha256(file: File) {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

function stars(value: number) {
  return `${'★'.repeat(Math.max(0, Math.min(5, Math.round(value))))}${'☆'.repeat(Math.max(0, 5 - Math.min(5, Math.round(value))))}`;
}

export function RouteCommunityPanel({ routeId, slug }: Props) {
  const [community, setCommunity] = useState<PublicRouteCommunity | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [rating, setRating] = useState(5);
  const [reviewBody, setReviewBody] = useState('');
  const [visitedOn, setVisitedOn] = useState('');
  const [photo, setPhoto] = useState<File | null>(null);
  const [conditionKind, setConditionKind] = useState('clear');
  const [conditionNote, setConditionNote] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = async () => {
    try {
      const data = await loadPublicRouteCommunity(slug);
      setCommunity(data); setError(false);
    } catch { setError(true); }
    finally { setLoading(false); }
  };

  useEffect(() => { void refresh(); }, [slug]);

  const ratingAverage = useMemo(() => Number(community?.summary.rating_average ?? 0), [community]);

  async function submitReview() {
    setBusy(true); setMessage(null);
    try {
      const response = await apiFetch<ReviewResponse>(`/api/v1/routes/${routeId}/review`, {
        method: 'PUT',
        body: JSON.stringify({ rating, body: reviewBody || null, visited_on: visitedOn || null, completed: true }),
      });
      if (photo) {
        const checksum = await sha256(photo);
        const reserved = await apiFetch<ReserveResponse>(`/api/v1/routes/${routeId}/reviews/${response.review.id}/photos/reserve`, {
          method: 'POST',
          body: JSON.stringify({
            original_filename: photo.name,
            mime_type: photo.type,
            byte_size: photo.size,
            sha256: checksum,
            captured_at: null,
          }),
        });
        const uploadResponse = await fetch(reserved.upload.uploadUrl, { method: reserved.upload.method, headers: reserved.upload.headers, body: photo });
        if (!uploadResponse.ok) throw new Error('photo_upload_failed');
        await apiFetch(`/api/v1/routes/${routeId}/reviews/${response.review.id}/photos/${reserved.media.id}/complete`, { method: 'POST' });
      }
      setReviewBody(''); setVisitedOn(''); setPhoto(null);
      setMessage('Gracias. Tu experiencia y las fotos quedan pendientes de revisión antes de publicarse.');
    } catch (cause) {
      const status = typeof cause === 'object' && cause && 'status' in cause ? Number((cause as { status?: number }).status) : 0;
      setMessage(status === 401 ? 'Necesitas iniciar sesión para publicar una experiencia.' : 'No se ha podido enviar la experiencia. Inténtalo de nuevo.');
    } finally { setBusy(false); }
  }

  async function submitCondition() {
    setBusy(true); setMessage(null);
    try {
      await apiFetch(`/api/v1/routes/${routeId}/conditions`, {
        method: 'POST',
        body: JSON.stringify({ condition_kind: conditionKind, severity: conditionKind === 'closed' || conditionKind === 'fire_risk' ? 'warning' : 'info', note: conditionNote || null, observed_at: new Date().toISOString() }),
      });
      setConditionNote('');
      setMessage('Aviso enviado. Se publicará cuando lo valide un moderador.');
    } catch (cause) {
      const status = typeof cause === 'object' && cause && 'status' in cause ? Number((cause as { status?: number }).status) : 0;
      setMessage(status === 401 ? 'Necesitas iniciar sesión para informar del estado del sendero.' : 'No se ha podido enviar el aviso.');
    } finally { setBusy(false); }
  }

  return <>
    <section className={styles.routeToolsGrid}>
      <article className={styles.toolCard}>
        <span className={styles.eyebrow}>Llévatela contigo</span>
        <h2>Ruta en tu móvil o GPS</h2>
        <p>Descarga el trazado validado en GPX y ábrelo en tu dispositivo o aplicación compatible.</p>
        <div className={styles.actionRow}>
          <a className={styles.primaryAction} href={routeGpxUrl(slug)}>Descargar GPX</a>
          <span className={styles.deviceList}>Garmin · Suunto · COROS · GPS</span>
        </div>
        <small>La descarga contiene el track real almacenado. La integración directa con Garmin Connect requerirá autorización del proveedor.</small>
      </article>

      <article className={styles.toolCard}>
        <span className={styles.eyebrow}>Comunidad</span>
        <h2>{loading ? 'Cargando experiencias…' : `${ratingAverage.toFixed(1)} / 5`}</h2>
        <p className={styles.ratingLine}>{stars(ratingAverage)} · {community?.summary.review_count ?? 0} valoraciones aprobadas</p>
        <p>Fotos, comentarios y estado reciente del sendero, siempre separados de la información oficial.</p>
      </article>
    </section>

    {community?.sponsorships.length ? <section className={styles.sponsorArea} aria-label="Patrocinadores de la ruta">
      {community.sponsorships.map((sponsor) => <article className={styles.sponsorCard} key={sponsor.id}>
        <span>{sponsor.disclosure || 'Patrocinado'}</span>
        <h3>{sponsor.headline ?? sponsor.sponsor_name}</h3>
        {sponsor.description ? <p>{sponsor.description}</p> : null}
        {sponsor.promo_code ? <strong>Código: {sponsor.promo_code}</strong> : null}
        {sponsor.cta_url ? <a href={sponsor.cta_url} target="_blank" rel="sponsored noreferrer">{sponsor.cta_label ?? 'Ver oferta'}</a> : null}
      </article>)}
    </section> : null}

    <section className={styles.communitySection}>
      <div className={styles.communityHeader}>
        <div><span className={styles.eyebrow}>Estado reciente</span><h2>¿Cómo está la ruta?</h2></div>
        <p>{community?.notices.community_conditions ?? 'Los avisos de la comunidad no sustituyen información oficial.'}</p>
      </div>
      {error ? <p>No se ha podido cargar la comunidad ahora mismo.</p> : null}
      <div className={styles.conditionGrid}>
        {community?.conditions.length ? community.conditions.map((condition) => <article className={`${styles.conditionCard} ${styles[`severity_${condition.severity}`] ?? ''}`} key={condition.id}>
          <strong>{conditionLabels[condition.condition_kind] ?? condition.condition_kind}</strong>
          <span>{new Date(condition.observed_at).toLocaleDateString('es-ES')}</span>
          {condition.note ? <p>{condition.note}</p> : null}
        </article>) : <article className={styles.conditionCard}><strong>Sin avisos recientes aprobados</strong><p>Esto no significa que no existan incidencias oficiales. Consulta siempre la información de seguridad de la ficha.</p></article>}
      </div>

      <div className={styles.contributionGrid}>
        <form className={styles.contributionCard} onSubmit={(event) => { event.preventDefault(); void submitReview(); }}>
          <h3>Cuenta tu experiencia</h3>
          <label>Valoración<select value={rating} onChange={(event) => setRating(Number(event.target.value))}>{[5,4,3,2,1].map((value) => <option key={value} value={value}>{value} estrellas</option>)}</select></label>
          <label>Fecha de la ruta<input type="date" value={visitedOn} onChange={(event) => setVisitedOn(event.target.value)} /></label>
          <label>Comentario<textarea value={reviewBody} onChange={(event) => setReviewBody(event.target.value)} maxLength={8000} placeholder="Estado, dificultad real, señalización, agua, sombra, consejos…" /></label>
          <label>Foto opcional<input type="file" accept="image/jpeg,image/png,image/webp,image/avif" onChange={(event) => setPhoto(event.target.files?.[0] ?? null)} /></label>
          <button disabled={busy} type="submit">{busy ? 'Enviando…' : 'Enviar para revisión'}</button>
          <small>Las aportaciones no aparecen automáticamente: pasan primero por moderación.</small>
        </form>

        <form className={styles.contributionCard} onSubmit={(event) => { event.preventDefault(); void submitCondition(); }}>
          <h3>Informar del estado</h3>
          <label>Situación<select value={conditionKind} onChange={(event) => setConditionKind(event.target.value)}>{Object.entries(conditionLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <label>Detalle<textarea value={conditionNote} onChange={(event) => setConditionNote(event.target.value)} maxLength={4000} placeholder="Ej.: árbol caído en el km 3,2; barro fuerte después del arroyo…" /></label>
          <button disabled={busy} type="submit">Enviar aviso</button>
          <small>Un aviso comunitario nunca se presenta como cierre oficial sin una fuente oficial.</small>
        </form>
      </div>
      {message ? <p className={styles.formMessage}>{message} {message.includes('iniciar sesión') ? <Link href="/login">Ir a acceso</Link> : null}</p> : null}
    </section>

    <section className={styles.communitySection}>
      <div className={styles.communityHeader}><div><span className={styles.eyebrow}>Galería real</span><h2>Fotos de la comunidad</h2></div><p>Solo imágenes aprobadas por moderación.</p></div>
      <div className={styles.communityPhotos}>
        {community?.photos.length ? community.photos.map((photo) => <figure key={photo.id}><img loading="lazy" src={publicRouteMediaUrl(photo.url)} alt={photo.caption ?? 'Foto aportada por la comunidad de esta ruta'} />{photo.caption ? <figcaption>{photo.caption}</figcaption> : null}</figure>) : <p>Aún no hay fotografías de usuarios publicadas. Puedes ser la primera persona en aportar una.</p>}
      </div>
    </section>

    <section className={styles.communitySection}>
      <div className={styles.communityHeader}><div><span className={styles.eyebrow}>Experiencias</span><h2>Lo que cuenta la gente</h2></div><p>{community?.summary.review_count ?? 0} reseñas aprobadas.</p></div>
      <div className={styles.reviewGrid}>
        {community?.reviews.length ? community.reviews.map((review) => <article className={styles.reviewCard} key={review.id}>
          <div className={styles.reviewMeta}><strong>{review.display_name || 'Usuario de Mágina'}</strong><span>{stars(review.rating)}</span></div>
          {review.title ? <h3>{review.title}</h3> : null}
          {review.body ? <p>{review.body}</p> : null}
          <small>{review.visited_on ? `Realizada: ${new Date(review.visited_on).toLocaleDateString('es-ES')}` : 'Fecha no indicada'}{review.difficulty_vote ? ` · Dificultad percibida: ${review.difficulty_vote}` : ''}</small>
        </article>) : <p>Aún no hay experiencias aprobadas para esta ruta.</p>}
      </div>
    </section>
  </>;
}