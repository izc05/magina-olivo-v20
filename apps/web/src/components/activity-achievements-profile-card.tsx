'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  loadRouteActivityAchievements,
  type RouteActivityAchievementSummary,
} from '../lib/route-activity-achievements-source';

function km(value: number) {
  return `${(Math.max(0, value) / 1000).toFixed(value >= 100000 ? 0 : 1)} km`;
}

function metres(value: number) {
  return `${Math.round(Math.max(0, value)).toLocaleString('es-ES')} m+`;
}

function progressLabel(metric: string, value: number, target: number) {
  if (metric === 'activities') return `${Math.min(value, target)} / ${target} salidas`;
  if (metric === 'distance_m' || metric === 'longest_activity_m') return `${km(Math.min(value, target))} / ${km(target)}`;
  return `${metres(Math.min(value, target))} / ${metres(target)}`;
}

export function ActivityAchievementsProfileCard() {
  const [data, setData] = useState<RouteActivityAchievementSummary | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadRouteActivityAchievements()
      .then((value) => {
        if (!cancelled) {
          setData(value);
          setFailed(false);
        }
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => { cancelled = true; };
  }, []);

  const unlocked = useMemo(() => data?.achievements.filter((item) => item.unlocked) ?? [], [data]);
  const next = useMemo(() => data?.achievements.find((item) => !item.unlocked) ?? null, [data]);

  if (failed) return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Logros de actividad</h3><span>Sin conexión</span></div>
    <p className="subtle">No se han podido calcular tus hitos deportivos ahora mismo.</p>
  </section>;

  if (!data) return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Logros de actividad</h3><span>Calculando…</span></div>
    <p className="subtle">Sumando recorridos, kilómetros y desnivel registrado.</p>
  </section>;

  return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Logros de actividad</h3><span>{unlocked.length}/{data.achievements.length}</span></div>
    <div className="profile-line"><span>Desnivel GPS acumulado</span><strong>{metres(data.summary.recorded_elevation_gain_m)}</strong></div>
    <div className="profile-line"><span>Km registrados</span><strong>{km(data.summary.recorded_distance_m)}</strong></div>
    <div className="profile-line"><span>Salidas completadas</span><strong>{data.summary.completed_activities}</strong></div>

    {unlocked.length ? <div style={{ marginTop: 14 }}>
      {unlocked.slice(-3).map((achievement) => <div className="profile-line" key={achievement.id}>
        <span>🏅 {achievement.label}</span><span>Conseguido ✓</span>
      </div>)}
    </div> : <p className="subtle">Tu primer recorrido guardado desbloqueará la primera insignia deportiva.</p>}

    {next ? <div style={{ marginTop: 14 }}>
      <div className="profile-card-head"><h3 style={{ fontSize: '1rem' }}>Siguiente reto</h3><span>{next.progress_percent}%</span></div>
      <strong>{next.label}</strong>
      <p className="subtle" style={{ marginBottom: 6 }}>{next.description}</p>
      <small className="subtle">{progressLabel(next.metric, next.value, next.target)}</small>
    </div> : <p className="subtle" style={{ marginTop: 14 }}>Has desbloqueado todos los logros deportivos actuales.</p>}

    <small className="subtle">{data.notice}</small>
  </section>;
}
