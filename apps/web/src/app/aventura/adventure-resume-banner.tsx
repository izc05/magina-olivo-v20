'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { loadExplorerProfile, type ExplorerProfile } from '../../lib/public-routes-source';
import { loadActiveRouteActivity, type RouteActivity } from '../../lib/route-activity-source';
import styles from './adventure-resume-banner.module.css';

export function AdventureResumeBanner() {
  const [profile, setProfile] = useState<ExplorerProfile | null>(null);
  const [activity, setActivity] = useState<RouteActivity | null>(null);

  useEffect(() => {
    let cancelled = false;
    void Promise.allSettled([loadExplorerProfile(), loadActiveRouteActivity()]).then(([profileResult, activityResult]) => {
      if (cancelled) return;
      if (profileResult.status === 'fulfilled') setProfile(profileResult.value);
      if (activityResult.status === 'fulfilled') setActivity(activityResult.value.activity);
    });
    return () => { cancelled = true; };
  }, []);

  const activeRun = useMemo(() => profile?.recent_runs.find((run) => run.status === 'active') ?? null, [profile]);
  const slug = activeRun?.slug ?? activity?.route_slug ?? null;
  if (!slug) return null;

  const title = activeRun?.adventure_title ?? activity?.route_name ?? 'Expedición en curso';
  const progress = activeRun ? `${activeRun.unlocked_checkpoints}/${activeRun.total_checkpoints} descubrimientos` : activity?.status === 'paused' ? 'GPS en pausa' : 'GPS activo';

  return <section className={styles.panel} aria-labelledby="continue-adventure-title">
    <div className={styles.heading}>
      <div>
        <span className={styles.eyebrow}>CONTINUAR EXPEDICIÓN</span>
        <h2 id="continue-adventure-title">{title}</h2>
      </div>
      <p>{progress}. Continúa con mapa, GPS, seguridad y checkpoints en una experiencia adaptada a web y móvil.</p>
    </div>
    <div className={styles.actions}>
      <Link className={styles.primary} href={`/aventura/en-curso?slug=${encodeURIComponent(slug)}`}>Continuar aventura <span>→</span></Link>
      <Link className={styles.secondary} href={`/rutas/detalle?slug=${encodeURIComponent(slug)}`}>Ficha de senderismo</Link>
    </div>
  </section>;
}
