'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  adminApi,
  type AdminSession,
  type AdminTerritoryMunicipality,
  type AdminTerritoryPlace,
  type CmsEntry,
  type CmsEntryStatus,
  type PlatformAdminRole,
  type TerritoryPlaceKind,
} from '../lib/admin-data-source';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import { AdminMediaPicker } from './admin-media-picker';

type Section = 'catalog' | 'content';
type TerritoryContentType = 'place' | 'mill' | 'directory';

type DirectoryForm = {
  id: string | null;
  type: TerritoryContentType;
  territoryPlaceId: string;
  title: string;
  slug: string;
  summary: string;
  body: string;
  phone: string;
  email: string;
  address: string;
  services: string;
  openingHours: string;
  instagram: string;
  facebook: string;
  campaignNotes: string;
  latitude: string;
  longitude: string;
  ctaLabel: string;
  mediaUrl: string;
  externalUrl: string;
  status: CmsEntryStatus;
  featured: boolean;
  sortOrder: string;
};

const emptyForm: DirectoryForm = {
  id: null,
  type: 'mill',
  territoryPlaceId: '',
  title: '',
  slug: '',
  summary: '',
  body: '',
  phone: '',
  email: '',
  address: '',
  services: '',
  openingHours: '',
  instagram: '',
  facebook: '',
  campaignNotes: '',
  latitude: '',
  longitude: '',
  ctaLabel: '',
  mediaUrl: '',
  externalUrl: '',
  status: 'draft',
  featured: false,
  sortOrder: '0',
};

const typeLabels: Record<TerritoryContentType, string> = {
  place: 'Ficha de pueblo / lugar',
  mill: 'Cooperativa / almazara',
  directory: 'Empresa / servicio local',
};

