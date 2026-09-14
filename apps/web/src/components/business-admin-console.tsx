'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import {
  businessAdminApi,
  type AdminBusiness,
  type BusinessAdminCatalog,
  type BusinessWriteInput,
  type TerritoryCatalog,
} from '@/lib/business-admin-source';
import styles from './business-admin.module.css';

type Section = 'businesses' | 'claims' | 'categories';

type FormState = {
  id: string | null;
  name: string;
  slug: string;
  shortDescription: string;
  description: string;
  placeId: string;
  address: string;
  latitude: string;
  longitude: string;
  phone: string;
  whatsapp: string;
  email: string;
  website: string;
  logoUrl: string;
  coverImageUrl: string;
  status: 'draft' | 'published' | 'archived';
  verificationStatus: 'unverified' | 'pending' | 'verified' | 'rejected';
  commercialPlan: 'free' | 'featured' | 'premium' | 'sponsor';
  featured: boolean;
  sponsored: boolean;
  priority: string;
  campaignStart: string;
  campaignEnd: string;
  categorySlugs: string[];
  primaryCategorySlug: string;
};

const emptyForm: FormState = {
  id: null,
  name: '',
  slug: '',
  shortDescription: '',
  description: '',
  placeId: '',
  address: '',
  latitude: '',
  longitude: '',
  phone: '',
  whatsapp: '',
  email: '',
  website: '',
  logoUrl: '',
  coverImageUrl: '',
  status: 'draft',
  verificationStatus: 'unverified',
  commercialPlan: 'free',
  featured: false,
  sponsored: false,
  priority: '0',
  campaignStart: '',
  campaignEnd: '',
  categorySlugs: [],
  primaryCategorySlug: '',
};

