'use client';

import { useEffect, useMemo, useState } from 'react';
import { getPublishedContent } from '../lib/public-content-source';
import type { CmsEntry } from '../lib/admin-data-source';

export type ManagedAdSlotName = 'home_top' | 'home_inline' | 'explore_top' | 'explore_inline';

type PromotionData = {
  slot?: ManagedAdSlotName;
  cta_label?: string;
  sponsor?: string;
};

function asData(entry: CmsEntry): PromotionData {
  return entry.content_json && typeof entry.content_json === 'object' && !Array.isArray(entry.content_json)
    ? entry.content_json as PromotionData
    : {};
}

export function ManagedAdSlot({ slot }: { slot: ManagedAdSlotName }) {
  const [entries, setEntries] = useState<CmsEntry[]>([]);

  useEffect(() => {
    let active = true;
    void getPublishedContent('promotion').then((payload) => {
      if (active) setEntries(payload.entries);
    }).catch(() => {
      if (active) setEntries([]);
    });
    return () => { active = false; };
  }, []);

  const promotion = useMemo(() => entries.find((entry) => asData(entry).slot === slot), [entries, slot]);
  if (!promotion) return null;

  const data = asData(promotion);
  const content = <article className="card managed-ad-slot">
    {promotion.media_url ? <div className="managed-ad-slot-media" style={{ backgroundImage: `url(${promotion.media_url})` }} /> : null}
    <div className="managed-ad-slot-copy">
      <span>PATROCINADO{data.sponsor ? ` · ${data.sponsor}` : ''}</span>
      <h3>{promotion.title}</h3>
      {promotion.summary ? <p>{promotion.summary}</p> : null}
      {data.cta_label ? <strong>{data.cta_label} →</strong> : null}
    </div>
  </article>;

  if (promotion.external_url) return <section className="section managed-ad-slot-wrap" aria-label="Publicidad"><a href={promotion.external_url} target="_blank" rel="noreferrer sponsored" className="managed-ad-slot-link">{content}</a></section>;
  return <section className="section managed-ad-slot-wrap" aria-label="Publicidad">{content}</section>;
}
