'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { ArrowIcon } from './icons';
import { getPublishedContent, getPublicSiteSettings } from '../lib/public-content-source';
import type { CmsEntry } from '../lib/admin-data-source';
import { demoContext } from '../lib/demo-data';

type PublicSettings = Record<string, unknown>;

type HeroSetting = {
  eyebrow?: string;
  title?: string;
  subtitle?: string;
  cta_label?: string;
  cta_href?: string;
  image_url?: string;
};

type TerritorySetting = {
  title?: string;
  cta_label?: string;
  cta_href?: string;
};

type BannerSetting = {
  enabled?: boolean;
  text?: string;
  href?: string;
};

function objectSetting<T>(settings: PublicSettings, key: string): T | null {
  const value = settings[key];
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  return value as T;
}

function safeHref(value: string | null | undefined, fallback: string | null = null) {
  if (!value) return fallback;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : fallback;
  } catch {
    return fallback;
  }
}

function safeMediaUrl(value: string | null | undefined) {
  if (!value) return null;
  try {
    const url = new URL(value, window.location.origin);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
    return value.startsWith('/') ? value : url.toString();
  } catch {
    return null;
  }
}

function storyTag(entry: CmsEntry) {
  if (entry.type === 'news') return 'NOTICIAS';
  if (entry.type === 'event') return 'EVENTOS';
  if (entry.type === 'promotion') return 'PATROCINADO';
  if (entry.type === 'alert') return 'AVISO';
  return 'MÁGINA';
}

function storyClass(entry: CmsEntry) {
  if (entry.type === 'promotion') return 'story-image story-oil';
  if (entry.type === 'event') return 'story-image story-town';
  return 'story-image story-olive';
}

function ManagedStory({ entry }: { entry: CmsEntry }) {
  const mediaUrl = safeMediaUrl(entry.media_url);
  const externalUrl = safeHref(entry.external_url);
  const card = (
    <article className={`card story-card ${entry.type === 'promotion' ? 'sponsored' : ''}`}>
      <div className={storyClass(entry)} style={mediaUrl ? { backgroundImage: `url(${mediaUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' } : undefined} />
      <span className={`story-tag ${entry.type === 'promotion' ? 'gold' : ''}`}>{storyTag(entry)}</span>
      <h3>{entry.title}</h3>
      <p>{entry.summary ?? 'Publicado desde Mágina Olivo.'}</p>
    </article>
  );

  if (externalUrl) {
    return <a href={externalUrl} target="_blank" rel="noreferrer" style={{ color: 'inherit', textDecoration: 'none' }}>{card}</a>;
  }
  return card;
}

export function ManagedHomeContent() {
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [settings, setSettings] = useState<PublicSettings>({});

  useEffect(() => {
    let active = true;
    Promise.all([getPublishedContent(), getPublicSiteSettings()])
      .then(([contentPayload, settingsPayload]) => {
        if (!active) return;
        setEntries(contentPayload.entries);
        setSettings(settingsPayload.settings);
      })
      .catch(() => {
        if (!active) return;
        setEntries([]);
        setSettings({});
      });
    return () => { active = false; };
  }, []);

  const hero = objectSetting<HeroSetting>(settings, 'home.hero');
  const territory = objectSetting<TerritorySetting>(settings, 'home.territory_banner');
  const banner = objectSetting<BannerSetting>(settings, 'alerts.banner');

  const stories = useMemo(() => entries
    .filter((entry) => entry.type === 'news' || entry.type === 'event' || entry.type === 'promotion')
    .slice(0, 3), [entries]);

  const heroMediaUrl = safeMediaUrl(hero?.image_url);
  const heroHref = safeHref(hero?.cta_href);
  const bannerHref = safeHref(banner?.href);
  const territoryHref = safeHref(territory?.cta_href, '/explorar') ?? '/explorar';
  const showManagedHero = Boolean(hero?.title || hero?.subtitle || heroMediaUrl);

  return (
    <>
      {banner?.enabled && banner.text ? (
        <section className="section" aria-label="Aviso de Mágina Olivo">
          {bannerHref ? <a className="card" href={bannerHref} style={{ display: 'block', padding: 16, textDecoration: 'none', color: 'inherit' }}><strong>{banner.text}</strong></a> : <div className="card" style={{ padding: 16 }}><strong>{banner.text}</strong></div>}
        </section>
      ) : null}

      {showManagedHero ? (
        <section className="section">
          <div className="card" style={{ overflow: 'hidden', padding: 0 }}>
            <div style={{ minHeight: 220, display: 'flex', alignItems: 'flex-end', padding: 24, background: heroMediaUrl ? `linear-gradient(90deg, rgba(18,38,22,.78), rgba(18,38,22,.22)), url(${heroMediaUrl}) center/cover` : 'linear-gradient(135deg, #244b31, #78936b)', color: '#fff' }}>
              <div style={{ maxWidth: 720 }}>
                <span className="eyebrow" style={{ color: 'inherit' }}>{hero?.eyebrow ?? 'MÁGINA OLIVO'}</span>
                <h2 style={{ fontSize: 'clamp(1.8rem,4vw,3rem)', margin: '8px 0' }}>{hero?.title}</h2>
                {hero?.subtitle ? <p style={{ fontSize: '1.05rem', opacity: .92 }}>{hero.subtitle}</p> : null}
                {hero?.cta_label && heroHref ? <Link className="primary action-link" href={heroHref}>{hero.cta_label} <ArrowIcon /></Link> : null}
              </div>
            </div>
          </div>
        </section>
      ) : null}

      <section className="section">
        <div className="section-head"><h2>Actualidad y vida local</h2><Link href="/explorar">Ver todo <ArrowIcon /></Link></div>
        <div className="story-grid">
          {stories.length ? stories.map((entry) => <ManagedStory key={entry.id} entry={entry} />) : (
            <>
              <article className="card story-card"><div className="story-image story-olive"/><span className="story-tag">NOTICIAS</span><h3>La campaña del olivar en Sierra Mágina</h3><p>Actualidad agrícola y territorio.</p></article>
              <article className="card story-card"><div className="story-image story-town"/><span className="story-tag">EVENTOS</span><h3>Agenda local de {demoContext.municipality}</h3><p>Ferias, jornadas y encuentros.</p></article>
              <article className="card story-card sponsored"><div className="story-image story-oil"/><span className="story-tag gold">PATROCINADO</span><h3>Empresas de nuestra tierra</h3><p>Promoción local integrada y clara.</p></article>
            </>
          )}
        </div>
      </section>

      <section className="territory-banner">
        <div><span className="eyebrow">MÁGINA OLIVO</span><h2>{territory?.title ?? 'Personas que cuidan de un territorio único'}</h2></div>
        <Link href={territoryHref}>{territory?.cta_label ?? 'Descubrir Mágina'} <ArrowIcon /></Link>
      </section>
    </>
  );
}