function slugify(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function localDateTime(value: string | null) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formFromBusiness(business: AdminBusiness): FormState {
  return {
    id: business.id,
    name: business.name,
    slug: business.slug,
    shortDescription: business.shortDescription ?? '',
    description: business.description ?? '',
    placeId: business.placeId ?? '',
    address: business.address ?? '',
    latitude: business.location ? String(business.location.latitude) : '',
    longitude: business.location ? String(business.location.longitude) : '',
    phone: business.phone ?? '',
    whatsapp: business.whatsapp ?? '',
    email: business.email ?? '',
    website: business.website ?? '',
    logoUrl: business.logoUrl ?? '',
    coverImageUrl: business.coverImageUrl ?? '',
    status: business.status,
    verificationStatus: business.verificationStatus,
    commercialPlan: business.commercialPlan,
    featured: business.featured,
    sponsored: business.sponsored,
    priority: String(business.priority),
    campaignStart: localDateTime(business.campaignStart),
    campaignEnd: localDateTime(business.campaignEnd),
    categorySlugs: business.categories.map((category) => category.slug),
    primaryCategorySlug: business.categories.find((category) => category.primary)?.slug ?? '',
  };
}

function parseCoordinate(value: string, min: number, max: number) {
  if (!value.trim()) return null;
  const parsed = Number(value.replace(',', '.'));
  return Number.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : undefined;
}

export function BusinessAdminConsole() {
  const auth = useAuth();
  const [catalog, setCatalog] = useState<BusinessAdminCatalog | null>(null);
  const [territory, setTerritory] = useState<TerritoryCatalog | null>(null);
  const [section, setSection] = useState<Section>('businesses');
  const [form, setForm] = useState<FormState>(emptyForm);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [denied, setDenied] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [mediaKind, setMediaKind] = useState<'photo' | 'logo' | 'cover' | 'video' | 'document'>('photo');
  const [mediaUrl, setMediaUrl] = useState('');
  const [sourceName, setSourceName] = useState('');
  const [sourceUrl, setSourceUrl] = useState('');

  const load = useCallback(async () => {
    const [nextCatalog, nextTerritory] = await Promise.all([businessAdminApi.catalog(), businessAdminApi.territory()]);
    setCatalog(nextCatalog);
    setTerritory(nextTerritory);
  }, []);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    setDenied(false);
    setError(null);
    void load().catch((caught: unknown) => {
      const status = typeof caught === 'object' && caught && 'status' in caught ? Number((caught as { status?: unknown }).status) : 0;
      if (status === 403) {
        setDenied(true);
        return;
      }
      setError('No se ha podido cargar el directorio de empresas.');
    });
  }, [auth.status, load]);

  const businesses = catalog?.businesses ?? [];
  const categories = catalog?.categories ?? [];
  const places = territory?.places ?? [];
  const selected = form.id ? businesses.find((business) => business.id === form.id) ?? null : null;

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es');
    if (!needle) return businesses;
    return businesses.filter((business) => [business.name, business.slug, business.placeName, business.municipalityName, business.shortDescription]
      .filter(Boolean).join(' ').toLocaleLowerCase('es').includes(needle));
  }, [businesses, query]);

  async function run(task: () => Promise<void>, success: string) {
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      await task();
      setMessage(success);
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido guardar el cambio. Comprueba los datos y vuelve a intentarlo.');
    } finally {
      setBusy(false);
    }
  }

  async function saveBusiness() {
    const name = form.name.trim();
    const slug = slugify(form.slug || form.name);
    if (!name || !slug) {
      setError('Nombre y slug son obligatorios.');
      return;
    }
    const latitude = parseCoordinate(form.latitude, -90, 90);
    const longitude = parseCoordinate(form.longitude, -180, 180);
    if (latitude === undefined || longitude === undefined || ((latitude === null) !== (longitude === null))) {
      setError('Las coordenadas deben ser válidas y latitud/longitud deben informarse juntas.');
      return;
    }
    const place = places.find((candidate) => candidate.id === form.placeId);
    const payload: BusinessWriteInput = {
      slug,
      name,
      shortDescription: form.shortDescription.trim() || null,
      description: form.description.trim() || null,
      municipalityId: place?.municipality_id ?? null,
      placeId: place?.id ?? null,
      address: form.address.trim() || null,
      location: latitude === null || longitude === null ? null : { latitude, longitude },
      phone: form.phone.trim() || null,
      whatsapp: form.whatsapp.trim() || null,
      email: form.email.trim() || null,
      website: form.website.trim() || null,
      logoUrl: form.logoUrl.trim() || null,
      coverImageUrl: form.coverImageUrl.trim() || null,
      status: form.status,
      verificationStatus: form.verificationStatus,
      commercialPlan: form.commercialPlan,
      featured: form.featured,
      sponsored: form.sponsored,
      priority: Math.max(0, Number(form.priority) || 0),
      campaignStart: form.campaignStart ? new Date(form.campaignStart).toISOString() : null,
      campaignEnd: form.campaignEnd ? new Date(form.campaignEnd).toISOString() : null,
      categorySlugs: form.categorySlugs,
      primaryCategorySlug: form.primaryCategorySlug || null,
    };

    await run(async () => {
      if (form.id) await businessAdminApi.update(form.id, payload);
      else await businessAdminApi.create({ ...payload, name, slug });
      await load();
      const next = form.id ? form.id : null;
      if (!next) setForm(emptyForm);
    }, form.id ? 'Empresa actualizada.' : 'Empresa creada.');
  }

  async function reviewClaim(id: string, status: 'needs_info' | 'approved' | 'rejected' | 'cancelled') {
    await run(async () => {
      await businessAdminApi.reviewClaim(id, { status });
      await load();
    }, status === 'approved' ? 'Reclamación aprobada y ficha verificada.' : 'Reclamación actualizada.');
  }

  async function addMedia() {
    if (!form.id || !mediaUrl.trim()) return;
    await run(async () => {
      await businessAdminApi.addMedia(form.id!, { kind: mediaKind, url: mediaUrl.trim(), origin: 'owned' });
      setMediaUrl('');
      await load();
    }, 'Recurso multimedia añadido.');
  }

  async function addSource() {
    if (!form.id || !sourceName.trim()) return;
    await run(async () => {
      await businessAdminApi.addSource(form.id!, {
        sourceName: sourceName.trim(),
        sourceUrl: sourceUrl.trim() || null,
        rawData: {},
      });
      setSourceName('');
      setSourceUrl('');
      await load();
    }, 'Fuente registrada.');
  }

  if (auth.status === 'loading') return <main className={styles.login}><div><strong>Comprobando acceso corporativo…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className={styles.login}><div><p className={styles.eyebrow}>Mágina Olivo · Empresas</p><h1>Acceso corporativo</h1><p>Inicia sesión para administrar el directorio de empresas.</p><GoogleSignInButton /></div></main>;
  if (denied) return <main className={styles.login}><div><p className={styles.eyebrow}>Acceso restringido</p><h1>Sin permisos de plataforma</h1><p>Esta cuenta no puede gestionar el directorio.</p><button className={styles.secondaryButton} type="button" onClick={() => void auth.logout()}>Usar otra cuenta</button></div></main>;
  if (!catalog || !territory) return <main className={styles.login}><div><strong>Cargando empresas…</strong>{error ? <p>{error}</p> : null}</div></main>;

  return <main className={styles.shell}>
    <header className={styles.header}>
      <div><a href="/admin">← Centro de control</a><p className={styles.eyebrow}>DIRECTORIO CANÓNICO DE EMPRESAS</p><h1>Empresas y servicios</h1><p>{auth.user?.primary_email ?? auth.user?.display_name}</p></div>
      <div className={styles.actions}><a className={styles.secondaryButton} href="/explorar/empresas" target="_blank">Ver público ↗</a><button className={styles.secondaryButton} disabled={busy} onClick={() => void load()}>Actualizar</button></div>
    </header>

    <section className={styles.stats}>
      <article><strong>{catalog.stats.total}</strong><span>Total</span></article>
      <article><strong>{catalog.stats.published}</strong><span>Publicadas</span></article>
      <article><strong>{catalog.stats.verified}</strong><span>Verificadas</span></article>
      <article><strong>{catalog.stats.sponsored}</strong><span>Comerciales</span></article>
      <article><strong>{catalog.stats.pendingClaims}</strong><span>Reclamaciones</span></article>
    </section>

    {message ? <div className={styles.success}>{message}</div> : null}
    {error ? <div className={styles.error}>{error}</div> : null}

    <nav className={styles.tabs} aria-label="Secciones del directorio">
      <button className={section === 'businesses' ? styles.activeTab : ''} onClick={() => setSection('businesses')}>Empresas</button>
      <button className={section === 'claims' ? styles.activeTab : ''} onClick={() => setSection('claims')}>Reclamaciones ({catalog.stats.pendingClaims})</button>
      <button className={section === 'categories' ? styles.activeTab : ''} onClick={() => setSection('categories')}>Categorías ({categories.length})</button>
    </nav>

    {section === 'businesses' ? <section className={styles.layout}>
      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>Directorio</h2><p>Fichas estructuradas, independientes del CMS editorial.</p></div><button className={styles.button} onClick={() => setForm(emptyForm)}>+ Nueva</button></div>
        <input className={styles.search} type="search" placeholder="Buscar empresa, pueblo, servicio…" value={query} onChange={(event) => setQuery(event.target.value)} />
        <div className={styles.businessList}>{filtered.map((business) => <button key={business.id} className={`${styles.businessRow} ${form.id === business.id ? styles.selectedRow : ''}`} onClick={() => setForm(formFromBusiness(business))}>
          <span><strong>{business.name}</strong><small>{business.placeName ?? business.municipalityName ?? 'Sin ubicación'} · {business.categories.map((item) => item.name).join(', ') || 'Sin categoría'}</small></span>
          <span className={styles.rowBadges}>{business.verificationStatus === 'verified' ? <b>✓</b> : null}{business.sponsored ? <em>Patrocinado</em> : business.featured ? <em>Destacado</em> : null}<i data-status={business.status}>{business.status}</i></span>
        </button>)}</div>
      </article>

      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>{form.id ? 'Editar empresa' : 'Nueva empresa'}</h2><p>{selected ? `${selected.mediaCount} medios · ${selected.sourceCount} fuentes · ${selected.pendingClaimCount} reclamaciones activas` : 'Crea una ficha sin inventar datos ausentes.'}</p></div></div>
        <div className={styles.formGrid}>
          <label>Nombre<input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value, slug: form.id ? form.slug : slugify(event.target.value) })} /></label>
          <label>Slug<input value={form.slug} onChange={(event) => setForm({ ...form, slug: slugify(event.target.value) })} /></label>
          <label className={styles.wide}>Resumen<input value={form.shortDescription} maxLength={320} onChange={(event) => setForm({ ...form, shortDescription: event.target.value })} /></label>
          <label className={styles.wide}>Descripción<textarea rows={5} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
          <label>Pueblo / localidad<select value={form.placeId} onChange={(event) => setForm({ ...form, placeId: event.target.value })}><option value="">Sin localidad</option>{places.map((place) => <option key={place.id} value={place.id}>{place.name} · {place.municipality_name}</option>)}</select></label>
          <label>Dirección<input value={form.address} onChange={(event) => setForm({ ...form, address: event.target.value })} /></label>
          <label>Latitud<input inputMode="decimal" value={form.latitude} onChange={(event) => setForm({ ...form, latitude: event.target.value })} /></label>
          <label>Longitud<input inputMode="decimal" value={form.longitude} onChange={(event) => setForm({ ...form, longitude: event.target.value })} /></label>
          <label>Teléfono<input value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label>
          <label>WhatsApp<input value={form.whatsapp} onChange={(event) => setForm({ ...form, whatsapp: event.target.value })} /></label>
          <label>Email<input type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
          <label>Web<input type="url" value={form.website} onChange={(event) => setForm({ ...form, website: event.target.value })} /></label>
          <label>Logo URL<input type="url" value={form.logoUrl} onChange={(event) => setForm({ ...form, logoUrl: event.target.value })} /></label>
          <label>Portada URL<input type="url" value={form.coverImageUrl} onChange={(event) => setForm({ ...form, coverImageUrl: event.target.value })} /></label>
          <label>Publicación<select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as FormState['status'] })}><option value="draft">Borrador</option><option value="published">Publicada</option><option value="archived">Archivada</option></select></label>
          <label>Verificación<select value={form.verificationStatus} onChange={(event) => setForm({ ...form, verificationStatus: event.target.value as FormState['verificationStatus'] })}><option value="unverified">Sin verificar</option><option value="pending">Pendiente</option><option value="verified">Verificada</option><option value="rejected">Rechazada</option></select></label>
        </div>

        <fieldset className={styles.fieldset}><legend>Categorías</legend><div className={styles.checkGrid}>{categories.filter((item) => item.active).map((item) => <label key={item.id}><input type="checkbox" checked={form.categorySlugs.includes(item.slug)} onChange={(event) => {
          const categorySlugs = event.target.checked ? [...form.categorySlugs, item.slug] : form.categorySlugs.filter((slug) => slug !== item.slug);
          setForm({ ...form, categorySlugs, primaryCategorySlug: event.target.checked ? (form.primaryCategorySlug || item.slug) : form.primaryCategorySlug === item.slug ? '' : form.primaryCategorySlug });
        }} />{item.name}</label>)}</div><label>Categoría principal<select value={form.primaryCategorySlug} onChange={(event) => setForm({ ...form, primaryCategorySlug: event.target.value })}><option value="">Sin principal</option>{categories.filter((item) => form.categorySlugs.includes(item.slug)).map((item) => <option key={item.id} value={item.slug}>{item.name}</option>)}</select></label></fieldset>

        <fieldset className={styles.fieldset}><legend>Plan comercial · siempre visible en público</legend><div className={styles.formGrid}>
          <label>Plan<select value={form.commercialPlan} onChange={(event) => setForm({ ...form, commercialPlan: event.target.value as FormState['commercialPlan'] })}><option value="free">Free</option><option value="featured">Featured</option><option value="premium">Premium</option><option value="sponsor">Sponsor</option></select></label>
          <label>Prioridad<input type="number" min="0" value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value })} /></label>
          <label>Inicio campaña<input type="datetime-local" value={form.campaignStart} onChange={(event) => setForm({ ...form, campaignStart: event.target.value })} /></label>
          <label>Fin campaña<input type="datetime-local" value={form.campaignEnd} onChange={(event) => setForm({ ...form, campaignEnd: event.target.value })} /></label>
          <label className={styles.checkbox}><input type="checkbox" checked={form.featured} onChange={(event) => setForm({ ...form, featured: event.target.checked })} />Destacada</label>
          <label className={styles.checkbox}><input type="checkbox" checked={form.sponsored} onChange={(event) => setForm({ ...form, sponsored: event.target.checked })} />Patrocinada</label>
        </div></fieldset>

        <div className={styles.actions}><button className={styles.button} disabled={busy} onClick={() => void saveBusiness()}>{busy ? 'Guardando…' : 'Guardar ficha'}</button>{form.id ? <a className={styles.secondaryButton} href={`/empresas?slug=${encodeURIComponent(form.slug)}`} target="_blank">Vista pública ↗</a> : null}</div>

        {form.id ? <div className={styles.operations}>
          <h3>Fuentes y multimedia</h3>
          <div className={styles.inlineForm}><select value={mediaKind} onChange={(event) => setMediaKind(event.target.value as typeof mediaKind)}><option value="photo">Foto</option><option value="logo">Logo</option><option value="cover">Portada</option><option value="video">Vídeo</option><option value="document">Documento</option></select><input type="url" placeholder="URL del recurso" value={mediaUrl} onChange={(event) => setMediaUrl(event.target.value)} /><button className={styles.secondaryButton} disabled={busy || !mediaUrl.trim()} onClick={() => void addMedia()}>Añadir medio</button></div>
          <div className={styles.inlineForm}><input placeholder="Nombre de la fuente" value={sourceName} onChange={(event) => setSourceName(event.target.value)} /><input type="url" placeholder="URL oficial / procedencia" value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} /><button className={styles.secondaryButton} disabled={busy || !sourceName.trim()} onClick={() => void addSource()}>Registrar fuente</button></div>
        </div> : null}
      </article>
    </section> : null}

    {section === 'claims' ? <section className={styles.panel}>
      <div className={styles.panelTitle}><div><h2>Reclamaciones de fichas</h2><p>La aprobación verifica la ficha; el plan comercial no interviene en esta decisión.</p></div></div>
      <div className={styles.claimList}>{catalog.claims.map((claim) => <article key={claim.id} className={styles.claim}>
        <div><strong>{claim.business_name}</strong><p>{claim.claimant_name} · {claim.claimant_email}{claim.claimant_phone ? ` · ${claim.claimant_phone}` : ''}</p><small>{new Date(claim.created_at).toLocaleString('es-ES')} · {claim.status}</small></div>
        {claim.status === 'pending' || claim.status === 'needs_info' ? <div className={styles.actions}><button className={styles.button} disabled={busy} onClick={() => void reviewClaim(claim.id, 'approved')}>Aprobar y verificar</button><button className={styles.secondaryButton} disabled={busy} onClick={() => void reviewClaim(claim.id, 'needs_info')}>Pedir información</button><button className={styles.dangerButton} disabled={busy} onClick={() => void reviewClaim(claim.id, 'rejected')}>Rechazar</button></div> : null}
      </article>)}</div>
    </section> : null}

    {section === 'categories' ? <section className={styles.panel}>
      <div className={styles.panelTitle}><div><h2>Categorías</h2><p>Taxonomía estructurada del directorio. Las categorías inactivas no se muestran en público.</p></div></div>
      <div className={styles.categoryAdminList}>{categories.map((item) => <article key={item.id}><div><strong>{item.name}</strong><small>/{item.slug} · {item.business_count} empresas</small></div><span>{item.active ? 'Activa' : 'Inactiva'}</span></article>)}</div>
      <p className={styles.help}>La creación y edición avanzada de la taxonomía queda disponible por API Admin para evitar cambios accidentales de slugs que ya estén enlazados.</p>
    </section> : null}
  </main>;
}
