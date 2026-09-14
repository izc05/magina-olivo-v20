'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { loadRouteActivitySummary, type RouteActivitySummary } from '../lib/route-activity-source';

function km(value: number) {
  return `${(Math.max(0, value) / 1000).toFixed(value >= 100000 ? 0 : 1)} km`;
}

function duration(seconds: number) {
  const safe = Math.max(0, Math.round(seconds));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  if (hours) return `${hours} h ${minutes ? `${minutes} min` : ''}`.trim();
  return `${minutes} min`;
}

export function RecordedActivityProfileCard() {
  const [data, setData] = useState<RouteActivitySummary | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadRouteActivitySummary()
      .then((value) => { if (!cancelled) { setData(value); setFailed(false); } })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, []);

  if (failed) return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Actividad registrada</h3><span>Sin conexión</span></div>
    <p className="subtle">No se ha podido cargar el historial privado de recorridos.</p>
    <Link className="profile-line" href="/aventura/actividad"><span>Abrir grabador</span><span>›</span></Link>
  </section>;

  if (!data) return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Actividad registrada</h3><span>Cargando…</span></div>
    <p className="subtle">Preparando tus kilómetros y recorridos privados.</p>
  </section>;

  return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Actividad registrada</h3><span>GPS voluntario</span></div>
    <div className="profile-line"><span>Km registrados</span><strong>{km(data.summary.recorded_distance_m)}</strong></div>
    <div className="profile-line"><span>Recorridos guardados</span><strong>{data.summary.completed_activities}</strong></div>
    <div className="profile-line"><span>Tiempo activo</span><strong>{duration(data.summary.recorded_active_seconds)}</strong></div>
    <div className="profile-line"><span>Recorrido más largo</span><strong>{km(data.summary.longest_activity_m)}</strong></div>
    <Link className="profile-line" href="/aventura/actividad"><span>Grabar o ver historial</span><span>›</span></Link>
    <small className="subtle">Estos km miden actividad real registrada. Son distintos de los “km conquistados”, que solo cuentan una vez cada ruta diferente completada.</small>
  </section>;
}
