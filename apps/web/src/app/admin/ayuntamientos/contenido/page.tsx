'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { adminApi, type AdminMediaAsset, type AdminTerritoryMunicipality, type CmsEntry, type CmsEntryStatus } from '@/lib/admin-data-source';
import { apiBaseUrl } from '@/lib/api-client';
import '../../admin.css';
import styles from './content-editor.module.css';

type MunicipalityRole = 'profile' | 'heritage' | 'nature' | 'tourism';
type FormState = {
  id: string | null;
  municipalityId: string;
  role: MunicipalityRole;
  title: string;
  slug: string;
  summary: string;
  body: string;
  sourceUrl: string;
  sourceLabel: string;
  verifiedAt: string;
  externalUrl: string;
  mediaUrl: string;
  status: CmsEntryStatus;
  featured: boolean;
  sortOrder: number;
};

const ROLE_LABELS: Record<MunicipalityRole, string> = {
  profile: 'Perfil principal',
  heritage: 'Patrimonio',
  nature: 'Naturaleza',
  tourism: 'Turismo',
};

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function field(entry: CmsEntry, key: string) {
  const value = asObject(entry.content_json)[key];
  return typeof value === 'string' ? value : '';
}

function roleOf(entry: CmsEntry): MunicipalityRole | null {
  const role = field(entry, 'municipality_role');
  return role === 'profile' || role === 'heritage' || role === 'nature' || role === 'tourism' ? role : null;
}

