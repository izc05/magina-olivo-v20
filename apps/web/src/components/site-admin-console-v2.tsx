'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { adminApi, type AdminSession, type CmsEntry, type CmsEntryStatus, type CmsEntryType, type PlatformAdminRole, type SiteSetting } from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import { AdminMediaPicker } from './admin-media-picker';

type Tab = 'inicio' | 'actualidad' | 'territorio' | 'publicidad' | 'seo';
type AdSlot = '' | 'home_top' | 'home_inline' | 'explore_top' | 'explore_inline';

type HomeForm = {
  eyebrow: string;
  title: string;
  subtitle: string;
  ctaLabel: string;
  ctaHref: string;
  imageUrl: string;
  territoryTitle: string;
  territoryCtaLabel: string;
  territoryCtaHref: string;
  bannerEnabled: boolean;
  bannerText: string;
  bannerHref: string;
};

type SeoForm = {
  title: string;
  description: string;
  ogImage: string;
  robotsIndex: boolean;
};

type EntryForm = {
  id: string | null;
  type: CmsEntryType;
  title: string;
  slug: string;
  summary: string;
  body: string;
  location: string;
  town: string;
  phone: string;
  address: string;
  ctaLabel: string;
  mediaUrl: string;
  externalUrl: string;
  sponsor: string;
  slot: AdSlot;
  status: CmsEntryStatus;
  featured: boolean;
  startsAt: string;
  endsAt: string;
  sortOrder: string;
};

const homeDefaults: HomeForm = {
  eyebrow: 'MÁGINA OLIVO',
  title: 'Tu olivar, tu territorio y tu día de trabajo en un solo lugar',
  subtitle: 'Información útil para gestionar el campo y estar al día de Sierra Mágina.',
  ctaLabel: 'Entrar en Mi Campo',
  ctaHref: '/mi-campo',
  imageUrl: '',
  territoryTitle: 'Personas que cuidan de un territorio único',
  territoryCtaLabel: 'Descubrir Mágina',
  territoryCtaHref: '/explorar',
  bannerEnabled: false,
  bannerText: '',
  bannerHref: '',
};

const seoDefaults: SeoForm = {
  title: 'Mágina Olivo V20',
  description: 'Territorio, personas y futuro. Gestión sencilla del olivar y guía de Sierra Mágina.',
  ogImage: '',
  robotsIndex: true,
};

const emptyEntry: EntryForm = {
  id: null,
  type: 'news',
  title: '',
  slug: '',
  summary: '',
  body: '',
  location: '',
  town: '',
  phone: '',
  address: '',
  ctaLabel: '',
  mediaUrl: '',
  externalUrl: '',
  sponsor: '',
  slot: '',
  status: 'draft',
  featured: false,
  startsAt: '',
  endsAt: '',
  sortOrder: '0',
};

const tabLabels: Record<Tab, string> = {
  inicio: 'Inicio',
  actualidad: 'Noticias y eventos',
  territorio: 'Territorio y cooperativas',
  publicidad: 'Publicidad',
  seo: 'SEO y buscadores',
};

const typeLabels: Partial<Record<CmsEntryType, string>> = {
  news: 'Noticia',
  event: 'Evento',
  place: 'Pueblo / lugar',
  mill: 'Almazara / cooperativa',
  directory: 'Directorio',
  promotion: 'Promoción',
  alert: 'Aviso',
};

const statusLabels: Record<CmsEntryStatus, string> = {
  draft: 'Borrador',
  published: 'Publicado',
  archived: 'Archivado',
};

const adSlots: Array<[AdSlot, string]> = [
  ['', 'Sin posición fija'],
  ['home_top', 'Inicio · arriba'],
  ['home_inline', 'Inicio · entre bloques'],
  ['explore_top', 'Explorar · arriba'],
  ['explore_inline', 'Explorar · entre bloques'],
];

function canEdit(role: PlatformAdminRole) {
  return role === 'super_admin' || role === 'admin' || role === 'editor';
}

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function stringValue(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback;
}

function booleanValue(value: unknown, fallback = false) {
  return typeof value === 'boolean' ? value : fallback;
}

