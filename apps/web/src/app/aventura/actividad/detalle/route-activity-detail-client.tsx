'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiRequestError } from '../../../../lib/api-client';
import { loadRouteActivityTrack, type RouteActivityTrack } from '../../../../lib/route-activity-source';
import styles from '../activity.module.css';

function km(value: number) {
  return `${(Math.max(0, value) / 1000).toFixed(value >= 100000 ? 0 : 2)} km`;
}

function duration(seconds: number) {
  const safe = Math.max(0, Math.round(seconds));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const secs = safe % 60;
  if (hours) return `${hours} h ${String(minutes).padStart(2, '0')} min`;
  return `${minutes}:${String(secs).padStart(2, '0')}`;
}

function dateLabel(value: string) {
  return new Intl.DateTimeFormat('es-ES', {
    day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value));
}

function TrackMap({ track }: { track: RouteActivityTrack }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const coordinates = useMemo(() => track.points.map((point) => [point.longitude, point.latitude] as [number, number]), [track.points]);

  useEffect(() => {
    const container = ref.current;
    if (!container || coordinates.length === 0) return;
    let disposed = false;
    let map: import('maplibre-gl').Map | null = null;
    const markers: Array<import('maplibre-gl').Marker> = [];

    void import('maplibre-gl').then((maplibre) => {
      if (disposed) return;
      const bounds = coordinates.reduce(
        (value, coordinate) => value.extend(coordinate),
        new maplibre.LngLatBounds(coordinates[0], coordinates[0]),
      );
      map = new maplibre.Map({
        container,
        style: 'https://tiles.openfreemap.org/styles/liberty',
        ...(coordinates.length > 1
          ? { bounds, fitBoundsOptions: { padding: 44, maxZoom: 16 } }
          : { center: coordinates[0], zoom: 15 }),
        attributionControl: {},
      });

      map.on('load', () => {
        if (!map || disposed) return;
        if (coordinates.length > 1) {
          map.addSource('private-activity-track', {
            type: 'geojson',
            data: {
              type: 'Feature',
              properties: {},
              geometry: { type: 'LineString', coordinates },
            },
          });
          map.addLayer({ id: 'private-track-shadow', type: 'line', source: 'private-activity-track', paint: { 'line-color': '#ffffff', 'line-width': 8, 'line-opacity': 0.9 } });
          map.addLayer({ id: 'private-track-line', type: 'line', source: 'private-activity-track', paint: { 'line-color': '#275a3b', 'line-width': 5 } });
        }

        const startNode = document.createElement('div');
        startNode.className = styles.trackMarker;
        startNode.textContent = 'S';
        startNode.title = 'Salida';
        markers.push(new maplibre.Marker({ element: startNode }).setLngLat(coordinates[0]).addTo(map));

        if (coordinates.length > 1) {
          const endNode = document.createElement('div');
          endNode.className = styles.trackMarker;
          endNode.textContent = 'F';
          endNode.title = 'Final';
          markers.push(new maplibre.Marker({ element: endNode }).setLngLat(coordinates[coordinates.length - 1]).addTo(map));
        }
      });
    }).catch(() => undefined);

    return () => {
      disposed = true;
      for (const marker of markers) marker.remove();
      map?.remove();
    };
  }, [coordinates]);

  return <div className={styles.trackMap} ref={ref} aria-label="Mapa privado del recorrido grabado" />;
}

