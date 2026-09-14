'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { loadExplorerProfile, type ExplorerProfile } from '../../lib/public-routes-source';
import { loadCurrentRouteActivity, type RouteActivity } from '../../lib/route-activity-source';
import styles from './adventure.module.css';

export function AdventureResumeBanner() {
  const [profile, setProfile] = useState<ExplorerProfile | null>(null);
  const [activity, setActivity] = useState<RouteActivity | null>(null);

  useEffect(() => {
    let cancelled = false;
    void Promise.allSettled([loadExplorerProfile(), loadCurrentRouteActivity()]).then(([profileResult, activityResult]) => {
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

  return <section className={styles.profilePanel} aria-labelledby="continue-adventure-title">
    <div className={styles.profileHeading}>
      <div>
        <span className={styles.eyebrow}>CONTINUAR EXPEDICIÓN</span>
        <h2 id="continue-adventure-title">{title}</h2>
      </div>
      <p>{progress}. Abre la vista de marcha para tener mapa, GPS y checkpoints en una sola pantalla móvil.</p>
    </div>
    <div className={styles.heroActions}>
      <Link className={styles.primaryAction} href={`/aventura/en-curso?slug=${encodeURIComponent(slug)}`}>Continuar aventura</Link>
      <Link className={styles.secondaryAction} href={`/rutas/detalle?slug=${encodeURIComponent(slug)}`}>Ficha de senderismo</Link>
    </div>
  </section>;
}
