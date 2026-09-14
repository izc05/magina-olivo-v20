'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  loadAdventureRewards,
  trackAdventureRewardEvent,
  type AdventureReward,
} from '../../../lib/adventure-rewards-source';
import styles from '../routes-public.module.css';

function sessionKey() {
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

export function RouteAdventureReward({ routeId }: { routeId: string }) {
  const [reward, setReward] = useState<AdventureReward | null>(null);
  const impressed = useRef<string | null>(null);

  const refresh = useCallback(() => {
    void loadAdventureRewards()
      .then((response) => {
        const next = response.rewards.find((item) => item.route_id === routeId) ?? null;
        setReward(next);
        if (next && impressed.current !== next.sponsorship_id) {
          impressed.current = next.sponsorship_id;
          void trackAdventureRewardEvent(next.sponsorship_id, 'impression', sessionKey()).catch(() => undefined);
        }
      })
      .catch(() => setReward(null));
  }, [routeId]);

  useEffect(() => {
    refresh();
    const onProgress = () => refresh();
    window.addEventListener('magina:route-adventure-progress', onProgress);
    return () => window.removeEventListener('magina:route-adventure-progress', onProgress);
  }, [refresh]);

  if (!reward) return null;

  return <section className={styles.infoCard} aria-labelledby="adventure-reward-title" style={{ marginTop: 18 }}>
    <div className={styles.reviewMeta}>
      <strong id="adventure-reward-title">🎁 Recompensa desbloqueada</strong>
      <span>{reward.disclosure || 'Patrocinado'}</span>
    </div>
    <h3>{reward.headline ?? reward.sponsor_name}</h3>
    <p>{reward.description ?? `Beneficio ofrecido por ${reward.sponsor_name} tras completar esta aventura.`}</p>
    {reward.promo_code ? <p><strong>Código: {reward.promo_code}</strong></p> : null}
    <div className={styles.actionRow}>
      {reward.cta_url ? <a
        className={styles.primaryAction}
        href={reward.cta_url}
        target="_blank"
        rel="noreferrer sponsored"
        onClick={() => void trackAdventureRewardEvent(reward.sponsorship_id, 'click', sessionKey()).catch(() => undefined)}
      >{reward.cta_label ?? 'Ver recompensa'} ↗</a> : null}
    </div>
    <small>La recompensa depende de una campaña comercial activa. El patrocinador no recibe tu track GPS ni tu velocidad para desbloquearla.</small>
  </section>;
}
