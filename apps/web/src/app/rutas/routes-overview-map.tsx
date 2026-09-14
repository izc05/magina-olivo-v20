'use client';

import { useEffect, useRef, useState } from 'react';
import { loadPublicRoute, type PublicRouteDetail, type PublicRouteSummary } from '../../lib/public-routes-source';
import styles from './routes-public.module.css';

type Coordinate = [number, number];

type RouteGeometry = {
  route: PublicRouteSummary;
  lines: Coordinate[][];
};

function geometryLines(detail: PublicRouteDetail): Coordinate[][] {
  const geometry = detail.track?.geometry;
  if (!geometry || !Array.isArray(geometry.coordinates)) return [];
  if (geometry.type === 'LineString') {
    const line = geometry.coordinates.filter((coordinate): coordinate is Coordinate =>
      Array.isArray(coordinate)
      && coordinate.length >= 2
      && Number.isFinite(Number(coordinate[0]))
      && Number.isFinite(Number(coordinate[1]))
    ).map((coordinate) => [Number(coordinate[0]), Number(coordinate[1])] as Coordinate);
    return line.length >= 2 ? [line] : [];
  }
  if (geometry.type === 'MultiLineString') {
    return geometry.coordinates.flatMap((candidate) => {
      if (!Array.isArray(candidate)) return [];
      const line = candidate.filter((coordinate): coordinate is Coordinate =>
        Array.isArray(coordinate)
        && coordinate.length >= 2
        && Number.isFinite(Number(coordinate[0]))
        && Number.isFinite(Number(coordinate[1]))
      ).map((coordinate) => [Number(coordinate[0]), Number(coordinate[1])] as Coordinate);
      return line.length >= 2 ? [line] : [];
    });
  }
  return [];
}

function km(value: number | null) {
  return value == null ? '—' : `${(value / 1000).toFixed(1)} km`;
}

function difficulty(value: PublicRouteSummary['difficulty']) {
  return value === 'easy' ? 'Fácil'
    : value === 'moderate' ? 'Moderada'
      : value === 'hard' ? 'Difícil'
        : value === 'very_hard' ? 'Muy difícil'
          : 'Sin clasificar';
}

function popupNode(route: PublicRouteSummary) {
  const article = document.createElement('article');
  article.style.width = 'min(280px, 72vw)';

  const eyebrow = document.createElement('small');
  eyebrow.textContent = `${route.municipality_name ?? route.place_name ?? 'Sierra Mágina'} · ${difficulty(route.difficulty)}`;
  eyebrow.style.display = 'block';
  eyebrow.style.marginBottom = '5px';
  eyebrow.style.opacity = '.7';
  article.append(eyebrow);

  const title = document.createElement('strong');
  title.textContent = route.name;
  title.style.display = 'block';
  title.style.fontSize = '1rem';
  article.append(title);

  const meta = document.createElement('p');
  meta.textContent = `${km(route.distance_m)} · +${route.elevation_gain_m ?? '—'} m`;
  meta.style.margin = '7px 0 10px';
  article.append(meta);

  const link = document.createElement('a');
  link.href = `/rutas/detalle?slug=${encodeURIComponent(route.slug)}`;
  link.textContent = 'Abrir ruta →';
  link.style.fontWeight = '800';
  link.style.color = '#244e35';
  article.append(link);
  return article;
}

