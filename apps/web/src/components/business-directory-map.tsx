'use client';

import { useEffect, useRef } from 'react';
import type { BusinessDirectoryItem } from '@/lib/business-directory-source';
import styles from './business-directory.module.css';

type Props = {
  businesses: BusinessDirectoryItem[];
};

export function BusinessDirectoryMap({ businesses }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    const points = businesses.filter((business) => business.location);
    if (!container || points.length === 0) return;

    let disposed = false;
    let map: import('maplibre-gl').Map | null = null;

    void import('maplibre-gl').then((module) => {
      if (disposed || !containerRef.current) return;
      const maplibregl = module.default;
      const first = points[0]?.location;
      if (!first) return;

      map = new maplibregl.Map({
        container: containerRef.current,
        center: [first.longitude, first.latitude],
        zoom: 10,
        attributionControl: false,
        style: {
          version: 8,
          sources: {
            osm: {
              type: 'raster',
              tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
              tileSize: 256,
              attribution: '© OpenStreetMap contributors',
            },
          },
          layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
        },
      });

      const bounds = new maplibregl.LngLatBounds();
      for (const business of points) {
        const location = business.location;
        if (!location) continue;
        bounds.extend([location.longitude, location.latitude]);

        const markerElement = document.createElement('button');
        markerElement.type = 'button';
        markerElement.className = styles.mapMarker;
        markerElement.title = business.name;
        markerElement.setAttribute('aria-label', `Ver ${business.name} en el mapa`);
        markerElement.textContent = business.placement.sponsored ? '★' : '●';

        const popupNode = document.createElement('div');
        const strong = document.createElement('strong');
        strong.textContent = business.name;
        popupNode.append(strong);
        const territory = business.territory.placeName ?? business.territory.municipalityName;
        if (territory) {
          const locationLine = document.createElement('div');
          locationLine.textContent = territory;
          popupNode.append(locationLine);
        }
        if (business.placement.label) {
          const placement = document.createElement('small');
          placement.textContent = business.placement.label;
          popupNode.append(placement);
        }

        const popup = new maplibregl.Popup({ offset: 18 }).setDOMContent(popupNode);
        new maplibregl.Marker({ element: markerElement })
          .setLngLat([location.longitude, location.latitude])
          .setPopup(popup)
          .addTo(map);
      }

      if (points.length > 1) {
        map.fitBounds(bounds, { padding: 54, maxZoom: 13, duration: 0 });
      }
    });

    return () => {
      disposed = true;
      map?.remove();
    };
  }, [businesses]);

  const located = businesses.filter((business) => business.location).length;
  if (!located) {
    return <div className={styles.mapEmpty}>No hay coordenadas públicas disponibles para los resultados actuales.</div>;
  }

  return <div className={styles.mapFrame}>
    <div ref={containerRef} className={styles.mapCanvas} aria-label={`Mapa con ${located} empresas localizadas`} />
    <div className={styles.mapAttribution}>Mapa © OpenStreetMap contributors</div>
  </div>;
}
