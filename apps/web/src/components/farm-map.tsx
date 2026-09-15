'use client';

import { useEffect, useRef } from 'react';
import { GeolocateControl, LngLatBounds, Map, NavigationControl } from 'maplibre-gl';

export type MapGeometry = {
  type: 'Polygon' | 'MultiPolygon';
  coordinates: number[][][] | number[][][][];
};

export type MapLandReference = {
  id: string;
  source: string;
  reference: string | null;
  status: string;
  geometry: MapGeometry | null;
};

export type FarmMapData = {
  id: string;
  name: string;
  geometry: MapGeometry | null;
  centroid?: { type: 'Point'; coordinates: [number, number] } | null;
  representative_point?: { type: 'Point'; coordinates: [number, number] } | null;
  references: MapLandReference[];
};

export type RadarMapOverlay = {
  imageUrl: string;
  bbox: [number, number, number, number];
};

const previewStyle = {
  version: 8 as const,
  sources: {
    osm: {
      type: 'raster' as const,
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize: 256,
      attribution: '© OpenStreetMap contributors',
      maxzoom: 19,
    },
  },
  layers: [
    { id: 'osm-base', type: 'raster' as const, source: 'osm' },
  ],
};

function visitCoordinates(value: unknown, visit: (longitude: number, latitude: number) => void) {
  if (!Array.isArray(value)) return;
  if (
    value.length >= 2
    && typeof value[0] === 'number'
    && typeof value[1] === 'number'
    && Number.isFinite(value[0])
    && Number.isFinite(value[1])
  ) {
    visit(value[0], value[1]);
    return;
  }
  for (const child of value) visitCoordinates(child, visit);
}

function geometryFeature(geometry: MapGeometry, properties: Record<string, string | null>) {
  return {
    type: 'Feature' as const,
    properties,
    geometry,
  };
}

export function FarmMap({ data, radarOverlay = null }: { data: FarmMapData; radarOverlay?: RadarMapOverlay | null }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<Map | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const initialCenter = data.representative_point?.coordinates
      ?? data.centroid?.coordinates
      ?? [-3.5, 37.7] as [number, number];

    const map = new Map({
      container: containerRef.current,
      style: previewStyle,
      center: initialCenter,
      zoom: data.geometry ? 14 : 11,
    });
    mapRef.current = map;

    map.addControl(new NavigationControl({ visualizePitch: true }), 'top-right');
    map.addControl(new GeolocateControl({
      positionOptions: { enableHighAccuracy: true },
      trackUserLocation: false,
      showUserLocation: true,
      showAccuracyCircle: true,
      fitBoundsOptions: { maxZoom: 16 },
    }), 'top-right');

    map.on('load', () => {
      if (radarOverlay) {
        const [minLon, minLat, maxLon, maxLat] = radarOverlay.bbox;
        map.addSource('radar-observed', {
          type: 'image',
          url: radarOverlay.imageUrl,
          coordinates: [
            [minLon, maxLat],
            [maxLon, maxLat],
            [maxLon, minLat],
            [minLon, minLat],
          ],
        });
        map.addLayer({
          id: 'radar-observed-layer',
          type: 'raster',
          source: 'radar-observed',
          paint: { 'raster-opacity': 0.78, 'raster-fade-duration': 0 },
        });
      }

      if (data.geometry) {
        map.addSource('farm-canonical', {
          type: 'geojson',
          data: geometryFeature(data.geometry, { kind: 'finca', name: data.name }),
        });
        map.addLayer({
          id: 'farm-canonical-fill',
          type: 'fill',
          source: 'farm-canonical',
          paint: { 'fill-color': '#60734c', 'fill-opacity': 0.24 },
        });
        map.addLayer({
          id: 'farm-canonical-line',
          type: 'line',
          source: 'farm-canonical',
          paint: { 'line-color': '#31452a', 'line-width': 3 },
        });
      }

      const referenceFeatures = data.references.flatMap((reference) => reference.geometry
        ? [geometryFeature(reference.geometry, {
            kind: 'reference',
            source: reference.source,
            reference: reference.reference,
          })]
        : []);

      if (referenceFeatures.length) {
        map.addSource('farm-references', {
          type: 'geojson',
          data: { type: 'FeatureCollection', features: referenceFeatures },
        });
        map.addLayer({
          id: 'farm-references-line',
          type: 'line',
          source: 'farm-references',
          paint: {
            'line-color': [
              'match', ['get', 'source'],
              'catastro', '#bd7a2c',
              'sigpac', '#36738a',
              '#6f6f6f',
            ],
            'line-width': 2,
            'line-dasharray': [2, 1.5],
          },
        });
      }

      const bounds = new LngLatBounds();
      let positions = 0;
      if (data.geometry) {
        visitCoordinates(data.geometry.coordinates, (longitude, latitude) => {
          bounds.extend([longitude, latitude]);
          positions += 1;
        });
      }
      for (const reference of data.references) {
        if (!reference.geometry) continue;
        visitCoordinates(reference.geometry.coordinates, (longitude, latitude) => {
          bounds.extend([longitude, latitude]);
          positions += 1;
        });
      }
      if (positions > 0 && !bounds.isEmpty()) {
        map.fitBounds(bounds, { padding: 44, maxZoom: 17, duration: 0 });
      }
    });

    return () => {
      mapRef.current = null;
      map.remove();
    };
  }, [data, radarOverlay]);

  return <div ref={containerRef} className="farm-map-canvas" aria-label={`Mapa de ${data.name}`} />;
}