export function RoutesOverviewMap({ routes }: { routes: PublicRouteSummary[] }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [geometries, setGeometries] = useState<RouteGeometry[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!routes.length) {
      setGeometries([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    setFailed(false);
    void Promise.all(routes.map(async (route) => {
      try {
        const detail = await loadPublicRoute(route.slug);
        return { route, lines: geometryLines(detail) } satisfies RouteGeometry;
      } catch {
        return { route, lines: [] } satisfies RouteGeometry;
      }
    })).then((items) => {
      if (cancelled) return;
      setGeometries(items.filter((item) => item.lines.length > 0));
    }).catch(() => {
      if (!cancelled) setFailed(true);
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [routes]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || !geometries.length) return;
    let disposed = false;
    let map: import('maplibre-gl').Map | null = null;

    void import('maplibre-gl').then((maplibre) => {
      if (disposed) return;
      const allCoordinates = geometries.flatMap((item) => item.lines.flat());
      if (!allCoordinates.length) return;
      const bounds = allCoordinates.reduce(
        (value, coordinate) => value.extend(coordinate),
        new maplibre.LngLatBounds(allCoordinates[0], allCoordinates[0]),
      );
      const features = geometries.flatMap((item) => item.lines.map((line) => ({
        type: 'Feature' as const,
        properties: {
          id: item.route.id,
          slug: item.route.slug,
          name: item.route.name,
          difficulty: item.route.difficulty ?? 'unknown',
        },
        geometry: { type: 'LineString' as const, coordinates: line },
      })));

      map = new maplibre.Map({
        container,
        style: 'https://tiles.openfreemap.org/styles/liberty',
        bounds,
        fitBoundsOptions: { padding: 46, maxZoom: 13 },
        attributionControl: {},
      });

      map.on('load', () => {
        if (!map || disposed) return;
        map.addSource('magina-routes-overview', {
          type: 'geojson',
          data: { type: 'FeatureCollection', features },
        });
        map.addLayer({
          id: 'magina-routes-overview-shadow',
          type: 'line',
          source: 'magina-routes-overview',
          paint: { 'line-color': '#ffffff', 'line-width': 8, 'line-opacity': 0.9 },
        });
        map.addLayer({
          id: 'magina-routes-overview-line',
          type: 'line',
          source: 'magina-routes-overview',
          paint: {
            'line-width': 5,
            'line-color': [
              'match', ['get', 'difficulty'],
              'easy', '#3f7d4a',
              'moderate', '#b98524',
              'hard', '#a84c34',
              'very_hard', '#6d2932',
              '#275a3b',
            ],
          },
        });
        map.on('mouseenter', 'magina-routes-overview-line', () => {
          if (map) map.getCanvas().style.cursor = 'pointer';
        });
        map.on('mouseleave', 'magina-routes-overview-line', () => {
          if (map) map.getCanvas().style.cursor = '';
        });
        map.on('click', 'magina-routes-overview-line', (event) => {
          if (!map) return;
          const feature = event.features?.[0];
          const slug = String(feature?.properties?.slug ?? '');
          const route = routes.find((item) => item.slug === slug);
          if (!route) return;
          new maplibre.Popup({ offset: 12 })
            .setLngLat(event.lngLat)
            .setDOMContent(popupNode(route))
            .addTo(map);
        });
      });
    }).catch(() => setFailed(true));

    return () => {
      disposed = true;
      map?.remove();
    };
  }, [geometries, routes]);

  return <section className={styles.overviewMapCard}>
    <div className={styles.overviewMapHeader}>
      <div><span>Mapa general</span><h2>Rutas verificadas de Sierra Mágina</h2><p>Solo aparecen recorridos publicados con track real validado.</p></div>
      <strong>{geometries.length} rutas en mapa</strong>
    </div>
    {loading ? <div className={styles.overviewMapState}>Preparando tracks verificados…</div> : null}
    {!loading && failed ? <div className={styles.overviewMapState}>El mapa no está disponible ahora mismo. Las fichas de ruta siguen accesibles.</div> : null}
    {!loading && !failed && geometries.length === 0 ? <div className={styles.overviewMapState}>No hay tracks verificables que coincidan con este filtro.</div> : null}
    {!loading && !failed && geometries.length > 0 ? <div ref={containerRef} className={styles.overviewMap} aria-label="Mapa general de rutas verificadas de Sierra Mágina" /> : null}
    <div className={styles.overviewLegend}><span><i className={styles.legendEasy} />Fácil</span><span><i className={styles.legendModerate} />Moderada</span><span><i className={styles.legendHard} />Difícil</span><span><i className={styles.legendVeryHard} />Muy difícil</span></div>
  </section>;
}