export function RouteActivityDetailClient() {
  const searchParams = useSearchParams();
  const activityId = searchParams.get('id')?.trim() ?? '';
  const [track, setTrack] = useState<RouteActivityTrack | null>(null);
  const [loading, setLoading] = useState(Boolean(activityId));
  const [error, setError] = useState<'missing' | 'auth' | 'not_found' | 'generic' | null>(activityId ? null : 'missing');

  useEffect(() => {
    if (!activityId) {
      setTrack(null);
      setLoading(false);
      setError('missing');
      return;
    }
    let cancelled = false;
    setLoading(true);
    loadRouteActivityTrack(activityId)
      .then((value) => {
        if (cancelled) return;
        setTrack(value);
        setError(null);
      })
      .catch((cause) => {
        if (cancelled) return;
        if (cause instanceof ApiRequestError && cause.status === 401) setError('auth');
        else if (cause instanceof ApiRequestError && cause.status === 404) setError('not_found');
        else setError('generic');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [activityId]);

  if (loading) return <main className={styles.shell}><Link href="/aventura/actividad" className={styles.back}>← Actividad</Link><section className={styles.card}><h1>Preparando tu recorrido…</h1><p>Cargando el track privado y sus métricas.</p></section></main>;

  if (error) return <main className={styles.shell}><Link href="/aventura/actividad" className={styles.back}>← Actividad</Link><section className={styles.card}>
    <h1>{error === 'missing' ? 'Falta seleccionar un recorrido' : error === 'auth' ? 'Necesitas iniciar sesión' : error === 'not_found' ? 'Recorrido no encontrado' : 'No se puede abrir el recorrido'}</h1>
    <p>{error === 'missing' ? 'Vuelve al historial y elige la actividad que quieres ver.' : error === 'auth' ? 'Este mapa contiene datos privados de ubicación y solo lo puede consultar su propietario.' : error === 'not_found' ? 'Puede haberse eliminado o no pertenecer a tu cuenta.' : 'Comprueba la conexión y vuelve a intentarlo.'}</p>
    {error === 'auth' ? <Link className={styles.login} href={`/login?next=${encodeURIComponent(`/aventura/actividad/detalle?id=${activityId}`)}`}>Iniciar sesión →</Link> : null}
  </section></main>;

  if (!track) return null;
  const activity = track.activity;
  const avgSpeed = activity.active_seconds > 0 ? (activity.distance_m / 1000) / (activity.active_seconds / 3600) : 0;
  const accuracyValues = track.points.map((point) => point.accuracy_m).filter(Number.isFinite);
  const averageAccuracy = accuracyValues.length ? accuracyValues.reduce((sum, value) => sum + value, 0) / accuracyValues.length : null;

  return <main className={styles.shell}>
    <Link href="/aventura/actividad" className={styles.back}>← Historial de actividad</Link>
    <section className={styles.hero}>
      <span className={styles.eyebrow}>TRACK PRIVADO · MÁGINA AVENTURA</span>
      <h1>{activity.route_name ?? 'Recorrido libre'}</h1>
      <p>{dateLabel(activity.ended_at ?? activity.started_at)} · este track solo está disponible para tu cuenta.</p>
    </section>

    <div className={styles.detailMetrics}>
      <div className={styles.metric}><strong>{km(activity.distance_m)}</strong><span>Distancia registrada</span></div>
      <div className={styles.metric}><strong>{duration(activity.active_seconds)}</strong><span>Tiempo activo</span></div>
      <div className={styles.metric}><strong>{avgSpeed.toFixed(1)} km/h</strong><span>Media aproximada</span></div>
      <div className={styles.metric}><strong>{activity.point_count}</strong><span>Puntos GPS</span></div>
    </div>

    <section className={styles.card}>
      <div className={styles.historyHead}><div><h2>Mapa del recorrido</h2><p>Salida y final se muestran únicamente dentro de tu sesión privada.</p></div><span>{averageAccuracy == null ? 'Sin precisión' : `Precisión media ±${Math.round(averageAccuracy)} m`}</span></div>
      {track.points.length ? <TrackMap track={track} /> : <p className={styles.empty}>Este recorrido no contiene puntos GPS válidos para dibujar un mapa.</p>}
      <p className={styles.note}>El trazado procede de las muestras aceptadas por el grabador. Es una referencia de actividad personal y no sustituye un track oficial de navegación ni la señalización del sendero.</p>
    </section>

    {activity.route_slug ? <section className={styles.card} style={{ marginTop: 18 }}>
      <h2>Ruta asociada</h2><p>Este recorrido se grabó sobre una ruta publicada de Mágina Olivo.</p>
      <Link className={styles.login} href={`/rutas/detalle?slug=${encodeURIComponent(activity.route_slug)}`}>Abrir ficha oficial de la ruta →</Link>
    </section> : null}
  </main>;
}
