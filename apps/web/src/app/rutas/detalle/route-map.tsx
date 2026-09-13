'use client';

import { useEffect, useRef, useState } from 'react';
import {
  loadPublicRouteCommunity,
  publicRouteMediaUrl,
  type PublicRouteCommunity,
  type PublicRouteDetail,
} from '../../../lib/public-routes-source';
import styles from '../routes-public.module.css';

type Coordinate = [number, number];
type Photo = PublicRouteCommunity['photos'][number];
type PhotoFocusDetail = { photoId: string; latitude: number; longitude: number };

function routeCoordinates(detail: PublicRouteDetail): Coordinate[] {
  const geometry = detail.track?.geometry;
  if (!geometry || geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates)) return [];
  return geometry.coordinates.filter((coordinate): coordinate is Coordinate =>
    Array.isArray(coordinate)
    && coordinate.length >= 2
    && Number.isFinite(coordinate[0])
    && Number.isFinite(coordinate[1]));
}

function validPhotoLocation(photo: Photo) {
  return photo.latitude != null
    && photo.longitude != null
    && Number.isFinite(Number(photo.latitude))
    && Number.isFinite(Number(photo.longitude));
}

function photoPopup(photo: Photo) {
  const container = document.createElement('article');
  container.style.width = 'min(260px, 70vw)';
  const image = document.createElement('img');
  image.src = publicRouteMediaUrl(photo.url);
  image.alt = photo.caption ?? 'Fotografía aportada por la comunidad';
  image.style.width = '100%';
  image.style.maxHeight = '180px';
  image.style.objectFit = 'cover';
  image.style.borderRadius = '8px';
  container.append(image);
  if (photo.caption) {
    const caption = document.createElement('p');
    caption.textContent = photo.caption;
    caption.style.margin = '8px 0 0';
    container.append(caption);
  }
  const note = document.createElement('small');
  note.textContent = 'Foto de la comunidad · aprobada por moderación';
  note.style.display = 'block';
  note.style.marginTop = '6px';
  note.style.opacity = '.7';
  container.append(note);
  return container;
}

export function RouteMap({ detail }: { detail: PublicRouteDetail }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<import('maplibre-gl').Map | null>(null);
  const photoMarkersRef = useRef(new Map<string, import('maplibre-gl').Marker>());
  const photoPopupsRef = useRef(new Map<string, import('maplibre-gl').Popup>());
  const [failed, setFailed] = useState(false);
  const [photoCount, setPhotoCount] = useState(0);

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
        attributionControl: {},
      });
      mapRef.current = map;

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

        void loadPublicRouteCommunity(detail.route.slug).then((community) => {
          if (!map || disposed) return;
          const photos = community.photos.filter(validPhotoLocation);
          setPhotoCount(photos.length);
          for (const photo of photos) {
            const longitude = Number(photo.longitude);
            const latitude = Number(photo.latitude);
            const button = document.createElement('button');
            button.type = 'button';
            button.dataset.photoId = photo.id;
            button.title = photo.caption ? `Ver foto: ${photo.caption}` : 'Ver foto de la comunidad';
            button.setAttribute('aria-label', button.title);
            button.style.width = '44px';
            button.style.height = '44px';
            button.style.padding = '2px';
            button.style.border = '3px solid #fff';
            button.style.borderRadius = '999px';
            button.style.overflow = 'hidden';
            button.style.background = '#244f36';
            button.style.boxShadow = '0 4px 14px rgba(0,0,0,.3)';
            button.style.cursor = 'pointer';
            const image = document.createElement('img');
            image.src = publicRouteMediaUrl(photo.url);
            image.alt = '';
            image.style.width = '100%';
            image.style.height = '100%';
            image.style.objectFit = 'cover';
            image.style.borderRadius = '999px';
            button.append(image);

            const popup = new maplibre.Popup({ offset: 28, closeButton: true }).setDOMContent(photoPopup(photo));
            const marker = new maplibre.Marker({ element: button, anchor: 'center' })
              .setLngLat([longitude, latitude])
              .setPopup(popup)
              .addTo(map);
            photoMarkersRef.current.set(photo.id, marker);
            photoPopupsRef.current.set(photo.id, popup);
          }
        }).catch(() => {
          // Community media is optional; a failure must not hide the validated route track.
        });
      });
      map.on('error', () => setFailed(true));
    }).catch(() => setFailed(true));

    function focusCommunityPhoto(event: Event) {
      const custom = event as CustomEvent<PhotoFocusDetail>;
      const value = custom.detail;
      if (!value || !mapRef.current) return;
      mapRef.current.flyTo({ center: [value.longitude, value.latitude], zoom: Math.max(mapRef.current.getZoom(), 15), essential: true });
      photoPopupsRef.current.get(value.photoId)?.addTo(mapRef.current);
      containerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
    window.addEventListener('magina:route-photo-focus', focusCommunityPhoto);

    return () => {
      disposed = true;
      window.removeEventListener('magina:route-photo-focus', focusCommunityPhoto);
      for (const marker of photoMarkersRef.current.values()) marker.remove();
      for (const popup of photoPopupsRef.current.values()) popup.remove();
      photoMarkersRef.current.clear();
      photoPopupsRef.current.clear();
      mapRef.current = null;
      map?.remove();
    };
  }, [detail]);

  if (!detail.track) return <div className={styles.emptyMap}>No hay geometría pública disponible.</div>;
  return <div className={styles.interactiveMapWrap}>
    <div ref={containerRef} className={styles.interactiveMap} aria-label="Mapa interactivo del track validado y fotografías geolocalizadas aprobadas" />
    {photoCount > 0 ? <div className={styles.mapFallback} style={{ bottom: 'auto', top: 12, right: 'auto' }}>{photoCount} foto{photoCount === 1 ? '' : 's'} geolocalizada{photoCount === 1 ? '' : 's'} de la comunidad</div> : null}
    {failed ? <div className={styles.mapFallback}>El mapa base no está disponible. El track validado sigue disponible en los datos de la ruta.</div> : null}
  </div>;
}