function slugify(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

function mediaPublicUrl(asset: AdminMediaAsset) {
  return `${apiBaseUrl}${asset.public_path}`;
}

function blankForm(municipalityId = ''): FormState {
  return {
    id: null,
    municipalityId,
    role: 'heritage',
    title: '',
    slug: '',
    summary: '',
    body: '',
    sourceUrl: '',
    sourceLabel: '',
    verifiedAt: new Date().toISOString().slice(0, 10),
    externalUrl: '',
    mediaUrl: '',
    status: 'draft',
    featured: false,
    sortOrder: 0,
  };
}

export default function AdminMunicipalityContentEditorPage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<AdminTerritoryMunicipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [assets, setAssets] = useState<AdminMediaAsset[]>([]);
  const [form, setForm] = useState<FormState>(() => blankForm());
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [content, territory, media] = await Promise.all([adminApi.content(), adminApi.territoryCatalog(), adminApi.media()]);
    const activeMunicipalities = territory.municipalities.filter((item) => item.active);
    setMunicipalities(activeMunicipalities);
    setEntries(content.entries.filter((entry) => entry.type === 'place' && Boolean(field(entry, 'municipality_id'))));
    setAssets(media.assets.filter((asset) => asset.status === 'uploaded'));
    setForm((current) => current.municipalityId ? current : { ...current, municipalityId: activeMunicipalities[0]?.id ?? '' });
  }, []);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    void load().catch(() => setError('No se ha podido cargar el editor municipal.'));
  }, [auth.status, load]);

  const municipality = useMemo(() => municipalities.find((item) => item.id === form.municipalityId) ?? null, [municipalities, form.municipalityId]);
  const municipalityEntries = useMemo(() => entries
    .filter((entry) => field(entry, 'municipality_id') === form.municipalityId)
    .sort((a, b) => Number(b.featured) - Number(a.featured) || b.sort_order - a.sort_order || a.title.localeCompare(b.title, 'es')), [entries, form.municipalityId]);
  const visibleEntries = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es');
    return municipalityEntries.filter((entry) => !needle || `${entry.title} ${entry.summary ?? ''} ${ROLE_LABELS[roleOf(entry) ?? 'heritage']}`.toLocaleLowerCase('es').includes(needle));
  }, [municipalityEntries, query]);
  const slugConflict = useMemo(() => entries.some((entry) => entry.slug === form.slug && entry.id !== form.id), [entries, form.slug, form.id]);
  const featuredCount = municipalityEntries.filter((entry) => entry.featured && roleOf(entry) !== 'profile').length;

  function startNew() {
    setForm(blankForm(form.municipalityId));
    setMessage(null);
    setError(null);
  }

  function edit(entry: CmsEntry) {
    const content = asObject(entry.content_json);
    setForm({
      id: entry.id,
      municipalityId: field(entry, 'municipality_id'),
      role: roleOf(entry) ?? 'heritage',
      title: entry.title,
      slug: entry.slug,
      summary: entry.summary ?? '',
      body: typeof content.body === 'string' ? content.body : '',
      sourceUrl: field(entry, 'source_url'),
      sourceLabel: field(entry, 'source_label'),
      verifiedAt: field(entry, 'verified_at') || new Date().toISOString().slice(0, 10),
      externalUrl: entry.external_url ?? '',
      mediaUrl: entry.media_url ?? '',
      status: entry.status,
      featured: entry.featured,
      sortOrder: entry.sort_order,
    });
    setMessage(null);
    setError(null);
  }

  function changeTitle(title: string) {
    setForm((current) => ({ ...current, title, slug: current.id ? current.slug : slugify(`${municipality?.slug ?? 'municipio'}-${title}`) }));
  }

  async function ensureSingleProfile(targetId: string | null) {
    if (form.role !== 'profile') return;
    const others = municipalityEntries.filter((entry) => entry.id !== targetId && roleOf(entry) === 'profile');
    for (const entry of others) {
      const next = { ...asObject(entry.content_json) };
      delete next.municipality_role;
      await adminApi.updateContent(entry.id, {
        type: entry.type,
        title: entry.title,
        slug: entry.slug,
        summary: entry.summary,
        content_json: next,
        status: entry.status,
        featured: entry.featured,
        starts_at: entry.starts_at,
        ends_at: entry.ends_at,
        media_url: entry.media_url,
        external_url: entry.external_url,
        sort_order: entry.sort_order,
      });
    }
  }

  async function save() {
    if (!municipality) return setError('Selecciona un municipio.');
    if (!form.title.trim() || !form.slug.trim()) return setError('Título y slug son obligatorios.');
    if (slugConflict) return setError('Ese slug ya existe en el CMS. Usa otro identificador.');
    if (form.featured && form.role !== 'profile' && !form.id && featuredCount >= 3) return setError('Este municipio ya tiene tres imprescindibles destacados.');
    if (form.featured && form.role !== 'profile' && form.id && !entries.find((entry) => entry.id === form.id)?.featured && featuredCount >= 3) return setError('Este municipio ya tiene tres imprescindibles destacados.');

    setBusy(true); setMessage(null); setError(null);
    try {
      await ensureSingleProfile(form.id);
      const existing = form.id ? entries.find((entry) => entry.id === form.id) ?? null : null;
      const existingContent = existing ? asObject(existing.content_json) : {};
      const contentJson = {
        ...existingContent,
        municipality_id: municipality.id,
        municipality_name: municipality.name,
        municipality_slug: municipality.slug,
        municipality_role: form.role,
        body: form.body.trim(),
        source_url: form.sourceUrl.trim(),
        source_label: form.sourceLabel.trim(),
        verified_at: form.verifiedAt,
      };
      const payload = {
        type: 'place' as const,
        title: form.title.trim(),
        slug: form.slug.trim(),
        summary: form.summary.trim() || null,
        content_json: contentJson,
        status: form.status,
        featured: form.role === 'profile' ? false : form.featured,
        starts_at: existing?.starts_at ?? null,
        ends_at: existing?.ends_at ?? null,
        media_url: form.mediaUrl.trim() || null,
        external_url: form.externalUrl.trim() || null,
        sort_order: form.sortOrder,
      };
      if (form.id) await adminApi.updateContent(form.id, payload);
      else await adminApi.createContent(payload);
      await load();
      setForm(blankForm(municipality.id));
      setMessage(form.id ? 'Contenido municipal actualizado y auditado.' : 'Contenido municipal creado correctamente.');
    } catch {
      setError('No se ha podido guardar el contenido. Revisa los datos y vuelve a intentarlo.');
    } finally {
      setBusy(false);
    }
  }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Contenido municipal</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar">
      <div><a href="/admin/ayuntamientos">← Ayuntamientos</a><h1>Editor de contenido municipal</h1><p>Crea y edita perfiles, patrimonio, naturaleza y turismo sin tocar código.</p></div>
      <div className={styles.headerActions}><a href="/admin/media" target="_blank" rel="noreferrer">Subir imagen ↗</a><button type="button" onClick={() => void load()}>Actualizar</button></div>
    </header>
    {message ? <div className="admin-notice success">{message}</div> : null}
    {error ? <div className="admin-notice error">{error}</div> : null}

    <section className={styles.layout}>
      <aside className={`admin-card ${styles.sidebar}`}>
        <div className={styles.sidebarTop}><div><h2>Contenido</h2><p>{municipalityEntries.length} piezas en el municipio</p></div><button type="button" onClick={startNew}>+ Nuevo</button></div>
        <label>Municipio<select value={form.municipalityId} onChange={(event) => setForm(blankForm(event.target.value))}>{municipalities.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar contenido…" />
        <div className={styles.entryList}>{visibleEntries.map((entry) => <button type="button" key={entry.id} data-active={form.id === entry.id ? 'true' : 'false'} onClick={() => edit(entry)}><span><strong>{entry.title}</strong><small>{ROLE_LABELS[roleOf(entry) ?? 'heritage']} · {entry.status}</small></span><b>{entry.featured ? '★' : ''}</b></button>)}</div>
      </aside>

      <div className={styles.mainColumn}>
        <section className={`admin-card ${styles.editorCard}`}>
          <div className={styles.editorHeading}><div><span>{form.id ? 'EDITANDO' : 'NUEVO CONTENIDO'}</span><h2>{form.title || 'Contenido municipal'}</h2></div>{municipality ? <a href={`/ayuntamientos/${municipality.slug}`} target="_blank" rel="noreferrer">Ver municipio ↗</a> : null}</div>
          <div className={styles.grid}>
            <label>Municipio<select value={form.municipalityId} onChange={(e) => setForm({ ...form, municipalityId: e.target.value })}>{municipalities.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
            <label>Tipo<select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as MunicipalityRole, featured: e.target.value === 'profile' ? false : form.featured })}>{Object.entries(ROLE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <label className={styles.wide}>Título<input value={form.title} onChange={(e) => changeTitle(e.target.value)} /></label>
            <label className={styles.wide}>Slug<input value={form.slug} onChange={(e) => setForm({ ...form, slug: slugify(e.target.value) })} /><small>{slugConflict ? '⚠ Ya existe' : 'Identificador único y estable'}</small></label>
            <label className={styles.wide}>Resumen<textarea rows={3} value={form.summary} onChange={(e) => setForm({ ...form, summary: e.target.value })} /></label>
            <label className={styles.wide}>Descripción / cuerpo<textarea rows={7} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} /></label>
            <label>Fuente oficial<input value={form.sourceUrl} onChange={(e) => setForm({ ...form, sourceUrl: e.target.value })} placeholder="https://…" /></label>
            <label>Nombre de la fuente<input value={form.sourceLabel} onChange={(e) => setForm({ ...form, sourceLabel: e.target.value })} placeholder="Ayuntamiento…" /></label>
            <label>Fecha de verificación<input type="date" value={form.verifiedAt} onChange={(e) => setForm({ ...form, verifiedAt: e.target.value })} /></label>
            <label>Enlace externo<input value={form.externalUrl} onChange={(e) => setForm({ ...form, externalUrl: e.target.value })} placeholder="https://…" /></label>
            <label>Estado<select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as CmsEntryStatus })}><option value="draft">Borrador</option><option value="published">Publicado</option><option value="archived">Archivado</option></select></label>
            <label>Prioridad<input type="number" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) || 0 })} /></label>
          </div>

          <section className={styles.mediaSection}>
            <div><h3>Imagen</h3><p>Selecciona una imagen de la biblioteca de Mágina o pega una URL pública.</p></div>
            <label>URL de imagen<input value={form.mediaUrl} onChange={(e) => setForm({ ...form, mediaUrl: e.target.value })} placeholder="https://…" /></label>
            {assets.length ? <div className={styles.mediaGrid}>{assets.map((asset) => { const url = mediaPublicUrl(asset); return <button type="button" key={asset.id} data-selected={form.mediaUrl === url ? 'true' : 'false'} onClick={() => setForm({ ...form, mediaUrl: url })}><img src={url} alt={asset.original_filename} loading="lazy" /><span>{asset.original_filename}</span></button>; })}</div> : <p>No hay imágenes subidas. <a href="/admin/media" target="_blank" rel="noreferrer">Abrir Multimedia ↗</a></p>}
            {form.mediaUrl ? <div className={styles.preview}><img src={form.mediaUrl} alt="Vista previa" /><button type="button" onClick={() => setForm({ ...form, mediaUrl: '' })}>Quitar imagen</button></div> : null}
          </section>

          {form.role !== 'profile' ? <label className={styles.featuredToggle}><input type="checkbox" checked={form.featured} onChange={(e) => setForm({ ...form, featured: e.target.checked })} /><span><strong>Mostrar en “Lo imprescindible”</strong><small>{featuredCount}/3 destacados activos en este municipio.</small></span></label> : <div className={styles.profileNote}>El perfil principal alimenta el hero del municipio. Al guardarlo como perfil, cualquier perfil anterior deja de ser principal.</div>}

          <div className={styles.saveBar}><button type="button" className={styles.secondary} onClick={startNew}>Cancelar / nuevo</button><button type="button" disabled={busy || slugConflict} onClick={() => void save()}>{busy ? 'Guardando…' : form.id ? 'Guardar cambios' : 'Crear contenido'}</button></div>
        </section>
      </div>
    </section>
  </main>;
}