const kindLabels: Record<TerritoryPlaceKind, string> = {
  municipal_seat: 'Cabecera municipal',
  locality: 'Localidad',
  hamlet: 'Núcleo / aldea',
  other: 'Otro',
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
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function text(value: unknown) {
  return typeof value === 'string' ? value : '';
}

function lines(value: unknown) {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string').join('\n') : '';
}

function linkedPlaceId(entry: CmsEntry) {
  return text(asObject(entry.content_json).territory_place_id);
}

function formFromEntry(entry: CmsEntry): DirectoryForm {
  const data = asObject(entry.content_json);
  const coordinates = asObject(data.coordinates);
  return {
    id: entry.id,
    type: entry.type as TerritoryContentType,
    territoryPlaceId: text(data.territory_place_id),
    title: entry.title,
    slug: entry.slug,
    summary: entry.summary ?? '',
    body: text(data.body),
    phone: text(data.phone),
    email: text(data.email),
    address: text(data.address),
    services: lines(data.services),
    openingHours: text(data.opening_hours),
    instagram: text(data.instagram),
    facebook: text(data.facebook),
    campaignNotes: text(data.campaign_notes),
    latitude: typeof coordinates.latitude === 'number' ? String(coordinates.latitude) : '',
    longitude: typeof coordinates.longitude === 'number' ? String(coordinates.longitude) : '',
    ctaLabel: text(data.cta_label),
    mediaUrl: entry.media_url ?? '',
    externalUrl: entry.external_url ?? '',
    status: entry.status,
    featured: entry.featured,
    sortOrder: String(entry.sort_order ?? 0),
  };
}

function parseCoordinate(value: string, min: number, max: number) {
  if (!value.trim()) return null;
  const parsed = Number(value.replace(',', '.'));
  if (!Number.isFinite(parsed) || parsed < min || parsed > max) return undefined;
  return parsed;
}

export function TerritoryAdminConsole() {
  const auth = useAuth();
  const [session, setSession] = useState<AdminSession | null>(null);
  const [denied, setDenied] = useState(false);
  const [section, setSection] = useState<Section>('catalog');
  const [municipalities, setMunicipalities] = useState<AdminTerritoryMunicipality[]>([]);
  const [places, setPlaces] = useState<AdminTerritoryPlace[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [form, setForm] = useState<DirectoryForm>(emptyForm);
  const [typeFilter, setTypeFilter] = useState<TerritoryContentType | 'all'>('all');
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const role = session?.platform_access.role ?? null;
  const editable = role ? canEdit(role) : false;

  const load = useCallback(async () => {
    const [adminSession, catalog, content] = await Promise.all([
      adminApi.session(),
      adminApi.territoryCatalog(),
      adminApi.content(),
    ]);
    setSession(adminSession);
    setMunicipalities(catalog.municipalities);
    setPlaces(catalog.places);
    setEntries(content.entries.filter((entry) => ['place', 'mill', 'directory'].includes(entry.type)));
  }, []);

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
      setError('No se ha podido cargar la administración territorial.');
    });
  }, [auth.status, load]);

  const activeEntries = useMemo(() => entries.filter((entry) => entry.status !== 'archived'), [entries]);

  const filteredPlaces = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('es');
    return places.filter((place) => !normalized
      || place.name.toLocaleLowerCase('es').includes(normalized)
      || place.municipality_name.toLocaleLowerCase('es').includes(normalized));
  }, [places, query]);

  const filteredEntries = useMemo(() => activeEntries.filter((entry) => {
    if (typeFilter !== 'all' && entry.type !== typeFilter) return false;
    const normalized = query.trim().toLocaleLowerCase('es');
    if (!normalized) return true;
    const place = places.find((candidate) => candidate.id === linkedPlaceId(entry));
    return entry.title.toLocaleLowerCase('es').includes(normalized)
      || (entry.summary ?? '').toLocaleLowerCase('es').includes(normalized)
      || Boolean(place?.name.toLocaleLowerCase('es').includes(normalized));
  }), [activeEntries, places, query, typeFilter]);

  const placeById = useMemo(() => new Map(places.map((place) => [place.id, place])), [places]);
  const municipalityById = useMemo(() => new Map(municipalities.map((municipality) => [municipality.id, municipality])), [municipalities]);

  async function run(task: () => Promise<void>, success: string) {
    setBusy(true);
    setError(null);
    setMessage(null);
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

  async function updatePlace(place: AdminTerritoryPlace, change: { public_enabled?: boolean; kind?: TerritoryPlaceKind }) {
    if (!editable) return;
    await run(async () => {
      await adminApi.updateTerritoryPlace(place.id, change);
      await load();
    }, 'Catálogo territorial actualizado.');
  }

  function startNew(type: TerritoryContentType, territoryPlaceId = '') {
    const place = placeById.get(territoryPlaceId);
    const existingPlaceEntry = type === 'place' && territoryPlaceId
      ? activeEntries.find((entry) => entry.type === 'place' && linkedPlaceId(entry) === territoryPlaceId)
      : null;
    if (existingPlaceEntry) {
      setForm(formFromEntry(existingPlaceEntry));
      setSection('content');
      return;
    }
    setForm({
      ...emptyForm,
      type,
      territoryPlaceId,
      title: type === 'place' && place ? place.name : '',
      slug: type === 'place' && place ? place.slug : '',
    });
    setSection('content');
  }

  async function saveContent() {
    if (!editable || !form.title.trim() || !form.slug.trim() || !form.territoryPlaceId) return;
    const place = placeById.get(form.territoryPlaceId);
    if (!place) {
      setError('Selecciona un pueblo o localidad del catálogo oficial.');
      return;
    }
    const latitude = parseCoordinate(form.latitude, -90, 90);
    const longitude = parseCoordinate(form.longitude, -180, 180);
    if (latitude === undefined || longitude === undefined) {
      setError('Las coordenadas no son válidas. Latitud -90…90 y longitud -180…180.');
      return;
    }
    const services = form.services.split('\n').map((item) => item.trim()).filter(Boolean).slice(0, 30);
    const payload = {
      type: form.type,
      title: form.title.trim(),
      slug: slugify(form.slug),
      summary: form.summary.trim() || null,
      content_json: {
        territory_place_id: place.id,
        territory_place_name: place.name,
        territory_place_slug: place.slug,
        municipality_id: place.municipality_id,
        municipality_name: place.municipality_name,
        body: form.body.trim(),
        phone: form.phone.trim(),
        email: form.email.trim(),
        address: form.address.trim(),
        services,
        opening_hours: form.openingHours.trim(),
        instagram: form.instagram.trim(),
        facebook: form.facebook.trim(),
        campaign_notes: form.campaignNotes.trim(),
        coordinates: latitude !== null && longitude !== null ? { latitude, longitude } : null,
        cta_label: form.ctaLabel.trim(),
      },
      status: form.status,
      featured: form.featured,
      media_url: form.mediaUrl.trim() || null,
      external_url: form.externalUrl.trim() || null,
      sort_order: Number(form.sortOrder) || 0,
    };

    await run(async () => {
      let id = form.id;
      if (!id && form.type === 'place') {
        id = activeEntries.find((entry) => entry.type === 'place' && linkedPlaceId(entry) === place.id)?.id ?? null;
      }
      if (id) await adminApi.updateContent(id, payload);
      else await adminApi.createContent(payload);
      setForm(emptyForm);
      await load();
    }, form.id ? 'Ficha actualizada.' : 'Ficha creada y vinculada al territorio.');
  }

  async function archiveContent() {
    if (!form.id || !editable) return;
    await run(async () => {
      await adminApi.archiveContent(form.id!);
      setForm(emptyForm);
      await load();
    }, 'Ficha archivada.');
  }

  if (auth.status === 'loading') return <main className="territory-admin-login"><div><strong>Comprobando acceso corporativo…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className="territory-admin-login"><div><span className="territory-admin-eyebrow">Mágina Olivo · Territorio</span><h1>Acceso corporativo</h1><p>Inicia sesión para administrar pueblos, cooperativas y servicios locales.</p><GoogleSignInButton /></div></main>;
  if (denied) return <main className="territory-admin-login"><div><span className="territory-admin-eyebrow">Acceso restringido</span><h1>Sin permisos de plataforma</h1><p>Esta cuenta no puede administrar el catálogo territorial.</p><button className="territory-admin-btn secondary" onClick={() => void auth.logout()}>Usar otra cuenta</button></div></main>;
  if (!session || !role) return <main className="territory-admin-login"><div><strong>Cargando territorio…</strong>{error ? <p>{error}</p> : null}</div></main>;

  return <main className="territory-admin-shell">
    <header className="territory-admin-topbar">
      <div><a href="/admin">← Centro de control</a><span className="territory-admin-eyebrow">Catálogo canónico + contenido público</span><h1>Territorio y directorio local</h1><p>{session.user.primary_email} · {role}</p></div>
      <div className="territory-admin-actions"><a className="territory-admin-btn secondary" href="/explorar" target="_blank">Ver Explorar</a><a className="territory-admin-btn secondary" href="/admin/media">Multimedia</a><button className="territory-admin-btn secondary" disabled={busy} onClick={() => void load()}>Actualizar</button></div>
    </header>

    <section className="territory-admin-summary">
      <article><strong>{municipalities.length}</strong><span>municipios oficiales</span></article>
      <article><strong>{places.length}</strong><span>pueblos / localidades</span></article>
      <article><strong>{places.filter((place) => place.public_enabled).length}</strong><span>visibles públicamente</span></article>
      <article><strong>{activeEntries.filter((entry) => entry.type === 'mill').length}</strong><span>cooperativas / almazaras</span></article>
      <article><strong>{activeEntries.filter((entry) => entry.type === 'directory').length}</strong><span>servicios locales</span></article>
    </section>

    {message ? <div className="territory-admin-notice success">{message}</div> : null}
    {error ? <div className="territory-admin-notice error">{error}</div> : null}

    <div className="territory-admin-tabs">
      <button className={section === 'catalog' ? 'active' : ''} onClick={() => setSection('catalog')}>Pueblos canónicos</button>
      <button className={section === 'content' ? 'active' : ''} onClick={() => setSection('content')}>Fichas, cooperativas y empresas</button>
    </div>

    <div className="territory-admin-search"><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar pueblo, cooperativa, empresa…" /></div>

    {section === 'catalog' ? <section className="territory-admin-grid">
      <article className="territory-admin-card territory-admin-wide">
        <div className="territory-admin-title"><div><h2>Pueblos y localidades</h2><p>Estos son los lugares canónicos usados por fincas, clima y otras integraciones. Aquí no se duplican.</p></div></div>
        <div className="territory-admin-place-list">{filteredPlaces.map((place) => {
          const municipality = municipalityById.get(place.municipality_id);
          return <div className="territory-admin-place" key={place.id}>
            <div className="territory-admin-place-main">
              <span className={`territory-admin-dot ${place.public_enabled ? 'on' : 'off'}`} />
              <div><strong>{place.name}</strong><small>{place.municipality_name} · {municipality?.ine_code ?? 'INE —'} · {place.field_count} finca{place.field_count === 1 ? '' : 's'} vinculada{place.field_count === 1 ? '' : 's'} · {place.editorial_count} ficha{place.editorial_count === 1 ? '' : 's'} pública/editorial</small></div>
            </div>
            <div className="territory-admin-place-controls">
              <select disabled={!editable || busy} value={place.kind} onChange={(event) => void updatePlace(place, { kind: event.target.value as TerritoryPlaceKind })}>{Object.entries(kindLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
              <button className={`territory-admin-btn compact ${place.public_enabled ? 'secondary' : ''}`} disabled={!editable || busy} onClick={() => void updatePlace(place, { public_enabled: !place.public_enabled })}>{place.public_enabled ? 'Ocultar' : 'Publicar'}</button>
              {editable ? <button className="territory-admin-btn compact secondary" onClick={() => startNew('place', place.id)}>{activeEntries.some((entry) => entry.type === 'place' && linkedPlaceId(entry) === place.id) ? 'Editar ficha' : 'Crear ficha'}</button> : null}
            </div>
          </div>;
        })}{!filteredPlaces.length ? <div className="territory-admin-empty">No hay lugares que coincidan con la búsqueda.</div> : null}</div>
      </article>
      <aside className="territory-admin-card">
        <h2>Regla de integridad</h2>
        <p>INE, AEMET, geometría y relación municipio/localidad siguen siendo datos canónicos de V20. Este panel solo permite decidir si un lugar se publica y clasificar su tipo.</p>
        <div className="territory-admin-callout"><strong>Ejemplo</strong><span>Bedmar y Garcíez es el municipio administrativo; Bedmar y Garcíez son lugares visibles distintos. El Admin respeta esa estructura.</span></div>
      </aside>
    </section> : null}

    {section === 'content' ? <section className="territory-admin-content-layout">
      <article className="territory-admin-card">
        <div className="territory-admin-title"><div><h2>Contenido territorial</h2><p>Fichas públicas enlazadas al catálogo canónico.</p></div>{editable ? <div className="territory-admin-actions"><button className="territory-admin-btn compact secondary" onClick={() => startNew('place')}>+ Pueblo</button><button className="territory-admin-btn compact secondary" onClick={() => startNew('mill')}>+ Cooperativa</button><button className="territory-admin-btn compact secondary" onClick={() => startNew('directory')}>+ Empresa</button></div> : null}</div>
        <div className="territory-admin-filter-row"><button className={typeFilter === 'all' ? 'active' : ''} onClick={() => setTypeFilter('all')}>Todos</button>{(Object.keys(typeLabels) as TerritoryContentType[]).map((type) => <button key={type} className={typeFilter === type ? 'active' : ''} onClick={() => setTypeFilter(type)}>{type === 'place' ? 'Pueblos' : type === 'mill' ? 'Cooperativas' : 'Directorio'}</button>)}</div>
        <div className="territory-admin-entry-list">{filteredEntries.map((entry) => {
          const place = placeById.get(linkedPlaceId(entry));
          return <button key={entry.id} className={`territory-admin-entry ${form.id === entry.id ? 'selected' : ''}`} onClick={() => setForm(formFromEntry(entry))}><span><strong>{entry.title}</strong><small>{typeLabels[entry.type as TerritoryContentType]} · {place?.name ?? 'Sin lugar canónico'} · /{entry.slug}</small></span><b className={`territory-admin-status ${entry.status}`}>{statusLabels[entry.status]}</b></button>;
        })}{!filteredEntries.length ? <div className="territory-admin-empty">No hay fichas en este filtro.</div> : null}</div>
      </article>

      <article className="territory-admin-card territory-admin-editor">
        <div className="territory-admin-title"><div><h2>{form.id ? 'Editar ficha' : 'Nueva ficha'}</h2><p>Los datos editoriales no alteran la referencia territorial canónica.</p></div>{form.id ? <button className="territory-admin-btn compact secondary" onClick={() => setForm(emptyForm)}>Nueva</button> : null}</div>
        <div className="territory-admin-fields">
          <label>Tipo<select disabled={!editable} value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value as TerritoryContentType })}>{Object.entries(typeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <label>Pueblo / localidad canónica<select disabled={!editable} value={form.territoryPlaceId} onChange={(event) => setForm({ ...form, territoryPlaceId: event.target.value })}><option value="">Selecciona un lugar…</option>{places.map((place) => <option value={place.id} key={place.id}>{place.name} · {place.municipality_name}</option>)}</select></label>
          <label>Nombre público<input disabled={!editable} value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value, slug: form.id ? form.slug : slugify(event.target.value) })} /></label>
          <label>Dirección web / slug<input disabled={!editable} value={form.slug} onChange={(event) => setForm({ ...form, slug: slugify(event.target.value) })} /></label>
          <label>Resumen<textarea disabled={!editable} rows={3} value={form.summary} onChange={(event) => setForm({ ...form, summary: event.target.value })} /></label>
          <label>Descripción completa<textarea disabled={!editable} rows={5} value={form.body} onChange={(event) => setForm({ ...form, body: event.target.value })} /></label>
          <div className="territory-admin-fields two"><label>Teléfono<input disabled={!editable} value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label><label>Email<input disabled={!editable} type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label></div>
          <label>Dirección<input disabled={!editable} value={form.address} onChange={(event) => setForm({ ...form, address: event.target.value })} /></label>
          <label>Servicios <small>uno por línea</small><textarea disabled={!editable} rows={4} value={form.services} onChange={(event) => setForm({ ...form, services: event.target.value })} placeholder={'Recepción de aceituna\nVenta de AOVE\nAsesoramiento'} /></label>
          <label>Horario / atención<textarea disabled={!editable} rows={3} value={form.openingHours} onChange={(event) => setForm({ ...form, openingHours: event.target.value })} /></label>
          {form.type === 'mill' ? <label>Información de campaña<textarea disabled={!editable} rows={3} value={form.campaignNotes} onChange={(event) => setForm({ ...form, campaignNotes: event.target.value })} placeholder="Recepción, campaña, liquidaciones, servicios al socio…" /></label> : null}
          <div className="territory-admin-fields two"><label>Instagram<input disabled={!editable} value={form.instagram} onChange={(event) => setForm({ ...form, instagram: event.target.value })} /></label><label>Facebook<input disabled={!editable} value={form.facebook} onChange={(event) => setForm({ ...form, facebook: event.target.value })} /></label></div>
          <div className="territory-admin-fields two"><label>Latitud opcional<input disabled={!editable} inputMode="decimal" value={form.latitude} onChange={(event) => setForm({ ...form, latitude: event.target.value })} /></label><label>Longitud opcional<input disabled={!editable} inputMode="decimal" value={form.longitude} onChange={(event) => setForm({ ...form, longitude: event.target.value })} /></label></div>
          <AdminMediaPicker disabled={!editable} label="Imagen principal" value={form.mediaUrl} onChange={(mediaUrl) => setForm({ ...form, mediaUrl })} />
          <label>Web / enlace principal<input disabled={!editable} placeholder="https://…" value={form.externalUrl} onChange={(event) => setForm({ ...form, externalUrl: event.target.value })} /></label>
          <label>Texto de llamada a la acción<input disabled={!editable} value={form.ctaLabel} onChange={(event) => setForm({ ...form, ctaLabel: event.target.value })} placeholder="Visitar web / Llamar / Ver cooperativa" /></label>
          <div className="territory-admin-fields two"><label>Estado<select disabled={!editable} value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as CmsEntryStatus })}>{Object.entries(statusLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label><label>Orden<input disabled={!editable} type="number" value={form.sortOrder} onChange={(event) => setForm({ ...form, sortOrder: event.target.value })} /></label></div>
          <label className="territory-admin-check"><input disabled={!editable} type="checkbox" checked={form.featured} onChange={(event) => setForm({ ...form, featured: event.target.checked })} /> Destacar en Explorar</label>
        </div>
        {form.title ? <div className="territory-admin-preview">{form.mediaUrl ? <img src={form.mediaUrl} alt="" /> : null}<div><span>{typeLabels[form.type].toUpperCase()}</span><h3>{form.title}</h3><p>{form.summary || 'El resumen aparecerá aquí.'}</p><small>{placeById.get(form.territoryPlaceId)?.name ?? 'Selecciona un pueblo'}{form.phone ? ` · ${form.phone}` : ''}</small></div></div> : null}
        {editable ? <div className="territory-admin-actions territory-admin-save"><button className="territory-admin-btn" disabled={busy || !form.title || !form.slug || !form.territoryPlaceId} onClick={() => void saveContent()}>Guardar ficha</button>{form.id ? <button className="territory-admin-btn danger" disabled={busy} onClick={() => void archiveContent()}>Archivar</button> : null}</div> : null}
      </article>
    </section> : null}
  </main>;
}