function slugify(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function localDateTime(value: string | null | undefined) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function isoDateTime(value: string) {
  return value ? new Date(value).toISOString() : null;
}

function settingMap(settings: SiteSetting[]) {
  return new Map(settings.map((setting) => [setting.key, setting]));
}

function entryToForm(item: CmsEntry): EntryForm {
  const data = asObject(item.content_json);
  const slot = stringValue(data.slot) as AdSlot;
  return {
    id: item.id,
    type: item.type,
    title: item.title,
    slug: item.slug,
    summary: item.summary ?? '',
    body: stringValue(data.body),
    location: stringValue(data.location),
    town: stringValue(data.town),
    phone: stringValue(data.phone),
    address: stringValue(data.address),
    ctaLabel: stringValue(data.cta_label),
    mediaUrl: item.media_url ?? '',
    externalUrl: item.external_url ?? '',
    sponsor: stringValue(data.sponsor),
    slot: adSlots.some(([value]) => value === slot) ? slot : '',
    status: item.status,
    featured: item.featured,
    startsAt: localDateTime(item.starts_at),
    endsAt: localDateTime(item.ends_at),
    sortOrder: String(item.sort_order ?? 0),
  };
}

function entryVisibility(entry: EntryForm) {
  if (entry.status !== 'published') return statusLabels[entry.status];
  const now = Date.now();
  if (entry.startsAt && new Date(entry.startsAt).getTime() > now) return 'Programado';
  if (entry.endsAt && new Date(entry.endsAt).getTime() < now) return 'Caducado';
  return 'Visible ahora';
}

function ContentPreview({ entry, onClose }: { entry: EntryForm; onClose: () => void }) {
  return <div className="site-admin-v2-preview-backdrop" role="dialog" aria-modal="true" aria-label="Vista previa del contenido">
    <div className="site-admin-v2-preview-modal">
      <div className="site-admin-v2-preview-head"><div><span>VISTA PREVIA</span><h2>{entry.title || 'Sin título'}</h2><small>{entryVisibility(entry)}</small></div><button className="site-admin-btn secondary" type="button" onClick={onClose}>Cerrar</button></div>
      {entry.mediaUrl ? <img className="site-admin-v2-preview-image" src={entry.mediaUrl} alt="" /> : <div className="site-admin-v2-preview-placeholder">Sin imagen</div>}
      <div className="site-admin-v2-preview-copy">
        <span className="site-admin-v2-preview-type">{typeLabels[entry.type] ?? entry.type}{entry.sponsor ? ` · ${entry.sponsor}` : ''}</span>
        <h3>{entry.title || 'Título del contenido'}</h3>
        <p>{entry.summary || 'Aquí aparecerá el resumen que verá el usuario.'}</p>
        {entry.body ? <p>{entry.body}</p> : null}
        {entry.location || entry.town || entry.address ? <small>{[entry.location, entry.town, entry.address].filter(Boolean).join(' · ')}</small> : null}
        {entry.ctaLabel ? <div className="site-admin-v2-preview-cta">{entry.ctaLabel} →</div> : null}
        {entry.slot ? <div className="site-admin-v2-preview-slot">Posición: {adSlots.find(([value]) => value === entry.slot)?.[1]}</div> : null}
      </div>
    </div>
  </div>;
}

export function SiteAdminConsoleV2() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [denied, setDenied] = useState(false);
  const [tab, setTab] = useState<Tab>('inicio');
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [settings, setSettings] = useState<SiteSetting[]>([]);
  const [home, setHome] = useState<HomeForm>(homeDefaults);
  const [seo, setSeo] = useState<SeoForm>(seoDefaults);
  const [entry, setEntry] = useState<EntryForm>(emptyEntry);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const role = session?.platform_access.role ?? null;
  const editable = role ? canEdit(role) : false;

  const hydrate = useCallback((siteSettings: SiteSetting[]) => {
    const map = settingMap(siteSettings);
    const hero = asObject(map.get('home.hero')?.value_json);
    const territory = asObject(map.get('home.territory_banner')?.value_json);
    const banner = asObject(map.get('alerts.banner')?.value_json);
    const seoSetting = asObject(map.get('site.seo')?.value_json);
    setHome({
      eyebrow: stringValue(hero.eyebrow, homeDefaults.eyebrow),
      title: stringValue(hero.title, homeDefaults.title),
      subtitle: stringValue(hero.subtitle, homeDefaults.subtitle),
      ctaLabel: stringValue(hero.cta_label, homeDefaults.ctaLabel),
      ctaHref: stringValue(hero.cta_href, homeDefaults.ctaHref),
      imageUrl: stringValue(hero.image_url),
      territoryTitle: stringValue(territory.title, homeDefaults.territoryTitle),
      territoryCtaLabel: stringValue(territory.cta_label, homeDefaults.territoryCtaLabel),
      territoryCtaHref: stringValue(territory.cta_href, homeDefaults.territoryCtaHref),
      bannerEnabled: booleanValue(banner.enabled),
      bannerText: stringValue(banner.text),
      bannerHref: stringValue(banner.href),
    });
    setSeo({
      title: stringValue(seoSetting.title, seoDefaults.title),
      description: stringValue(seoSetting.description, seoDefaults.description),
      ogImage: stringValue(seoSetting.og_image),
      robotsIndex: booleanValue(seoSetting.robots_index, true),
    });
  }, []);

  const load = useCallback(async () => {
    const [adminSession, contentPayload, settingsPayload] = await Promise.all([adminApi.session(), adminApi.content(), adminApi.settings()]);
    setSession(adminSession);
    setEntries(contentPayload.entries);
    setSettings(settingsPayload.settings);
    hydrate(settingsPayload.settings);
  }, [hydrate]);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    setDenied(false);
    setError(null);
    void load().catch((caught: unknown) => {
      const status = typeof caught === 'object' && caught && 'status' in caught ? Number((caught as { status?: unknown }).status) : 0;
      if (status === 403) { setDenied(true); setSession(null); return; }
      setError('No se ha podido cargar el editor web.');
    });
  }, [auth.status, load]);

  const activeEntries = useMemo(() => entries.filter((item) => {
    if (tab === 'actualidad') return item.type === 'news' || item.type === 'event';
    if (tab === 'territorio') return item.type === 'place' || item.type === 'mill' || item.type === 'directory';
    if (tab === 'publicidad') return item.type === 'promotion' || item.type === 'alert';
    return false;
  }), [entries, tab]);

  const counts = useMemo(() => ({
    published: entries.filter((item) => item.status === 'published').length,
    scheduled: entries.filter((item) => item.status === 'published' && item.starts_at && new Date(item.starts_at) > new Date()).length,
    ads: entries.filter((item) => item.type === 'promotion' && item.status !== 'archived').length,
    media: entries.filter((item) => item.media_url).length,
  }), [entries]);

  async function run(task: () => Promise<void>, success: string) {
    setBusy(true); setMessage(null); setError(null);
    try { await task(); setMessage(success); }
    catch (caught) { console.error(caught); setError('No se ha podido guardar el cambio. Revisa los datos e inténtalo de nuevo.'); }
    finally { setBusy(false); }
  }

  async function saveHome() {
    if (!editable) return;
    await run(async () => {
      await adminApi.saveSetting('home.hero', { eyebrow: home.eyebrow, title: home.title, subtitle: home.subtitle, cta_label: home.ctaLabel, cta_href: home.ctaHref, image_url: home.imageUrl }, 'Cabecera principal editable desde Administración', true);
      await adminApi.saveSetting('home.territory_banner', { title: home.territoryTitle, cta_label: home.territoryCtaLabel, cta_href: home.territoryCtaHref }, 'Bloque territorial de portada', true);
      await adminApi.saveSetting('alerts.banner', { enabled: home.bannerEnabled, text: home.bannerText, href: home.bannerHref }, 'Aviso superior público', true);
      await load();
    }, 'Portada actualizada.');
  }

  async function saveSeo() {
    if (!editable) return;
    await run(async () => {
      await adminApi.saveSetting('site.seo', { title: seo.title.trim(), description: seo.description.trim(), og_image: seo.ogImage.trim(), robots_index: seo.robotsIndex }, 'SEO público y metadatos sociales', true);
      await load();
    }, 'SEO actualizado.');
  }

  async function saveEntry() {
    if (!editable || !entry.title.trim() || !entry.slug.trim()) return;
    const payload = {
      type: entry.type,
      title: entry.title.trim(),
      slug: slugify(entry.slug),
      summary: entry.summary.trim() || null,
      content_json: { body: entry.body.trim(), location: entry.location.trim(), town: entry.town.trim(), phone: entry.phone.trim(), address: entry.address.trim(), cta_label: entry.ctaLabel.trim(), sponsor: entry.sponsor.trim(), slot: entry.slot || null },
      status: entry.status,
      featured: entry.featured,
      starts_at: isoDateTime(entry.startsAt),
      ends_at: isoDateTime(entry.endsAt),
      media_url: entry.mediaUrl.trim() || null,
      external_url: entry.externalUrl.trim() || null,
      sort_order: Number(entry.sortOrder) || 0,
    };
    await run(async () => {
      if (entry.id) await adminApi.updateContent(entry.id, payload); else await adminApi.createContent(payload);
      setEntry(emptyEntry); setPreviewOpen(false); await load();
    }, entry.id ? 'Contenido actualizado.' : 'Contenido creado.');
  }

  async function archiveEntry() {
    if (!editable || !entry.id) return;
    await run(async () => { await adminApi.archiveContent(entry.id!); setEntry(emptyEntry); await load(); }, 'Contenido archivado.');
  }

  function switchTab(next: Tab) { setTab(next); setEntry(emptyEntry); setPreviewOpen(false); setMessage(null); setError(null); }
  function newEntry(type: CmsEntryType) { setEntry({ ...emptyEntry, type }); }

  if (auth.status === 'loading') return <main className="site-admin-login"><div><strong>Comprobando acceso corporativo…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className="site-admin-login"><div><span className="site-admin-eyebrow">Mágina Olivo · Editor web</span><h1>Acceso corporativo</h1><p>Inicia sesión con una cuenta autorizada para administrar la web pública.</p><GoogleSignInButton /></div></main>;
  if (denied) return <main className="site-admin-login"><div><span className="site-admin-eyebrow">Acceso restringido</span><h1>Sin permisos de plataforma</h1><p>Esta cuenta puede usar Mágina Olivo, pero no editar la web pública.</p><button className="site-admin-btn secondary" onClick={() => void auth.logout()}>Usar otra cuenta</button></div></main>;
  if (!session || !role) return <main className="site-admin-login"><div><strong>Cargando editor…</strong>{error ? <p>{error}</p> : null}</div></main>;

  return <main className="site-admin-shell site-admin-v2">
    <header className="site-admin-topbar">
      <div><a className="site-admin-backlink" href="/admin">← Centro de control</a><h1>Editar web pública</h1><div className="site-admin-userline">{session.user.primary_email} · {role}</div></div>
      <div className="site-admin-actions"><a className="site-admin-btn secondary" href="/admin/media">Multimedia</a><a className="site-admin-btn secondary" href="/" target="_blank">Ver web</a><button className="site-admin-btn secondary" disabled={busy} onClick={() => void load()}>Actualizar</button></div>
    </header>

    <div className="site-admin-layout">
      <nav className="site-admin-nav" aria-label="Editor de la web">{(Object.keys(tabLabels) as Tab[]).map((item) => <button key={item} className={tab === item ? 'active' : ''} onClick={() => switchTab(item)}>{tabLabels[item]}</button>)}</nav>
      <section className="site-admin-panel">
        {message ? <div className="site-admin-notice success">{message}</div> : null}
        {error ? <div className="site-admin-notice error">{error}</div> : null}
        <div className="site-admin-kpis"><div><strong>{counts.published}</strong><span>publicados</span></div><div><strong>{counts.scheduled}</strong><span>programados</span></div><div><strong>{counts.ads}</strong><span>promociones</span></div><div><strong>{counts.media}</strong><span>con imagen</span></div></div>

        {tab === 'inicio' ? <div className="site-admin-grid">
          <div className="site-admin-stack">
            <article className="site-admin-card"><h3>Cabecera de Inicio</h3><p className="site-admin-muted">Todos los cambios se reflejan en la portada pública.</p><div className="site-admin-fields">
              <label>Texto superior<input disabled={!editable} value={home.eyebrow} onChange={(event) => setHome({ ...home, eyebrow: event.target.value })} /></label>
              <label>Título<textarea disabled={!editable} rows={3} value={home.title} onChange={(event) => setHome({ ...home, title: event.target.value })} /></label>
              <label>Descripción<textarea disabled={!editable} rows={3} value={home.subtitle} onChange={(event) => setHome({ ...home, subtitle: event.target.value })} /></label>
              <div className="site-admin-fields two"><label>Botón<input disabled={!editable} value={home.ctaLabel} onChange={(event) => setHome({ ...home, ctaLabel: event.target.value })} /></label><label>Destino<input disabled={!editable} value={home.ctaHref} onChange={(event) => setHome({ ...home, ctaHref: event.target.value })} /></label></div>
              <AdminMediaPicker label="Imagen de portada" value={home.imageUrl} disabled={!editable} onChange={(value) => setHome({ ...home, imageUrl: value })} />
            </div></article>
            <article className="site-admin-card"><h3>Banner territorial</h3><div className="site-admin-fields"><label>Título<input disabled={!editable} value={home.territoryTitle} onChange={(event) => setHome({ ...home, territoryTitle: event.target.value })} /></label><div className="site-admin-fields two"><label>Botón<input disabled={!editable} value={home.territoryCtaLabel} onChange={(event) => setHome({ ...home, territoryCtaLabel: event.target.value })} /></label><label>Destino<input disabled={!editable} value={home.territoryCtaHref} onChange={(event) => setHome({ ...home, territoryCtaHref: event.target.value })} /></label></div></div></article>
            <article className="site-admin-card"><h3>Aviso superior</h3><div className="site-admin-fields"><label className="site-admin-check"><input disabled={!editable} type="checkbox" checked={home.bannerEnabled} onChange={(event) => setHome({ ...home, bannerEnabled: event.target.checked })} /> Mostrar aviso en la portada</label><label>Texto<input disabled={!editable} value={home.bannerText} onChange={(event) => setHome({ ...home, bannerText: event.target.value })} /></label><label>Enlace opcional<input disabled={!editable} value={home.bannerHref} onChange={(event) => setHome({ ...home, bannerHref: event.target.value })} /></label></div></article>
            {editable ? <button className="site-admin-btn" disabled={busy} onClick={() => void saveHome()}>Guardar portada</button> : null}
          </div>
          <aside className="site-admin-stack"><div className="site-admin-preview"><div className="site-admin-preview-media" style={home.imageUrl ? { backgroundImage: `url(${home.imageUrl})` } : undefined}><div><span>{home.eyebrow}</span><h3>{home.title}</h3><p>{home.subtitle}</p></div></div><div className="site-admin-preview-body"><strong>{home.ctaLabel}</strong><span>Vista previa</span></div></div>{home.bannerEnabled ? <div className="site-admin-ad-preview"><span>AVISO ACTIVO</span><h4>{home.bannerText || 'Escribe el aviso'}</h4></div> : null}</aside>
        </div> : null}

        {tab === 'seo' ? <div className="site-admin-grid">
          <article className="site-admin-card"><h3>SEO y redes sociales</h3><p className="site-admin-muted">Controla título, descripción, imagen social e indexación pública.</p><div className="site-admin-fields"><label>Título SEO<input disabled={!editable} maxLength={70} value={seo.title} onChange={(event) => setSeo({ ...seo, title: event.target.value })} /></label><small className="site-admin-help">{seo.title.length}/70 caracteres</small><label>Descripción SEO<textarea disabled={!editable} rows={4} maxLength={170} value={seo.description} onChange={(event) => setSeo({ ...seo, description: event.target.value })} /></label><small className="site-admin-help">{seo.description.length}/170 caracteres</small><AdminMediaPicker label="Imagen social / Open Graph" value={seo.ogImage} disabled={!editable} onChange={(value) => setSeo({ ...seo, ogImage: value })} /><label className="site-admin-check"><input disabled={!editable} type="checkbox" checked={seo.robotsIndex} onChange={(event) => setSeo({ ...seo, robotsIndex: event.target.checked })} /> Permitir indexación en buscadores</label></div>{editable ? <button className="site-admin-btn" disabled={busy || !seo.title.trim()} onClick={() => void saveSeo()}>Guardar SEO</button> : null}</article>
          <aside className="site-admin-stack"><div className="site-admin-v2-search-preview"><span>maginaolivo.es</span><h3>{seo.title || 'Título SEO'}</h3><p>{seo.description || 'Descripción que aparecerá en buscadores.'}</p></div><div className="site-admin-card"><h3>Estado</h3><p className="site-admin-muted">{seo.robotsIndex ? 'La web está preparada para indexarse.' : 'La web indicará noindex,nofollow.'}</p>{seo.ogImage ? <img className="site-admin-v2-og-image" src={seo.ogImage} alt="Imagen social" /> : null}</div></aside>
        </div> : null}

        {tab !== 'inicio' && tab !== 'seo' ? <div className="site-admin-grid">
          <article className="site-admin-card"><div className="site-admin-split-title"><div><h3>{tabLabels[tab]}</h3><p className="site-admin-muted">{activeEntries.length} elementos.</p></div>{editable ? <div className="site-admin-actions">{tab === 'actualidad' ? <><button className="site-admin-btn secondary" onClick={() => newEntry('news')}>+ Noticia</button><button className="site-admin-btn secondary" onClick={() => newEntry('event')}>+ Evento</button></> : null}{tab === 'territorio' ? <><button className="site-admin-btn secondary" onClick={() => newEntry('place')}>+ Lugar</button><button className="site-admin-btn secondary" onClick={() => newEntry('mill')}>+ Cooperativa</button><button className="site-admin-btn secondary" onClick={() => newEntry('directory')}>+ Directorio</button></> : null}{tab === 'publicidad' ? <><button className="site-admin-btn secondary" onClick={() => newEntry('promotion')}>+ Promoción</button><button className="site-admin-btn secondary" onClick={() => newEntry('alert')}>+ Aviso</button></> : null}</div> : null}</div><div className="site-admin-list">{activeEntries.map((item) => <button className={`site-admin-row ${entry.id === item.id ? 'selected' : ''}`} key={item.id} onClick={() => setEntry(entryToForm(item))}><span><strong>{item.title}</strong><small>{typeLabels[item.type] ?? item.type} · /{item.slug}</small></span><span className={`site-admin-badge ${item.status}`}>{statusLabels[item.status]}</span></button>)}{!activeEntries.length ? <div className="site-admin-empty">Todavía no hay contenido.</div> : null}</div></article>

          <article className="site-admin-card"><h3>{entry.id ? 'Editar elemento' : 'Nuevo elemento'}</h3><p className="site-admin-muted">Previsualiza antes de guardar o publicar.</p><div className="site-admin-fields">
            <label>Tipo<select disabled={!editable} value={entry.type} onChange={(event) => setEntry({ ...entry, type: event.target.value as CmsEntryType })}>{Object.entries(typeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <label>Título<input disabled={!editable} value={entry.title} onChange={(event) => setEntry({ ...entry, title: event.target.value, slug: entry.id ? entry.slug : slugify(event.target.value) })} /></label>
            <label>Slug<input disabled={!editable} value={entry.slug} onChange={(event) => setEntry({ ...entry, slug: slugify(event.target.value) })} /></label>
            <label>Resumen<textarea disabled={!editable} rows={3} value={entry.summary} onChange={(event) => setEntry({ ...entry, summary: event.target.value })} /></label>
            <label>Texto ampliado<textarea disabled={!editable} rows={5} value={entry.body} onChange={(event) => setEntry({ ...entry, body: event.target.value })} /></label>
            {(entry.type === 'event' || entry.type === 'place') ? <label>Ubicación<input disabled={!editable} value={entry.location} onChange={(event) => setEntry({ ...entry, location: event.target.value })} /></label> : null}
            {(entry.type === 'place' || entry.type === 'mill' || entry.type === 'directory') ? <div className="site-admin-fields two"><label>Pueblo<input disabled={!editable} value={entry.town} onChange={(event) => setEntry({ ...entry, town: event.target.value })} /></label><label>Teléfono<input disabled={!editable} value={entry.phone} onChange={(event) => setEntry({ ...entry, phone: event.target.value })} /></label></div> : null}
            {(entry.type === 'mill' || entry.type === 'directory') ? <label>Dirección<input disabled={!editable} value={entry.address} onChange={(event) => setEntry({ ...entry, address: event.target.value })} /></label> : null}
            <AdminMediaPicker label="Imagen" value={entry.mediaUrl} disabled={!editable} onChange={(value) => setEntry({ ...entry, mediaUrl: value })} />
            <label>Enlace / destino<input disabled={!editable} value={entry.externalUrl} onChange={(event) => setEntry({ ...entry, externalUrl: event.target.value })} /></label>
            {(entry.type === 'promotion' || entry.type === 'alert') ? <label>Texto del botón<input disabled={!editable} value={entry.ctaLabel} onChange={(event) => setEntry({ ...entry, ctaLabel: event.target.value })} /></label> : null}
            {entry.type === 'promotion' ? <><div className="site-admin-fields two"><label>Patrocinador<input disabled={!editable} value={entry.sponsor} onChange={(event) => setEntry({ ...entry, sponsor: event.target.value })} /></label><label>Posición<select disabled={!editable} value={entry.slot} onChange={(event) => setEntry({ ...entry, slot: event.target.value as AdSlot })}>{adSlots.map(([value, label]) => <option key={value || 'none'} value={value}>{label}</option>)}</select></label></div><p className="site-admin-help">Las posiciones fijas se muestran automáticamente en Inicio o Explorar mientras la promoción esté publicada y vigente.</p></> : null}
            <div className="site-admin-schedule"><strong>Programación</strong><div className="site-admin-fields two"><label>Desde<input disabled={!editable} type="datetime-local" value={entry.startsAt} onChange={(event) => setEntry({ ...entry, startsAt: event.target.value })} /></label><label>Hasta<input disabled={!editable} type="datetime-local" value={entry.endsAt} onChange={(event) => setEntry({ ...entry, endsAt: event.target.value })} /></label></div></div>
            <div className="site-admin-fields two"><label>Estado<select disabled={!editable} value={entry.status} onChange={(event) => setEntry({ ...entry, status: event.target.value as CmsEntryStatus })}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>Orden<input disabled={!editable} type="number" value={entry.sortOrder} onChange={(event) => setEntry({ ...entry, sortOrder: event.target.value })} /></label></div>
            <label className="site-admin-check"><input disabled={!editable} type="checkbox" checked={entry.featured} onChange={(event) => setEntry({ ...entry, featured: event.target.checked })} /> Destacar en la web</label>
          </div><div className="site-admin-inline-actions"><button className="site-admin-btn secondary" type="button" disabled={!entry.title.trim()} onClick={() => setPreviewOpen(true)}>Vista previa</button>{editable ? <button className="site-admin-btn" disabled={busy || !entry.title.trim() || !entry.slug.trim()} onClick={() => void saveEntry()}>Guardar</button> : null}{editable && entry.id ? <button className="site-admin-btn danger" disabled={busy} onClick={() => void archiveEntry()}>Archivar</button> : null}</div>
          </article>
        </div> : null}

        <div className="site-admin-v2-public-settings">Ajustes públicos activos: {settings.filter((item) => item.is_public).length}</div>
      </section>
    </div>
    {previewOpen ? <ContentPreview entry={entry} onClose={() => setPreviewOpen(false)} /> : null}
  </main>;
}
