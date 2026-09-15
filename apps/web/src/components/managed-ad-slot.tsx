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

function safePublicUrl(value: string | null | undefined) {
  if (!value) return null;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
  } catch {
    return null;
  }
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
  const mediaUrl = safePublicUrl(promotion.media_url);
  const externalUrl = safePublicUrl(promotion.external_url);
  const content = <article className="card managed-ad-slot">
    {mediaUrl ? <div className="managed-ad-slot-media" style={{ backgroundImage: `url(${mediaUrl})` }} /> : null}
    <div className="managed-ad-slot-copy">
      <span>PATROCINADO{data.sponsor ? ` · ${data.sponsor}` : ''}</span>
      <h3>{promotion.title}</h3>
      {promotion.summary ? <p>{promotion.summary}</p> : null}
      {data.cta_label ? <strong>{data.cta_label} →</strong> : null}
    </div>
  </article>;

  if (externalUrl) return <section className="section managed-ad-slot-wrap" aria-label="Publicidad"><a href={externalUrl} target="_blank" rel="noreferrer sponsored" className="managed-ad-slot-link">{content}</a></section>;
  return <section className="section managed-ad-slot-wrap" aria-label="Publicidad">{content}</section>;
}
