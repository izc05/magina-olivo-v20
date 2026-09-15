'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { adminApi, type AdminSession, type CmsEntry, type CmsEntryStatus, type CmsEntryType, type PlatformAdminRole, type SiteSetting } from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';

type Tab = 'inicio' | 'actualidad' | 'territorio' | 'publicidad' | 'configuracion';

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

type ConfigForm = {
  supportEmail: string;
  supportPhone: string;
  instagram: string;
  facebook: string;
  legalName: string;
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

const configDefaults: ConfigForm = {
  supportEmail: '',
  supportPhone: '',
  instagram: '',
  facebook: '',
  legalName: 'Mágina Olivo',
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
  publicidad: 'Publicidad y avisos',
  configuracion: 'Configuración pública',
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

function canEdit(role: PlatformAdminRole) {
  return role === 'super_admin' || role === 'admin' || role === 'editor';
}

function slugify(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
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

function entryToForm(entry: CmsEntry): EntryForm {
  const data = asObject(entry.content_json);
  return {
    id: entry.id,
    type: entry.type,
    title: entry.title,
    slug: entry.slug,
    summary: entry.summary ?? '',
    body: stringValue(data.body),
    location: stringValue(data.location),
    town: stringValue(data.town),
    phone: stringValue(data.phone),
    address: stringValue(data.address),
    ctaLabel: stringValue(data.cta_label),
    mediaUrl: entry.media_url ?? '',
    externalUrl: entry.external_url ?? '',
    status: entry.status,
    featured: entry.featured,
    startsAt: localDateTime(entry.starts_at),
    endsAt: localDateTime(entry.ends_at),
    sortOrder: String(entry.sort_order ?? 0),
  };
}

function settingMap(settings: SiteSetting[]) {
  return new Map(settings.map((setting) => [setting.key, setting]));
}

export function SiteAdminConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [denied, setDenied] = useState(false);
  const [tab, setTab] = useState<Tab>('inicio');
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [settings, setSettings] = useState<SiteSetting[]>([]);
  const [home, setHome] = useState<HomeForm>(homeDefaults);
  const [config, setConfig] = useState<ConfigForm>(configDefaults);
  const [entry, setEntry] = useState<EntryForm>(emptyEntry);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const role = session?.platform_access.role ?? null;
  const editable = role ? canEdit(role) : false;

  const hydrateForms = useCallback((siteSettings: SiteSetting[]) => {
    const map = settingMap(siteSettings);
    const hero = asObject(map.get('home.hero')?.value_json);
    const territory = asObject(map.get('home.territory_banner')?.value_json);
    const banner = asObject(map.get('alerts.banner')?.value_json);
    const contact = asObject(map.get('site.contact')?.value_json);
    const social = asObject(map.get('site.social')?.value_json);
    const identity = asObject(map.get('site.identity')?.value_json);

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

    setConfig({
      supportEmail: stringValue(contact.email),
      supportPhone: stringValue(contact.phone),
      instagram: stringValue(social.instagram),
      facebook: stringValue(social.facebook),
      legalName: stringValue(identity.legal_name, configDefaults.legalName),
    });
  }, []);

  const load = useCallback(async () => {
    const [adminSession, contentPayload, settingsPayload] = await Promise.all([
      adminApi.session(),
      adminApi.content(),
      adminApi.settings(),
    ]);
    setSession(adminSession);
    setEntries(contentPayload.entries);
    setSettings(settingsPayload.settings);
    hydrateForms(settingsPayload.settings);
  }, [hydrateForms]);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    setDenied(false);
    setError(null);
    void load().catch((caught: unknown) => {
      const status = typeof caught === 'object' && caught && 'status' in caught ? Number((caught as { status?: unknown }).status) : 0;
      if (status === 403) {
        setDenied(true);
        setSession(null);
        return;
      }
      setError('No se ha podido cargar el editor de la web.');
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
    promotions: entries.filter((item) => item.type === 'promotion' && item.status === 'published').length,
    territory: entries.filter((item) => ['place', 'mill', 'directory'].includes(item.type) && item.status !== 'archived').length,
  }), [entries]);

  async function run(task: () => Promise<void>, success: string) {
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      await task();
      setMessage(success);
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido guardar el cambio. Revisa los datos e inténtalo de nuevo.');
    } finally {
      setBusy(false);
    }
  }

  async function refresh() {
    await load();
  }

  async function saveHome() {
    if (!editable) return;
    await run(async () => {
      await adminApi.saveSetting('home.hero', {
        eyebrow: home.eyebrow,
        title: home.title,
        subtitle: home.subtitle,
        cta_label: home.ctaLabel,
        cta_href: home.ctaHref,
        image_url: home.imageUrl,
      }, 'Cabecera principal editable desde Administración', true);
      await adminApi.saveSetting('home.territory_banner', {
        title: home.territoryTitle,
        cta_label: home.territoryCtaLabel,
        cta_href: home.territoryCtaHref,
      }, 'Bloque territorial de la portada', true);
      await adminApi.saveSetting('alerts.banner', {
        enabled: home.bannerEnabled,
        text: home.bannerText,
        href: home.bannerHref,
      }, 'Aviso superior de la web', true);
      await refresh();
    }, 'Portada actualizada.');
  }

  async function saveConfig() {
    if (!editable) return;
    await run(async () => {
      await adminApi.saveSetting('site.contact', { email: config.supportEmail, phone: config.supportPhone }, 'Contacto público', true);
      await adminApi.saveSetting('site.social', { instagram: config.instagram, facebook: config.facebook }, 'Redes sociales públicas', true);
      await adminApi.saveSetting('site.identity', { legal_name: config.legalName }, 'Identidad pública de la plataforma', true);
      await refresh();
    }, 'Configuración pública actualizada.');
  }

  async function saveEntry() {
    if (!editable || !entry.title.trim() || !entry.slug.trim()) return;
    const payload = {
      type: entry.type,
      title: entry.title.trim(),
      slug: slugify(entry.slug),
      summary: entry.summary.trim() || null,
      content_json: {
        body: entry.body.trim(),
        location: entry.location.trim(),
        town: entry.town.trim(),
        phone: entry.phone.trim(),
        address: entry.address.trim(),
        cta_label: entry.ctaLabel.trim(),
      },
      status: entry.status,
      featured: entry.featured,
      starts_at: isoDateTime(entry.startsAt),
      ends_at: isoDateTime(entry.endsAt),
      media_url: entry.mediaUrl.trim() || null,
      external_url: entry.externalUrl.trim() || null,
      sort_order: Number(entry.sortOrder) || 0,
    };
    await run(async () => {
      if (entry.id) await adminApi.updateContent(entry.id, payload);
      else await adminApi.createContent(payload);
      setEntry(emptyEntry);
      await refresh();
    }, entry.id ? 'Contenido actualizado.' : 'Contenido creado.');
  }

  async function archiveEntry() {
    if (!entry.id || !editable) return;
    await run(async () => {
      await adminApi.archiveContent(entry.id!);
      setEntry(emptyEntry);
      await refresh();
    }, 'Contenido archivado.');
  }

  function startNew(type: CmsEntryType) {
    setEntry({ ...emptyEntry, type });
  }

  if (auth.status === 'loading') {
    return <main className="site-admin-login"><div><strong>Comprobando acceso corporativo…</strong></div></main>;
  }

  if (auth.status === 'anonymous') {
    return <main className="site-admin-login"><div><span className="site-admin-eyebrow">Mágina Olivo · Editor web</span><h1>Acceso corporativo</h1><p>Inicia sesión con una cuenta autorizada para administrar el contenido público.</p><GoogleSignInButton /></div></main>;
  }

  if (denied) {
    return <main className="site-admin-login"><div><span className="site-admin-eyebrow">Acceso restringido</span><h1>Sin permisos de plataforma</h1><p>Esta cuenta puede usar Mágina Olivo, pero no editar la web pública.</p><button className="site-admin-btn secondary" onClick={() => void auth.logout()}>Usar otra cuenta</button></div></main>;
  }

  if (!session || !role) {
    return <main className="site-admin-login"><div><strong>Cargando editor…</strong>{error ? <p>{error}</p> : null}</div></main>;
  }

  return (
    <main className="site-admin-shell">
      <header className="site-admin-topbar">
        <div><a className="site-admin-backlink" href="/admin">← Centro de control</a><h1>Editar web pública</h1><div className="site-admin-userline">{session.user.primary_email} · {role}</div></div>
        <div className="site-admin-actions"><a className="site-admin-btn secondary" href="/" target="_blank">Ver web</a><button className="site-admin-btn secondary" disabled={busy} onClick={() => void refresh()}>Actualizar</button></div>
      </header>

      <div className="site-admin-layout">
        <nav className="site-admin-nav" aria-label="Editor de la web">
          {(Object.keys(tabLabels) as Tab[]).map((item) => <button key={item} className={tab === item ? 'active' : ''} onClick={() => { setTab(item); setEntry(emptyEntry); }}>{tabLabels[item]}</button>)}
        </nav>

        <section className="site-admin-panel">
          {message ? <div className="site-admin-notice success">{message}</div> : null}
          {error ? <div className="site-admin-notice error">{error}</div> : null}

          <div className="site-admin-kpis">
            <div><strong>{counts.published}</strong><span>publicados</span></div>
            <div><strong>{counts.scheduled}</strong><span>programados</span></div>
            <div><strong>{counts.promotions}</strong><span>promociones activas</span></div>
            <div><strong>{counts.territory}</strong><span>fichas de territorio</span></div>
          </div>

          {tab === 'inicio' ? (
            <div className="site-admin-grid">
              <div className="site-admin-stack">
                <article className="site-admin-card">
                  <div className="site-admin-split-title"><div><h3>Cabecera de Inicio</h3><p className="site-admin-muted">Edita el mensaje principal sin tocar código.</p></div></div>
                  <div className="site-admin-fields">
                    <label>Texto superior<input disabled={!editable} value={home.eyebrow} onChange={(e) => setHome({ ...home, eyebrow: e.target.value })} /></label>
                    <label>Título<textarea disabled={!editable} rows={3} value={home.title} onChange={(e) => setHome({ ...home, title: e.target.value })} /></label>
                    <label>Descripción<textarea disabled={!editable} rows={3} value={home.subtitle} onChange={(e) => setHome({ ...home, subtitle: e.target.value })} /></label>
                    <div className="site-admin-fields two"><label>Texto del botón<input disabled={!editable} value={home.ctaLabel} onChange={(e) => setHome({ ...home, ctaLabel: e.target.value })} /></label><label>Destino<input disabled={!editable} value={home.ctaHref} onChange={(e) => setHome({ ...home, ctaHref: e.target.value })} /></label></div>
                    <label>Imagen de portada (URL)<input disabled={!editable} placeholder="https://…" value={home.imageUrl} onChange={(e) => setHome({ ...home, imageUrl: e.target.value })} /></label>
                  </div>
                </article>

                <article className="site-admin-card">
                  <h3>Banner territorial</h3>
                  <div className="site-admin-fields">
                    <label>Título<input disabled={!editable} value={home.territoryTitle} onChange={(e) => setHome({ ...home, territoryTitle: e.target.value })} /></label>
                    <div className="site-admin-fields two"><label>Botón<input disabled={!editable} value={home.territoryCtaLabel} onChange={(e) => setHome({ ...home, territoryCtaLabel: e.target.value })} /></label><label>Destino<input disabled={!editable} value={home.territoryCtaHref} onChange={(e) => setHome({ ...home, territoryCtaHref: e.target.value })} /></label></div>
                  </div>
                </article>

                <article className="site-admin-card">
                  <h3>Aviso superior</h3>
                  <div className="site-admin-fields"><label className="site-admin-check"><input disabled={!editable} type="checkbox" checked={home.bannerEnabled} onChange={(e) => setHome({ ...home, bannerEnabled: e.target.checked })} /> Mostrar aviso en la portada</label><label>Texto<input disabled={!editable} value={home.bannerText} onChange={(e) => setHome({ ...home, bannerText: e.target.value })} /></label><label>Enlace opcional<input disabled={!editable} value={home.bannerHref} onChange={(e) => setHome({ ...home, bannerHref: e.target.value })} /></label></div>
                </article>

                {editable ? <button className="site-admin-btn" disabled={busy} onClick={() => void saveHome()}>Guardar portada</button> : null}
              </div>

              <aside className="site-admin-stack">
                <div className="site-admin-preview"><div className="site-admin-preview-media" style={home.imageUrl ? { backgroundImage: `url(${home.imageUrl})` } : undefined}><div><span>{home.eyebrow || 'MÁGINA OLIVO'}</span><h3>{home.title || 'Título de portada'}</h3><p>{home.subtitle || 'Descripción de portada'}</p></div></div><div className="site-admin-preview-body"><strong>{home.ctaLabel || 'Botón principal'}</strong><span>Vista previa</span></div></div>
                {home.bannerEnabled ? <div className="site-admin-ad-preview"><span>AVISO ACTIVO</span><h4>{home.bannerText || 'Escribe el texto del aviso'}</h4><small>{home.bannerHref || 'Sin enlace'}</small></div> : null}
              </aside>
            </div>
          ) : null}

          {tab === 'configuracion' ? (
            <div className="site-admin-grid">
              <article className="site-admin-card">
                <h3>Contacto y redes</h3><p className="site-admin-muted">Datos públicos reutilizables por el pie de página, ayuda y futuras pantallas.</p>
                <div className="site-admin-fields"><label>Nombre legal / público<input disabled={!editable} value={config.legalName} onChange={(e) => setConfig({ ...config, legalName: e.target.value })} /></label><div className="site-admin-fields two"><label>Email de contacto<input disabled={!editable} type="email" value={config.supportEmail} onChange={(e) => setConfig({ ...config, supportEmail: e.target.value })} /></label><label>Teléfono<input disabled={!editable} value={config.supportPhone} onChange={(e) => setConfig({ ...config, supportPhone: e.target.value })} /></label></div><label>Instagram<input disabled={!editable} placeholder="https://instagram.com/…" value={config.instagram} onChange={(e) => setConfig({ ...config, instagram: e.target.value })} /></label><label>Facebook<input disabled={!editable} placeholder="https://facebook.com/…" value={config.facebook} onChange={(e) => setConfig({ ...config, facebook: e.target.value })} /></label></div>
                {editable ? <div className="site-admin-inline-actions"><button className="site-admin-btn" disabled={busy} onClick={() => void saveConfig()}>Guardar configuración</button></div> : null}
              </article>
              <aside className="site-admin-card"><h3>Ajustes públicos activos</h3><p className="site-admin-muted">Solo se muestran aquí las claves públicas. Los ajustes privados siguen reservados al centro de control.</p><div className="site-admin-list">{settings.filter((item) => item.is_public).map((item) => <div className="site-admin-row" key={item.key}><span><strong>{item.key}</strong><small>{item.description ?? 'Sin descripción'}</small></span><span className="site-admin-badge published">Público</span></div>)}</div></aside>
            </div>
          ) : null}

          {tab !== 'inicio' && tab !== 'configuracion' ? (
            <div className="site-admin-grid">
              <article className="site-admin-card">
                <div className="site-admin-split-title"><div><h3>{tabLabels[tab]}</h3><p className="site-admin-muted">{activeEntries.length} elementos en esta sección.</p></div>{editable ? <div className="site-admin-actions">{tab === 'actualidad' ? <><button className="site-admin-btn secondary" onClick={() => startNew('news')}>+ Noticia</button><button className="site-admin-btn secondary" onClick={() => startNew('event')}>+ Evento</button></> : null}{tab === 'territorio' ? <><button className="site-admin-btn secondary" onClick={() => startNew('place')}>+ Lugar</button><button className="site-admin-btn secondary" onClick={() => startNew('mill')}>+ Cooperativa</button></> : null}{tab === 'publicidad' ? <><button className="site-admin-btn secondary" onClick={() => startNew('promotion')}>+ Promoción</button><button className="site-admin-btn secondary" onClick={() => startNew('alert')}>+ Aviso</button></> : null}</div> : null}</div>
                <div className="site-admin-list">{activeEntries.map((item) => <button className={`site-admin-row ${entry.id === item.id ? 'selected' : ''}`} key={item.id} onClick={() => setEntry(entryToForm(item))}><span><strong>{item.title}</strong><small>{typeLabels[item.type] ?? item.type} · /{item.slug}</small></span><span className={`site-admin-badge ${item.status}`}>{statusLabels[item.status]}</span></button>)}{!activeEntries.length ? <div className="site-admin-empty">Todavía no hay contenido en esta sección.</div> : null}</div>
              </article>

              <article className="site-admin-card">
                <h3>{entry.id ? 'Editar elemento' : 'Nuevo elemento'}</h3><p className="site-admin-muted">Formulario visual. Los detalles se almacenan de forma estructurada automáticamente.</p>
                <div className="site-admin-fields">
                  <label>Tipo<select disabled={!editable} value={entry.type} onChange={(e) => setEntry({ ...entry, type: e.target.value as CmsEntryType })}>{Object.entries(typeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                  <label>Título<input disabled={!editable} value={entry.title} onChange={(e) => setEntry({ ...entry, title: e.target.value, slug: entry.id ? entry.slug : slugify(e.target.value) })} /></label>
                  <label>Dirección web / slug<input disabled={!editable} value={entry.slug} onChange={(e) => setEntry({ ...entry, slug: slugify(e.target.value) })} /></label>
                  <label>Resumen<textarea disabled={!editable} rows={3} value={entry.summary} onChange={(e) => setEntry({ ...entry, summary: e.target.value })} /></label>
                  <label>Texto / descripción ampliada<textarea disabled={!editable} rows={5} value={entry.body} onChange={(e) => setEntry({ ...entry, body: e.target.value })} /></label>
                  {(entry.type === 'event' || entry.type === 'place') ? <label>Ubicación<input disabled={!editable} value={entry.location} onChange={(e) => setEntry({ ...entry, location: e.target.value })} /></label> : null}
                  {(entry.type === 'place' || entry.type === 'mill' || entry.type === 'directory') ? <div className="site-admin-fields two"><label>Pueblo<input disabled={!editable} value={entry.town} onChange={(e) => setEntry({ ...entry, town: e.target.value })} /></label><label>Teléfono<input disabled={!editable} value={entry.phone} onChange={(e) => setEntry({ ...entry, phone: e.target.value })} /></label></div> : null}
                  {(entry.type === 'mill' || entry.type === 'directory') ? <label>Dirección<input disabled={!editable} value={entry.address} onChange={(e) => setEntry({ ...entry, address: e.target.value })} /></label> : null}
                  <div className="site-admin-fields two"><label>Imagen (URL)<input disabled={!editable} value={entry.mediaUrl} onChange={(e) => setEntry({ ...entry, mediaUrl: e.target.value })} /></label><label>Enlace / destino<input disabled={!editable} value={entry.externalUrl} onChange={(e) => setEntry({ ...entry, externalUrl: e.target.value })} /></label></div>
                  {(entry.type === 'promotion' || entry.type === 'alert') ? <label>Texto del botón<input disabled={!editable} value={entry.ctaLabel} onChange={(e) => setEntry({ ...entry, ctaLabel: e.target.value })} /></label> : null}
                  <div className="site-admin-schedule"><strong>Programación</strong><div className="site-admin-fields two"><label>Desde<input disabled={!editable} type="datetime-local" value={entry.startsAt} onChange={(e) => setEntry({ ...entry, startsAt: e.target.value })} /></label><label>Hasta<input disabled={!editable} type="datetime-local" value={entry.endsAt} onChange={(e) => setEntry({ ...entry, endsAt: e.target.value })} /></label></div><p className="site-admin-help">Si dejas las fechas vacías, el contenido publicado permanece visible hasta que lo archives.</p></div>
                  <div className="site-admin-fields two"><label>Estado<select disabled={!editable} value={entry.status} onChange={(e) => setEntry({ ...entry, status: e.target.value as CmsEntryStatus })}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>Orden<input disabled={!editable} type="number" value={entry.sortOrder} onChange={(e) => setEntry({ ...entry, sortOrder: e.target.value })} /></label></div>
                  <label className="site-admin-check"><input disabled={!editable} type="checkbox" checked={entry.featured} onChange={(e) => setEntry({ ...entry, featured: e.target.checked })} /> Destacar en la web</label>
                </div>
                {editable ? <div className="site-admin-inline-actions"><button className="site-admin-btn" disabled={busy || !entry.title || !entry.slug} onClick={() => void saveEntry()}>Guardar</button>{entry.id ? <button className="site-admin-btn danger" disabled={busy} onClick={() => void archiveEntry()}>Archivar</button> : null}</div> : null}
                {entry.title ? <div className="site-admin-ad-preview" style={{ marginTop: 16 }}><span>{(typeLabels[entry.type] ?? entry.type).toUpperCase()}</span><h4>{entry.title}</h4><p>{entry.summary || 'Aquí aparecerá el resumen.'}</p>{entry.startsAt || entry.endsAt ? <small>{entry.startsAt ? `Desde ${new Date(entry.startsAt).toLocaleString('es-ES')}` : 'Desde ahora'} · {entry.endsAt ? `hasta ${new Date(entry.endsAt).toLocaleString('es-ES')}` : 'sin fecha final'}</small> : null}</div> : null}
              </article>
            </div>
          ) : null}
        </section>
      </div>
    </main>
  );
}
