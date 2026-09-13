'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import {
  deleteRouteActivity,
  downloadRouteActivityGpx,
  loadRecordedJourneySummary,
  loadRouteActivities,
  type RecordedJourneySummary,
  type RouteActivity,
} from '../lib/route-activity-source';

function formatKm(value: number) {
  return `${(Math.max(0, value) / 1000).toFixed(value >= 100000 ? 0 : 1)} km`;
}

function formatDuration(seconds: number) {
  const total = Math.max(0, Math.round(seconds));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  return h ? `${h} h ${m} min` : `${m} min`;
}

function formatDate(value: string) {
  try {
    return new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
  } catch {
    return value;
  }
}

export function RouteActivityHistory() {
  const [summary, setSummary] = useState<RecordedJourneySummary | null>(null);
  const [activities, setActivities] = useState<RouteActivity[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  async function refresh() {
    setLoading(true);
    try {
      const [nextSummary, history] = await Promise.all([
        loadRecordedJourneySummary(),
        loadRouteActivities(12),
      ]);
      setSummary(nextSummary);
      setActivities(history.activities);
      setMessage(null);
    } catch {
      setMessage('No se ha podido cargar ahora tu historial de recorridos.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void refresh(); }, []);

  const active = useMemo(() => activities.find((item) => item.status === 'recording' || item.status === 'paused') ?? null, [activities]);

  async function remove(activity: RouteActivity) {
    const confirmed = window.confirm('¿Borrar este recorrido y todas sus posiciones GPS? Esta acción no se puede deshacer.');
    if (!confirmed) return;
    setBusyId(activity.id);
    try {
      await deleteRouteActivity(activity.id);
      await refresh();
      setMessage('Recorrido y posiciones GPS eliminados.');
    } catch {
      setMessage('No se ha podido borrar el recorrido.');
    } finally {
      setBusyId(null);
    }
  }

  async function download(activity: RouteActivity) {
    setBusyId(activity.id);
    try {
      const blob = await downloadRouteActivityGpx(activity.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `magina-${activity.route_slug ?? 'recorrido'}-${activity.id.slice(0, 8)}.gpx`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch {
      setMessage('No se ha podido preparar el GPX. La actividad necesita al menos dos puntos GPS.');
    } finally {
      setBusyId(null);
    }
  }

  if (loading && !summary) return <section className="card profile-card premium-profile-card"><div className="profile-card-head"><h3>Mis recorridos</h3><span>Cargando…</span></div></section>;

  return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Mis recorridos grabados</h3><span>Privados</span></div>
    <p className="subtle">Aquí sí cuentan las salidas reales repetidas. No se mezclan con los “km conquistados”, que siguen midiendo rutas distintas completadas.</p>

    {summary ? <>
      <div className="profile-line"><span>Actividades finalizadas</span><strong>{summary.activity_count}</strong></div>
      <div className="profile-line"><span>Km grabados</span><strong>{formatKm(summary.recorded_distance_m)}</strong></div>
      <div className="profile-line"><span>Tiempo GPS</span><strong>{formatDuration(summary.recorded_duration_seconds)}</strong></div>
      <div className="profile-line"><span>Desnivel GPS acumulado</span><strong>{Math.round(summary.recorded_elevation_gain_m).toLocaleString('es-ES')} m+</strong></div>
      <div className="profile-line"><span>Salida más larga</span><strong>{formatKm(summary.longest_activity_m)}</strong></div>
    </> : null}

    {active ? <div className="profile-line"><span>Actividad abierta</span><strong>{active.status === 'paused' ? 'En pausa' : 'Grabando'}</strong></div> : null}

    {activities.length ? <div aria-label="Historial de recorridos">
      {activities.map((activity) => <div className="profile-line" key={activity.id} style={{ alignItems: 'flex-start', gap: 12 }}>
        <div>
          <strong>{activity.route_name ?? 'Recorrido de Mágina'}</strong>
          <small style={{ display: 'block' }}>{formatDate(activity.started_at)} · {activity.status === 'completed' ? `${formatKm(activity.distance_m)} · ${formatDuration(activity.duration_seconds)}` : activity.status === 'paused' ? 'En pausa' : 'Grabando'}</small>
          {activity.status === 'completed' && activity.elevation_gain_m !== null ? <small style={{ display: 'block' }}>+{Math.round(activity.elevation_gain_m)} m GPS · {activity.points_count} puntos</small> : null}
        </div>
        <span style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          {activity.route_slug ? <Link href={`/rutas/detalle?slug=${encodeURIComponent(activity.route_slug)}`}>Ruta</Link> : null}
          {activity.status === 'completed' && activity.points_count >= 2 ? <button type="button" disabled={busyId === activity.id} onClick={() => void download(activity)}>GPX</button> : null}
          <button type="button" disabled={busyId === activity.id} onClick={() => void remove(activity)}>Borrar</button>
        </span>
      </div>)}
    </div> : <p className="subtle">Todavía no has grabado ninguna salida. Abre una ruta validada y pulsa “Iniciar recorrido”.</p>}

    {message ? <p role="status" aria-live="polite">{message}</p> : null}
    <small className="subtle">La V1 no promete GPS en segundo plano. Si el navegador suspende la web, los huecos largos no se suman a la actividad.</small>
  </section>;
}
