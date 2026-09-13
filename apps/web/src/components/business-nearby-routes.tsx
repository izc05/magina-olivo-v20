'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { apiFetch } from '@/lib/api-client';
import styles from './business-directory.module.css';

type NearbyRoute = {
  id: string;
  slug: string;
  name: string;
  routeType: string;
  difficulty: string | null;
  distanceMeters: number | null;
  durationMinutes: number | null;
  elevationGainMeters: number | null;
  shortDescription: string | null;
  territory: { municipalityName: string | null; placeName: string | null };
  heroUrl: string | null;
  businessDistanceMeters: number | null;
  distanceBasis: 'spatial' | 'same_place' | 'same_municipality';
};

type Payload = {
  routes: NearbyRoute[];
  meta: { radiusMeters: number | null; distanceNote: string };
};

function routeTypeLabel(value: string) {
  if (value === 'hiking') return 'Senderismo';
  if (value === 'mtb') return 'MTB';
  if (value === 'cycling') return 'Ciclismo';
  if (value === 'trail') return 'Trail';
  if (value === 'family') return 'Familiar';
  return 'Ruta';
}

function difficultyLabel(value: string | null) {
  if (value === 'easy') return 'Fácil';
  if (value === 'moderate') return 'Moderada';
  if (value === 'hard') return 'Difícil';
  if (value === 'very_hard') return 'Muy difícil';
  return null;
}

function proximityLabel(route: NearbyRoute) {
  if (route.distanceBasis === 'spatial' && route.businessDistanceMeters !== null) {
    if (route.businessDistanceMeters < 1_000) return `${Math.round(route.businessDistanceMeters)} m del trazado`;
    return `${(route.businessDistanceMeters / 1_000).toFixed(1)} km del trazado`;
  }
  if (route.distanceBasis === 'same_place') {
    return route.territory.placeName ? `En ${route.territory.placeName}` : 'En la misma localidad';
  }
  return route.territory.municipalityName ? `En ${route.territory.municipalityName}` : 'En el mismo municipio';
}

function routeDistance(value: number | null) {
  if (value === null) return null;
  return `${(value / 1_000).toFixed(1)} km`;
}

export function BusinessNearbyRoutes() {
  const params = useSearchParams();
  const slug = params.get('slug')?.trim() ?? '';
  const [payload, setPayload] = useState<Payload | null>(null);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!slug) return;
    let cancelled = false;
    setLoading(true);
    setFailed(false);
    apiFetch<Payload>(`/api/v1/public/businesses/${encodeURIComponent(slug)}/nearby-routes`)
      .then((value) => { if (!cancelled) setPayload(value); })
      .catch(() => { if (!cancelled) setFailed(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [slug]);

  if (!slug || failed) return null;

  return <section className={styles.page} aria-labelledby="business-nearby-routes-title">
    <div className={styles.claimCard}>
      <div>
        <p className={styles.eyebrow}>EMPRESA × TERRITORIO × RUTAS</p>
        <h2 id="business-nearby-routes-title">Rutas cerca de esta empresa</h2>
        <p>Descubre rutas publicadas y validadas relacionadas con este negocio por proximidad real o, cuando no hay coordenadas, por territorio.</p>
      </div>

      {loading ? <p>Cargando rutas cercanas…</p> : null}
      {!loading && payload && payload.routes.length === 0 ? <div>
        <p>No hay rutas publicadas que podamos relacionar con esta empresa sin inventar cercanía.</p>
        <Link className={styles.secondaryButton} href="/rutas">Explorar todas las rutas</Link>
      </div> : null}

      {!loading && payload && payload.routes.length > 0 ? <>
        <div className={styles.detailGrid}>
          {payload.routes.map((route) => {
            const distance = routeDistance(route.distanceMeters);
            const difficulty = difficultyLabel(route.difficulty);
            return <article key={route.id} className={styles.infoBox}>
              <small>{proximityLabel(route)}</small>
              <strong>{route.name}</strong>
              <p>{[routeTypeLabel(route.routeType), difficulty, distance].filter(Boolean).join(' · ')}</p>
              {route.shortDescription ? <p>{route.shortDescription}</p> : null}
              <div className={styles.actionRow}>
                <Link className={styles.secondaryButton} href={`/rutas/detalle?slug=${encodeURIComponent(route.slug)}`}>Ver ruta →</Link>
              </div>
            </article>;
          })}
        </div>
        <small>{payload.meta.distanceNote}</small>
        <div className={styles.actionRow}><Link className={styles.secondaryButton} href="/rutas">Ver todas las rutas</Link></div>
      </> : null}
    </div>
  </section>;
}
