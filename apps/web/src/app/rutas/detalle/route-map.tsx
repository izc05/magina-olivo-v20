'use client';

import { useEffect, useRef, useState } from 'react';
import type { PublicRouteDetail } from '../../../lib/public-routes-source';
import styles from '../routes-public.module.css';

type Coordinate = [number, number];

function routeCoordinates(detail: PublicRouteDetail): Coordinate[] {
  const geometry = detail.track?.geometry;
  if (!geometry || geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates)) return [];
  return geometry.coordinates.filter((coordinate): coordinate is Coordinate =>
    Array.isArray(coordinate)
    && coordinate.length >= 2
    && Number.isFinite(coordinate[0])
    && Number.isFinite(coordinate[1]));
}

export function RouteMap({ detail }: { detail: PublicRouteDetail }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const container = containerRef.current;
    const coordinates = routeCoordinates(detail);
    if (!container || coordinates.length < 2) return;
    let disposed = false;
    let map: import('maplibre-gl').Map | null = null;

    void import('maplibre-gl').then((maplibre) => {
      if (disposed) return;
      const bounds = coordinates.reduce(
        (value, coordinate) => value.extend(coordinate),
        new maplibre.LngLatBounds(coordinates[0], coordinates[0]),
      );
      map = new maplibre.Map({
        container,
        style: 'https://tiles.openfreemap.org/styles/liberty',
        bounds,
        fitBoundsOptions: { padding: 42, maxZoom: 15 },
        attributionControl: true,
      });

      map.on('load', () => {
        if (!map || disposed) return;
        map.addSource('route-track', {
          type: 'geojson',
          data: {
            type: 'Feature',
            properties: {},
            geometry: { type: 'LineString', coordinates },
          },
        });
        map.addLayer({
          id: 'route-track-shadow',
          type: 'line',
          source: 'route-track',
          paint: { 'line-color': '#ffffff', 'line-width': 8, 'line-opacity': 0.88 },
        });
        map.addLayer({
          id: 'route-track-line',
          type: 'line',
          source: 'route-track',
          paint: { 'line-color': '#265b39', 'line-width': 5 },
        });

        const pointFeatures = detail.points.flatMap((point) => {
          const longitude = Number(point.longitude);
          const latitude = Number(point.latitude);
          if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return [];
          return [{
            type: 'Feature' as const,
            properties: { name: String(point.name ?? ''), kind: String(point.kind ?? 'other') },
            geometry: { type: 'Point' as const, coordinates: [longitude, latitude] },
          }];
        });
        if (pointFeatures.length) {
          map.addSource('route-points', { type: 'geojson', data: { type: 'FeatureCollection', features: pointFeatures } });
          map.addLayer({
            id: 'route-points-circle',
            type: 'circle',
            source: 'route-points',
            paint: {
              'circle-radius': 6,
              'circle-color': '#f4f1e8',
              'circle-stroke-color': '#234e36',
              'circle-stroke-width': 3,
            },
          });
        }
      });
      map.on('error', () => setFailed(true));
    }).catch(() => setFailed(true));

    return () => {
      disposed = true;
      map?.remove();
    };
  }, [detail]);

  if (!detail.track) return <div className={styles.emptyMap}>No hay geometría pública disponible.</div>;
  return <div className={styles.interactiveMapWrap}>
    <div ref={containerRef} className={styles.interactiveMap} aria-label="Mapa interactivo del track validado" />
    {failed ? <div className={styles.mapFallback}>El mapa base no está disponible. El track validado sigue disponible en los datos de la ruta.</div> : null}
  </div>;
}
