'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { ArrowIcon } from './icons';
import { getPublishedContent, getPublicSiteSettings } from '../lib/public-content-source';
import type { CmsEntry } from '../lib/admin-data-source';

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
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
  } catch {
    return null;
  }
}

function hasFixedPromotionSlot(entry: CmsEntry) {
  if (entry.type !== 'promotion' || !entry.content_json || typeof entry.content_json !== 'object' || Array.isArray(entry.content_json)) return false;
  const slot = (entry.content_json as { slot?: unknown }).slot;
  return slot === 'home_top' || slot === 'home_inline' || slot === 'explore_top' || slot === 'explore_inline';
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
  const internalUrl = entry.type === 'news'
    ? `/noticias?slug=${encodeURIComponent(entry.slug)}`
    : entry.type === 'event'
      ? `/eventos?slug=${encodeURIComponent(entry.slug)}`
      : null;
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
  if (internalUrl) {
    return <Link href={internalUrl} style={{ color: 'inherit', textDecoration: 'none' }}>{card}</Link>;
  }
  return card;
}

export function ManagedHomeContent({ children }: { children?: ReactNode }) {
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [settings, setSettings] = useState<PublicSettings>({});
  const [contentStatus, setContentStatus] = useState<'loading' | 'ready' | 'error'>('loading');

  useEffect(() => {
    let active = true;
    Promise.all([getPublishedContent(), getPublicSiteSettings()])
      .then(([contentPayload, settingsPayload]) => {
        if (!active) return;
        setEntries(contentPayload.entries);
        setSettings(settingsPayload.settings);
        setContentStatus('ready');
      })
      .catch(() => {
        if (!active) return;
        setEntries([]);
        setSettings({});
        setContentStatus('error');
      });
    return () => { active = false; };
  }, []);

  const hero = objectSetting<HeroSetting>(settings, 'home.hero');
  const territory = objectSetting<TerritorySetting>(settings, 'home.territory_banner');
  const banner = objectSetting<BannerSetting>(settings, 'alerts.banner');

  const stories = useMemo(() => entries
    .filter((entry) => (entry.type === 'news' || entry.type === 'event' || entry.type === 'promotion') && !hasFixedPromotionSlot(entry))
    .slice(0, 3), [entries]);

  const heroMediaUrl = safeMediaUrl(hero?.image_url);
  const heroHref = safeHref(hero?.cta_href);
  const bannerHref = safeHref(banner?.href);
  const territoryHref = safeHref(territory?.cta_href, '/explorar') ?? '/explorar';

  return (
    <>
      {banner?.enabled && banner.text ? (
        <section className="section" aria-label="Aviso de Mágina Olivo">
          {bannerHref ? <a className="card" href={bannerHref} style={{ display: 'block', padding: 16, textDecoration: 'none', color: 'inherit' }}><strong>{banner.text}</strong></a> : <div className="card" style={{ padding: 16 }}><strong>{banner.text}</strong></div>}
        </section>
      ) : null}

        <section className="section home-editorial-hero">
          <div className="card" style={{ overflow: 'hidden', padding: 0 }}>
            <div style={{ minHeight: 220, display: 'flex', alignItems: 'flex-end', padding: 24, background: heroMediaUrl ? `linear-gradient(90deg, rgba(18,38,22,.78), rgba(18,38,22,.22)), url(${heroMediaUrl}) center/cover` : 'linear-gradient(135deg, #244b31, #78936b)', color: '#fff' }}>
              <div style={{ maxWidth: 720 }}>
                <span className="eyebrow" style={{ color: 'inherit' }}>{hero?.eyebrow ?? 'MÁGINA OLIVO'}</span>
                <h2 style={{ fontSize: 'clamp(1.8rem,4vw,3rem)', margin: '8px 0' }}>{hero?.title ?? 'Personas que cuidan de un territorio único'}</h2>
                <p style={{ fontSize: '1.05rem', opacity: .92 }}>{hero?.subtitle ?? 'El olivar, sus pueblos y su gente. Descubre Sierra Mágina y organiza el día a día de tu campo.'}</p>
                <Link className="primary action-link" href={heroHref ?? '/explorar'}>{hero?.cta_label ?? 'Descubrir Mágina'} <ArrowIcon /></Link>
              </div>
            </div>
          </div>
        </section>

      {children}

      <section className="section home-stories">
        <div className="section-head"><h2>Actualidad y vida local</h2><Link href="/explorar">Ver todo <ArrowIcon /></Link></div>
        {!stories.length ? <p className="subtle" role="status">{contentStatus === 'loading' ? 'Cargando actualidad…' : contentStatus === 'error' ? 'La actualidad no está disponible ahora. Puedes acceder a las secciones públicas.' : 'Todavía no hay publicaciones destacadas.'}</p> : null}
        <div className="story-grid">
          {stories.length ? stories.map((entry) => <ManagedStory key={entry.id} entry={entry} />) : (
            <>
              <Link href="/noticias" style={{ color: 'inherit', textDecoration: 'none' }}><article className="card story-card"><div className="story-image story-olive"/><span className="story-tag">NOTICIAS</span><h3>Noticias de Mágina</h3><p>Consultar la actualidad agrícola y del territorio.</p></article></Link>
              <Link href="/eventos" style={{ color: 'inherit', textDecoration: 'none' }}><article className="card story-card"><div className="story-image story-town"/><span className="story-tag">EVENTOS</span><h3>Agenda del territorio</h3><p>Consultar ferias, jornadas y encuentros publicados.</p></article></Link>
              <Link href="/servicios" style={{ color: 'inherit', textDecoration: 'none' }}><article className="card story-card"><div className="story-image story-oil"/><span className="story-tag">DIRECTORIO</span><h3>Servicios de Mágina</h3><p>Consultar los profesionales y negocios del territorio.</p></article></Link>
            </>
          )}
        </div>
      </section>

      <section className="section home-territory-links" aria-label="Consultar el territorio">
        <div className="section-head"><h2>El territorio, a tu alcance</h2></div>
        <div className="quick-grid">
          <Link className="card quick" href="/radar"><div><strong>Tiempo y radar</strong><small>Consulta las condiciones y su disponibilidad</small></div><ArrowIcon /></Link>
          <Link className="card quick" href="/mercado"><div><strong>Mercado del aceite</strong><small>Precios publicados y evolución</small></div><ArrowIcon /></Link>
          <Link className="card quick" href="/explorar"><div><strong>Mapa de Mágina</strong><small>Pueblos, lugares y servicios</small></div><ArrowIcon /></Link>
        </div>
      </section>

      <section className="territory-banner">
        <div><span className="eyebrow">MÁGINA OLIVO</span><h2>{territory?.title ?? 'Personas que cuidan de un territorio único'}</h2></div>
        <Link href={territoryHref}>{territory?.cta_label ?? 'Descubrir Mágina'} <ArrowIcon /></Link>
      </section>
    </>
  );
}
