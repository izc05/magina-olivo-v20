'use client';

import { useEffect, useMemo, useState } from 'react';
import { ArrowIcon } from './icons';
import { getPublishedContent } from '../lib/public-content-source';
import type { CmsEntry } from '../lib/admin-data-source';

const iconByType: Record<CmsEntry['type'], string> = {
  page: '📄',
  news: '📰',
  event: '📅',
  place: '📍',
  mill: '🫒',
  directory: '🏪',
  promotion: '✨',
  alert: '⚠️',
};

type TerritoryData = {
  territory_place_name?: string;
  municipality_name?: string;
  phone?: string;
  address?: string;
  services?: string[];
  opening_hours?: string;
};

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function territoryData(entry: CmsEntry): TerritoryData {
  const raw = asObject(entry.content_json);
  return {
    territory_place_name: typeof raw.territory_place_name === 'string' ? raw.territory_place_name : undefined,
    municipality_name: typeof raw.municipality_name === 'string' ? raw.municipality_name : undefined,
    phone: typeof raw.phone === 'string' ? raw.phone : undefined,
    address: typeof raw.address === 'string' ? raw.address : undefined,
    opening_hours: typeof raw.opening_hours === 'string' ? raw.opening_hours : undefined,
    services: Array.isArray(raw.services) ? raw.services.filter((item): item is string => typeof item === 'string') : undefined,
  };
}

function safeHref(value: string | null | undefined) {
  if (!value) return null;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
  } catch {
    return null;
  }
}

function safeMedia(value: string | null | undefined) {
  return safeHref(value);
}

function ManagedCard({ entry }: { entry: CmsEntry }) {
  const data = territoryData(entry);
  const href = safeHref(entry.external_url);
  const media = safeMedia(entry.media_url);
  const isTerritory = entry.type === 'place' || entry.type === 'mill' || entry.type === 'directory';
  const services = data.services?.slice(0, 3) ?? [];
  const meta = [data.territory_place_name, data.phone].filter(Boolean).join(' · ');

  const content = (
    <>
      {media ? <img src={media} alt="" loading="lazy" style={{ width: 58, height: 58, borderRadius: 14, objectFit: 'cover', flex: '0 0 auto' }} /> : <span className="explore-icon">{iconByType[entry.type]}</span>}
      <div style={{ minWidth: 0 }}>
        <h3>{entry.title}</h3>
        {isTerritory && meta ? <small style={{ display: 'block', marginBottom: 5, color: '#6e756d', fontWeight: 700 }}>{meta}</small> : null}
        <p>{entry.summary ?? 'Información publicada desde Mágina Olivo.'}</p>
        {isTerritory && services.length ? <small style={{ display: 'block', marginTop: 6, color: '#747a72' }}>{services.join(' · ')}</small> : null}
      </div>
      <ArrowIcon />
    </>
  );

  if (href) return <a className="card explore-card" href={href} target={href.startsWith('/') ? undefined : '_blank'} rel={href.startsWith('/') ? undefined : 'noreferrer'}>{content}</a>;
  return <article className="card explore-card">{content}</article>;
}

export function ManagedExploreContent() {
  const [entries, setEntries] = useState<CmsEntry[]>([]);

  useEffect(() => {
    let active = true;
    void getPublishedContent()
      .then((payload) => {
        if (active) setEntries(payload.entries);
      })
      .catch(() => {
        if (active) setEntries([]);
      });
    return () => { active = false; };
  }, []);

  const territoryEntries = useMemo(() => entries
    .filter((entry) => entry.type === 'place' || entry.type === 'mill' || entry.type === 'directory')
    .slice(0, 6), [entries]);
  const editorialEntries = useMemo(() => entries
    .filter((entry) => entry.type === 'news' || entry.type === 'event' || entry.type === 'page')
    .slice(0, 6), [entries]);

  if (!territoryEntries.length && !editorialEntries.length) return null;

  return (
    <>
      {territoryEntries.length ? <section className="section" aria-labelledby="managed-territory-title">
        <div className="section-head"><h2 id="managed-territory-title">Pueblos y servicios de Mágina</h2><span>Información local verificada desde administración</span></div>
        <div className="explore-grid">{territoryEntries.map((entry) => <ManagedCard key={entry.id} entry={entry} />)}</div>
      </section> : null}

      {editorialEntries.length ? <section className="section" aria-labelledby="managed-magina-title">
        <div className="section-head"><h2 id="managed-magina-title">Publicado en Mágina</h2><span>Noticias, eventos y contenidos locales</span></div>
        <div className="explore-grid">{editorialEntries.map((entry) => <ManagedCard key={entry.id} entry={entry} />)}</div>
      </section> : null}
    </>
  );
}
