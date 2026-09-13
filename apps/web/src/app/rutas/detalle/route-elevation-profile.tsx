'use client';

import { useMemo, useState } from 'react';
import type { PublicRouteDetail } from '../../../lib/public-routes-source';
import styles from '../routes-public.module.css';

type ElevationSample = PublicRouteDetail['elevation'][number];

type FocusDetail = {
  latitude: number;
  longitude: number;
  distance_m: number;
  elevation_m: number;
  grade_percent: number | null;
};

function validSamples(samples: PublicRouteDetail['elevation']) {
  return samples.filter((sample) =>
    Number.isFinite(Number(sample.distance_m))
    && Number.isFinite(Number(sample.elevation_m))
    && sample.latitude != null
    && sample.longitude != null
    && Number.isFinite(Number(sample.latitude))
    && Number.isFinite(Number(sample.longitude)));
}

function point(sample: ElevationSample, maxDistance: number, minElevation: number, range: number, width: number, height: number, padding: number) {
  return {
    x: padding + (Number(sample.distance_m) / maxDistance) * (width - padding * 2),
    y: height - padding - ((Number(sample.elevation_m) - minElevation) / range) * (height - padding * 2),
  };
}

function dispatchFocus(sample: ElevationSample | null) {
  if (!sample || sample.latitude == null || sample.longitude == null) return;
  window.dispatchEvent(new CustomEvent<FocusDetail>('magina:route-elevation-focus', {
    detail: {
      latitude: Number(sample.latitude),
      longitude: Number(sample.longitude),
      distance_m: Number(sample.distance_m),
      elevation_m: Number(sample.elevation_m),
      grade_percent: sample.grade_percent == null ? null : Number(sample.grade_percent),
    },
  }));
}

export function RouteElevationProfile({ samples }: { samples: PublicRouteDetail['elevation'] }) {
  const width = 1100;
  const height = 230;
  const padding = 24;
  const usable = useMemo(() => validSamples(samples), [samples]);
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  const metrics = useMemo(() => {
    if (usable.length < 2) return null;
    const maxDistance = Math.max(...usable.map((sample) => Number(sample.distance_m)), 1);
    const elevations = usable.map((sample) => Number(sample.elevation_m));
    const minElevation = Math.min(...elevations);
    const maxElevation = Math.max(...elevations);
    const range = Math.max(maxElevation - minElevation, 1);
    const points = usable.map((sample) => point(sample, maxDistance, minElevation, range, width, height, padding));
    return {
      maxDistance,
      minElevation,
      maxElevation,
      points,
      path: points.map((value, index) => `${index ? 'L' : 'M'}${value.x.toFixed(1)},${value.y.toFixed(1)}`).join(' '),
    };
  }, [usable]);

  if (!metrics) return <section className={styles.profileCard}>
    <h2>Perfil de elevación</h2>
    <p>No hay cotas georreferenciadas suficientes en el GPX validado. No se han inventado altitudes.</p>
  </section>;

  const resolvedMetrics = metrics;
  const active = activeIndex == null ? null : usable[activeIndex] ?? null;
  const activePoint = activeIndex == null ? null : resolvedMetrics.points[activeIndex] ?? null;

  function selectFromPointer(clientX: number, element: SVGSVGElement) {
    const bounds = element.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (clientX - bounds.left) / Math.max(bounds.width, 1)));
    const targetDistance = ratio * resolvedMetrics.maxDistance;
    let bestIndex = 0;
    let bestDistance = Number.POSITIVE_INFINITY;
    usable.forEach((sample, index) => {
      const delta = Math.abs(Number(sample.distance_m) - targetDistance);
      if (delta < bestDistance) { bestDistance = delta; bestIndex = index; }
    });
    setActiveIndex(bestIndex);
    dispatchFocus(usable[bestIndex]);
  }

  return <section className={styles.profileCard}>
    <div style={{ display: 'flex', gap: 16, justifyContent: 'space-between', alignItems: 'baseline', flexWrap: 'wrap' }}>
      <div><h2>Perfil de elevación interactivo</h2><p style={{ margin: 0 }}>Mueve el cursor o toca el perfil para localizar ese punto sobre el mapa.</p></div>
      <small>{Math.round(resolvedMetrics.minElevation)}–{Math.round(resolvedMetrics.maxElevation)} m · {(resolvedMetrics.maxDistance / 1000).toFixed(1)} km</small>
    </div>
    <div style={{ position: 'relative', marginTop: 14 }}>
      <svg
        className={styles.profileSvg}
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="none"
        role="img"
        aria-label="Perfil de elevación interactivo calculado a partir de las cotas GPX"
        onPointerMove={(event) => selectFromPointer(event.clientX, event.currentTarget)}
        onPointerDown={(event) => selectFromPointer(event.clientX, event.currentTarget)}
        onPointerLeave={() => { setActiveIndex(null); }}
        style={{ touchAction: 'pan-y', cursor: 'crosshair' }}
      >
        <path d={`${resolvedMetrics.path} L${width - padding},${height - padding} L${padding},${height - padding} Z`} fill="rgba(56,98,65,.14)" />
        <path d={resolvedMetrics.path} fill="none" stroke="currentColor" strokeWidth="4" vectorEffect="non-scaling-stroke" />
        {activePoint ? <>
          <line x1={activePoint.x} x2={activePoint.x} y1={padding} y2={height - padding} stroke="currentColor" strokeWidth="2" strokeDasharray="7 7" vectorEffect="non-scaling-stroke" />
          <circle cx={activePoint.x} cy={activePoint.y} r="7" fill="currentColor" vectorEffect="non-scaling-stroke" />
        </> : null}
      </svg>
      {active ? <div aria-live="polite" style={{ marginTop: 8, display: 'flex', gap: 14, flexWrap: 'wrap' }}>
        <strong>{(Number(active.distance_m) / 1000).toFixed(2)} km</strong>
        <span>{Math.round(Number(active.elevation_m))} m de altitud</span>
        {active.grade_percent != null ? <span>Pendiente {Number(active.grade_percent).toFixed(1)} %</span> : null}
      </div> : null}
    </div>
  </section>;
}
