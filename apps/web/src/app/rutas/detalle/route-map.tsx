'use client';

import { useEffect, useRef, useState } from 'react';
import {
  loadPublicRouteAdventure,
  loadPublicRouteCommunity,
  loadRouteAdventureProgress,
  publicRouteMediaUrl,
  type PublicRouteCommunity,
  type PublicRouteDetail,
  type RouteAdventureCheckpoint,
} from '../../../lib/public-routes-source';
import styles from '../routes-public.module.css';

type Coordinate = [number, number];
type Photo = PublicRouteCommunity['photos'][number];
type PhotoFocusDetail = { photoId: string; latitude: number; longitude: number };
type ElevationFocusDetail = { latitude: number; longitude: number; distance_m: number; elevation_m: number; grade_percent: number | null };
type AdventureFocusDetail = { checkpointId: string; latitude: number; longitude: number };
type AdventureProgressDetail = { unlockedCheckpointIds: string[] };

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

function validCheckpointLocation(checkpoint: RouteAdventureCheckpoint) {
  return Number.isFinite(Number(checkpoint.latitude)) && Number.isFinite(Number(checkpoint.longitude));
}

function checkpointKind(kind: RouteAdventureCheckpoint['kind']) {
  return ({
    landmark: 'Lugar', trivia: 'Reto', observation: 'Observación', photo: 'Foto',
    collection: 'Coleccionable', rest: 'Descanso',
  } satisfies Record<RouteAdventureCheckpoint['kind'], string>)[kind];
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

function adventurePopup(checkpoint: RouteAdventureCheckpoint, unlocked: boolean) {
  const container = document.createElement('article');
  container.style.width = 'min(260px, 70vw)';
  const eyebrow = document.createElement('small');
  eyebrow.textContent = `Mágina Aventura · ${checkpointKind(checkpoint.kind)}`;
  eyebrow.style.display = 'block';
  eyebrow.style.marginBottom = '4px';
  eyebrow.style.fontWeight = '700';
  container.append(eyebrow);
  const title = document.createElement('strong');
  title.textContent = checkpoint.title;
  container.append(title);
  if (checkpoint.description) {
    const description = document.createElement('p');
    description.textContent = checkpoint.description;
    description.style.margin = '8px 0';
    container.append(description);
  }
  const status = document.createElement('p');
  status.dataset.adventurePopupStatus = checkpoint.id;
  status.textContent = unlocked ? `✓ Desbloqueado · +${checkpoint.points} pt` : `Pendiente · acércate a ${checkpoint.unlock_radius_m} m para desbloquearlo`;
  status.style.margin = '8px 0 0';
  status.style.fontWeight = '700';
  container.append(status);
  const safety = document.createElement('small');
  safety.textContent = 'El reto es lúdico y no sustituye la señalización ni la información de seguridad de la ruta.';
  safety.style.display = 'block';
  safety.style.marginTop = '8px';
  safety.style.opacity = '.7';
  container.append(safety);
  return container;
}

function paintAdventureMarker(button: HTMLButtonElement, unlocked: boolean) {
  button.dataset.unlocked = unlocked ? 'true' : 'false';
  button.style.width = '38px';
  button.style.height = '38px';
  button.style.borderRadius = '999px';
  button.style.border = '3px solid #fff';
  button.style.boxShadow = '0 4px 14px rgba(0,0,0,.28)';
  button.style.cursor = 'pointer';
  button.style.fontWeight = '900';
  button.style.fontSize = '16px';
  button.style.color = unlocked ? '#163d29' : '#fff';
  button.style.background = unlocked ? '#d7b768' : '#244f36';
  button.style.display = 'grid';
  button.style.placeItems = 'center';
  button.textContent = unlocked ? '✓' : '✦';
}

export function RouteMap({ detail }: { detail: PublicRouteDetail }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<import('maplibre-gl').Map | null>(null);
  const photoMarkersRef = useRef(new Map<string, import('maplibre-gl').Marker>());
  const photoPopupsRef = useRef(new Map<string, import('maplibre-gl').Popup>());
  const adventureMarkersRef = useRef(new Map<string, import('maplibre-gl').Marker>());
  const adventurePopupsRef = useRef(new Map<string, import('maplibre-gl').Popup>());
  const adventureElementsRef = useRef(new Map<string, HTMLButtonElement>());
  const [failed, setFailed] = useState(false);
  const [photoCount, setPhotoCount] = useState(0);
  const [conditionCount, setConditionCount] = useState(0);
  const [adventureCount, setAdventureCount] = useState(0);

  useEffect(() => {
    const container = containerRef.current;
    const coordinates = routeCoordinates(detail);
    if (!container || coordinates.length < 2) return;
    let disposed = false;
    let map: import('maplibre-gl').Map | null = null;

    function updateAdventureState(unlockedIds: Iterable<string>) {
      const unlocked = new Set(unlockedIds);
      for (const [checkpointId, button] of adventureElementsRef.current.entries()) {
        const done = unlocked.has(checkpointId);
        paintAdventureMarker(button, done);
        const popupNode = adventurePopupsRef.current.get(checkpointId)?.getElement();
        const status = popupNode?.querySelector<HTMLElement>(`[data-adventure-popup-status="${checkpointId}"]`);
        if (status) status.textContent = done ? '✓ Desbloqueado' : 'Pendiente · usa “Estoy aquí” desde Mágina Aventura';
      }
    }

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
          data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates } },
        });
        map.addLayer({ id: 'route-track-shadow', type: 'line', source: 'route-track', paint: { 'line-color': '#ffffff', 'line-width': 8, 'line-opacity': 0.88 } });
        map.addLayer({ id: 'route-track-line', type: 'line', source: 'route-track', paint: { 'line-color': '#265b39', 'line-width': 5 } });

        map.addSource('elevation-focus', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
        map.addLayer({
          id: 'elevation-focus-point', type: 'circle', source: 'elevation-focus',
          paint: { 'circle-radius': 8, 'circle-color': '#b45309', 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 3 },
        });

        const pointFeatures = detail.points.flatMap((point) => {
          const longitude = Number(point.longitude);
          const latitude = Number(point.latitude);
          if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return [];
          return [{
            type: 'Feature' as const,
            properties: {
              name: String(point.name ?? ''),
              kind: String(point.kind ?? 'other'),
              description: String(point.description ?? ''),
            },
            geometry: { type: 'Point' as const, coordinates: [longitude, latitude] },
          }];
        });
        if (pointFeatures.length) {
          map.addSource('route-points', { type: 'geojson', data: { type: 'FeatureCollection', features: pointFeatures } });
          map.addLayer({
            id: 'route-points-circle', type: 'circle', source: 'route-points',
            paint: { 'circle-radius': 7, 'circle-color': '#f4f1e8', 'circle-stroke-color': '#234e36', 'circle-stroke-width': 3 },
          });
          map.on('mouseenter', 'route-points-circle', () => { if (map) map.getCanvas().style.cursor = 'pointer'; });
          map.on('mouseleave', 'route-points-circle', () => { if (map) map.getCanvas().style.cursor = ''; });
          map.on('click', 'route-points-circle', (event) => {
            if (!map) return;
            const feature = event.features?.[0];
            const geometry = feature?.geometry;
            if (!feature || !geometry || geometry.type !== 'Point' || !Array.isArray(geometry.coordinates)) return;
            const name = String(feature.properties?.name ?? 'Punto de interés');
            const kind = String(feature.properties?.kind ?? 'POI');
            const description = String(feature.properties?.description ?? '');
            const node = document.createElement('article');
            const strong = document.createElement('strong'); strong.textContent = name; node.append(strong);
            const meta = document.createElement('small'); meta.textContent = ` · ${kind}`; node.append(meta);
            if (description) { const text = document.createElement('p'); text.textContent = description; node.append(text); }
            new maplibre.Popup({ offset: 12 }).setLngLat([Number(geometry.coordinates[0]), Number(geometry.coordinates[1])]).setDOMContent(node).addTo(map);
          });
        }

        void loadPublicRouteAdventure(detail.route.slug).then(async (definition) => {
          if (!map || disposed || !definition.enabled || !definition.adventure) return;
          const checkpoints = definition.checkpoints.filter(validCheckpointLocation);
          setAdventureCount(checkpoints.length);
          let unlocked = new Set<string>();
          try {
            const progress = await loadRouteAdventureProgress(detail.route.id);
            unlocked = new Set(progress.unlocks.map((item) => item.checkpoint_id));
          } catch {
            // Public visitors can still see the adventure checkpoints without exposing private progress.
          }
          if (!map || disposed) return;
          for (const checkpoint of checkpoints) {
            const button = document.createElement('button');
            button.type = 'button';
            button.dataset.adventureCheckpointId = checkpoint.id;
            button.title = `${checkpoint.title} · ${checkpointKind(checkpoint.kind)}`;
            button.setAttribute('aria-label', `Mágina Aventura: ${button.title}`);
            paintAdventureMarker(button, unlocked.has(checkpoint.id));
            const popup = new maplibre.Popup({ offset: 24, closeButton: true })
              .setDOMContent(adventurePopup(checkpoint, unlocked.has(checkpoint.id)));
            const marker = new maplibre.Marker({ element: button, anchor: 'center' })
              .setLngLat([Number(checkpoint.longitude), Number(checkpoint.latitude)])
              .setPopup(popup)
              .addTo(map);
            adventureElementsRef.current.set(checkpoint.id, button);
            adventureMarkersRef.current.set(checkpoint.id, marker);
            adventurePopupsRef.current.set(checkpoint.id, popup);
          }
        }).catch(() => {
          // Adventure is an optional layer; failure must never hide the validated route.
        });

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
            button.style.width = '44px'; button.style.height = '44px'; button.style.padding = '2px';
            button.style.border = '3px solid #fff'; button.style.borderRadius = '999px'; button.style.overflow = 'hidden';
            button.style.background = '#244f36'; button.style.boxShadow = '0 4px 14px rgba(0,0,0,.3)'; button.style.cursor = 'pointer';
            const image = document.createElement('img');
            image.src = publicRouteMediaUrl(photo.url); image.alt = ''; image.style.width = '100%'; image.style.height = '100%';
            image.style.objectFit = 'cover'; image.style.borderRadius = '999px'; button.append(image);
            const popup = new maplibre.Popup({ offset: 28, closeButton: true }).setDOMContent(photoPopup(photo));
            const marker = new maplibre.Marker({ element: button, anchor: 'center' }).setLngLat([longitude, latitude]).setPopup(popup).addTo(map);
            photoMarkersRef.current.set(photo.id, marker);
            photoPopupsRef.current.set(photo.id, popup);
          }

          const conditions = community.conditions.filter((condition) => condition.latitude != null && condition.longitude != null);
          setConditionCount(conditions.length);
          if (conditions.length) {
            const features = conditions.map((condition) => ({
              type: 'Feature' as const,
              properties: {
                kind: condition.condition_kind,
                severity: condition.severity,
                note: condition.note ?? '',
                observed_at: condition.observed_at,
              },
              geometry: { type: 'Point' as const, coordinates: [Number(condition.longitude), Number(condition.latitude)] },
            }));
            map.addSource('community-conditions', { type: 'geojson', data: { type: 'FeatureCollection', features } });
            map.addLayer({
              id: 'community-conditions-point', type: 'circle', source: 'community-conditions',
              paint: { 'circle-radius': 8, 'circle-color': '#c2410c', 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 3 },
            });
            map.on('mouseenter', 'community-conditions-point', () => { if (map) map.getCanvas().style.cursor = 'pointer'; });
            map.on('mouseleave', 'community-conditions-point', () => { if (map) map.getCanvas().style.cursor = ''; });
            map.on('click', 'community-conditions-point', (event) => {
              if (!map) return;
              const feature = event.features?.[0];
              const geometry = feature?.geometry;
              if (!feature || !geometry || geometry.type !== 'Point' || !Array.isArray(geometry.coordinates)) return;
              const node = document.createElement('article');
              const strong = document.createElement('strong'); strong.textContent = `Aviso comunitario · ${String(feature.properties?.kind ?? 'estado')}`; node.append(strong);
              const note = String(feature.properties?.note ?? '');
              if (note) { const text = document.createElement('p'); text.textContent = note; node.append(text); }
              const warning = document.createElement('small'); warning.textContent = 'No sustituye restricciones o cierres oficiales.'; node.append(warning);
              new maplibre.Popup({ offset: 12 }).setLngLat([Number(geometry.coordinates[0]), Number(geometry.coordinates[1])]).setDOMContent(node).addTo(map);
            });
          }
        }).catch(() => {
          // Community layers are optional; a failure must not hide the validated route track.
        });
      });
      map.on('error', () => setFailed(true));
    }).catch(() => setFailed(true));

    function focusCommunityPhoto(event: Event) {
      const value = (event as CustomEvent<PhotoFocusDetail>).detail;
      if (!value || !mapRef.current) return;
      mapRef.current.flyTo({ center: [value.longitude, value.latitude], zoom: Math.max(mapRef.current.getZoom(), 15), essential: true });
      photoPopupsRef.current.get(value.photoId)?.addTo(mapRef.current);
      containerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    function focusElevation(event: Event) {
      const value = (event as CustomEvent<ElevationFocusDetail>).detail;
      const activeMap = mapRef.current;
      if (!value || !activeMap) return;
      const source = activeMap.getSource('elevation-focus') as import('maplibre-gl').GeoJSONSource | undefined;
      source?.setData({
        type: 'FeatureCollection',
        features: [{
          type: 'Feature',
          properties: { distance_m: value.distance_m, elevation_m: value.elevation_m, grade_percent: value.grade_percent },
          geometry: { type: 'Point', coordinates: [value.longitude, value.latitude] },
        }],
      });
    }

    function focusAdventureCheckpoint(event: Event) {
      const value = (event as CustomEvent<AdventureFocusDetail>).detail;
      const activeMap = mapRef.current;
      if (!value || !activeMap) return;
      activeMap.flyTo({ center: [value.longitude, value.latitude], zoom: Math.max(activeMap.getZoom(), 16), essential: true });
      adventurePopupsRef.current.get(value.checkpointId)?.addTo(activeMap);
      containerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    function syncAdventureProgress(event: Event) {
      const value = (event as CustomEvent<AdventureProgressDetail>).detail;
      if (!value || !Array.isArray(value.unlockedCheckpointIds)) return;
      updateAdventureState(value.unlockedCheckpointIds);
    }

    window.addEventListener('magina:route-photo-focus', focusCommunityPhoto);
    window.addEventListener('magina:route-elevation-focus', focusElevation);
    window.addEventListener('magina:route-adventure-focus', focusAdventureCheckpoint);
    window.addEventListener('magina:route-adventure-progress', syncAdventureProgress);

    return () => {
      disposed = true;
      window.removeEventListener('magina:route-photo-focus', focusCommunityPhoto);
      window.removeEventListener('magina:route-elevation-focus', focusElevation);
      window.removeEventListener('magina:route-adventure-focus', focusAdventureCheckpoint);
      window.removeEventListener('magina:route-adventure-progress', syncAdventureProgress);
      for (const marker of photoMarkersRef.current.values()) marker.remove();
      for (const popup of photoPopupsRef.current.values()) popup.remove();
      for (const marker of adventureMarkersRef.current.values()) marker.remove();
      for (const popup of adventurePopupsRef.current.values()) popup.remove();
      photoMarkersRef.current.clear(); photoPopupsRef.current.clear();
      adventureMarkersRef.current.clear(); adventurePopupsRef.current.clear(); adventureElementsRef.current.clear();
      mapRef.current = null;
      map?.remove();
    };
  }, [detail]);

  if (!detail.track) return <div className={styles.emptyMap}>No hay geometría pública disponible.</div>;
  return <div className={styles.interactiveMapWrap}>
    <div ref={containerRef} className={styles.interactiveMap} aria-label="Mapa inteligente del track validado, POI, retos de Mágina Aventura, perfil, fotos y avisos comunitarios" />
    {adventureCount > 0 ? <div className={styles.mapFallback} style={{ bottom: 'auto', top: 12, right: 12, left: 'auto' }}>✦ {adventureCount} reto{adventureCount === 1 ? '' : 's'} de aventura</div> : null}
    {photoCount > 0 ? <div className={styles.mapFallback} style={{ bottom: 'auto', top: 12, right: 'auto' }}>{photoCount} foto{photoCount === 1 ? '' : 's'} geolocalizada{photoCount === 1 ? '' : 's'}</div> : null}
    {conditionCount > 0 ? <div className={styles.mapFallback} style={{ bottom: 'auto', top: 48, right: 'auto' }}>{conditionCount} aviso{conditionCount === 1 ? '' : 's'} comunitario{conditionCount === 1 ? '' : 's'}</div> : null}
    {failed ? <div className={styles.mapFallback}>El mapa base no está disponible. El track validado sigue disponible en los datos de la ruta.</div> : null}
  </div>;
}
