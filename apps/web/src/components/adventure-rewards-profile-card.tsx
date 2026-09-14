'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import {
  loadAdventureRewards,
  trackAdventureRewardEvent,
  type AdventureRewardsResponse,
} from '../lib/adventure-rewards-source';

function rewardSessionKey() {
  if (typeof window === 'undefined') return null;
  const key = 'magina-adventure-reward-session';
  const existing = window.sessionStorage.getItem(key);
  if (existing) return existing;
  const created = typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  window.sessionStorage.setItem(key, created);
  return created;
}

function dateLabel(value: string) {
  return new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(value));
}

export function AdventureRewardsProfileCard() {
  const [data, setData] = useState<AdventureRewardsResponse | null>(null);
  const [failed, setFailed] = useState(false);
  const impressedRef = useRef(new Set<string>());

  useEffect(() => {
    let cancelled = false;
    loadAdventureRewards()
      .then((value) => {
        if (cancelled) return;
        setData(value);
        setFailed(false);
        const sessionKey = rewardSessionKey();
        for (const reward of value.rewards) {
          if (impressedRef.current.has(reward.sponsorship_id)) continue;
          impressedRef.current.add(reward.sponsorship_id);
          void trackAdventureRewardEvent(reward.sponsorship_id, 'impression', sessionKey).catch(() => undefined);
        }
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => { cancelled = true; };
  }, []);

  if (failed) return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Recompensas de Aventura</h3><span>Sin conexión</span></div>
    <p className="subtle">No se han podido cargar tus beneficios desbloqueados.</p>
  </section>;

  if (!data) return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Recompensas de Aventura</h3><span>Cargando…</span></div>
    <p className="subtle">Comprobando beneficios asociados a aventuras completadas.</p>
  </section>;

  if (!data.rewards.length) return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Recompensas de Aventura</h3><span>0 desbloqueadas</span></div>
    <p className="subtle">Cuando completes una aventura con una promoción activa, el beneficio aparecerá aquí. No necesitas correr ni compartir más GPS para desbloquearlo.</p>
    <Link className="profile-line" href="/aventura"><span>Explorar aventuras</span><span>›</span></Link>
  </section>;

  const sessionKey = rewardSessionKey();

  return <section className="card profile-card premium-profile-card">
    <div className="profile-card-head"><h3>Recompensas de Aventura</h3><span>{data.rewards.length} desbloqueada{data.rewards.length === 1 ? '' : 's'}</span></div>
    {data.rewards.slice(0, 4).map((reward) => <article key={reward.sponsorship_id} style={{ padding: '12px 0', borderTop: '1px solid var(--line, #e3e1d8)' }}>
      <div className="profile-card-head">
        <div><strong>{reward.headline ?? reward.sponsor_name}</strong><p className="subtle" style={{ margin: '4px 0 0' }}>{reward.route_name} · completada {dateLabel(reward.adventure_completed_at)}</p></div>
        <span>{reward.disclosure || 'Patrocinado'}</span>
      </div>
      {reward.description ? <p className="subtle">{reward.description}</p> : null}
      {reward.promo_code ? <div className="profile-line"><span>Código</span><strong>{reward.promo_code}</strong></div> : null}
      {reward.ends_at ? <small className="subtle">Válido durante la campaña hasta {dateLabel(reward.ends_at)}.</small> : null}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginTop: 10 }}>
        {reward.cta_url ? <a
          className="profile-line"
          href={reward.cta_url}
          target="_blank"
          rel="noreferrer sponsored"
          onClick={() => void trackAdventureRewardEvent(reward.sponsorship_id, 'click', sessionKey).catch(() => undefined)}
        ><span>{reward.cta_label ?? 'Ver beneficio'}</span><span>↗</span></a> : null}
        <Link className="profile-line" href={`/rutas/detalle?slug=${encodeURIComponent(reward.route_slug)}`}><span>Ver ruta</span><span>›</span></Link>
      </div>
    </article>)}
    <small className="subtle">{data.notice}</small>
  </section>;
}
