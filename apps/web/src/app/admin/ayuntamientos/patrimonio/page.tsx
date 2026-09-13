'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { adminApi, type AdminTerritoryMunicipality, type CmsEntry } from '@/lib/admin-data-source';
import { MUNICIPALITY_HERITAGE_CATALOG, type MunicipalityHeritageSeed } from '@/lib/municipality-heritage-catalog';
import { MUNICIPALITY_DISCOVERY_CATALOG } from '@/lib/municipality-discovery-catalog';
import '../../admin.css';
import styles from './patrimonio.module.css';

type MunicipalityRole = '' | 'profile' | 'heritage' | 'nature' | 'tourism';

type Row = {
  entry: CmsEntry;
  municipalityId: string;
  role: MunicipalityRole;
};

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function stringField(entry: CmsEntry, key: string) {
  const value = asObject(entry.content_json)[key];
  return typeof value === 'string' ? value : '';
}

function roleOf(entry: CmsEntry): MunicipalityRole {
  const value = stringField(entry, 'municipality_role');
  return value === 'profile' || value === 'heritage' || value === 'nature' || value === 'tourism' ? value : '';
}

const ROLE_LABELS: Record<MunicipalityRole, string> = {
  '': 'Sin función municipal',
  profile: 'Perfil principal del municipio',
  heritage: 'Patrimonio',
  nature: 'Naturaleza',
  tourism: 'Lugar / recurso turístico',
};

const OFFICIAL_DISCOVERY_CATALOG = [...MUNICIPALITY_HERITAGE_CATALOG, ...MUNICIPALITY_DISCOVERY_CATALOG];

export default function AdminMunicipalityHeritagePage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<AdminTerritoryMunicipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [query, setQuery] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [importingAll, setImportingAll] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [content, territory] = await Promise.all([adminApi.content(), adminApi.territoryCatalog()]);
    setEntries(content.entries.filter((entry) => entry.type === 'place'));
    setMunicipalities(territory.municipalities.filter((municipality) => municipality.active));
  }, []);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    void load().catch(() => setError('No se han podido cargar los lugares y el catálogo municipal.'));
  }, [auth.status, load]);

  const rows = useMemo<Row[]>(() => entries.map((entry) => ({
    entry,
    municipalityId: stringField(entry, 'municipality_id'),
    role: roleOf(entry),
  })).filter(({ entry, municipalityId, role }) => {
    const needle = query.trim().toLocaleLowerCase('es');
    if (!needle) return true;
    const municipality = municipalities.find((item) => item.id === municipalityId);
    return `${entry.title} ${entry.summary ?? ''} ${municipality?.name ?? ''} ${ROLE_LABELS[role]}`.toLocaleLowerCase('es').includes(needle);
  }), [entries, municipalities, query]);

  const existingBySlug = useMemo(() => new Map(entries.map((entry) => [entry.slug, entry])), [entries]);
  const importedCount = OFFICIAL_DISCOVERY_CATALOG.filter((seed) => existingBySlug.has(seed.slug)).length;
  const heritageCount = OFFICIAL_DISCOVERY_CATALOG.filter((seed) => seed.role === 'heritage').length;
  const natureCount = OFFICIAL_DISCOVERY_CATALOG.filter((seed) => seed.role === 'nature').length;
  const tourismCount = OFFICIAL_DISCOVERY_CATALOG.filter((seed) => seed.role === 'tourism').length;

  async function save(entry: CmsEntry, nextMunicipalityId: string, nextRole: MunicipalityRole) {
    setBusyId(entry.id); setMessage(null); setError(null);
    try {
      const existing = asObject(entry.content_json);
      const nextContent = { ...existing };
      const municipality = municipalities.find((item) => item.id === nextMunicipalityId);

      if (municipality) {
        nextContent.municipality_id = municipality.id;
        nextContent.municipality_name = municipality.name;
        nextContent.municipality_slug = municipality.slug;
      } else {
        delete nextContent.municipality_id;
        delete nextContent.municipality_name;
        delete nextContent.municipality_slug;
      }

      if (nextRole) nextContent.municipality_role = nextRole;
      else delete nextContent.municipality_role;

      await adminApi.updateContent(entry.id, {
        type: entry.type,
        title: entry.title,
        slug: entry.slug,
        summary: entry.summary,
        content_json: nextContent,
        status: entry.status,
        featured: entry.featured,
        starts_at: entry.starts_at,
        ends_at: entry.ends_at,
        media_url: entry.media_url,
        external_url: entry.external_url,
        sort_order: entry.sort_order,
      });
      await load();
      setMessage(municipality
        ? `${entry.title} vinculado a ${municipality.name} como ${ROLE_LABELS[nextRole || '']}.`
        : `${entry.title} queda sin vínculo municipal.`);
    } catch {
      setError('No se ha podido guardar la clasificación territorial del lugar.');
    } finally {
      setBusyId(null);
    }
  }

  async function importSeed(seed: MunicipalityHeritageSeed, refresh = true) {
    const municipality = municipalities.find((item) => item.slug === seed.municipalitySlug);
    if (!municipality) throw new Error(`municipality_not_found:${seed.municipalitySlug}`);
    const existing = existingBySlug.get(seed.slug);
    const existingContent = existing ? asObject(existing.content_json) : {};
    const nextContent = {
      ...existingContent,
      municipality_id: municipality.id,
      municipality_name: municipality.name,
      municipality_slug: municipality.slug,
      municipality_role: seed.role,
      source_url: seed.sourceUrl,
      source_label: seed.sourceLabel,
      verified_at: seed.verifiedAt,
      body: typeof existingContent.body === 'string' && existingContent.body.trim() ? existingContent.body : seed.body,
    };

    setBusyId(`seed:${seed.slug}`); setMessage(null); setError(null);
    try {
      if (existing) {
        await adminApi.updateContent(existing.id, {
          type: 'place',
          title: existing.title || seed.title,
          slug: seed.slug,
          summary: existing.summary || seed.summary,
          content_json: nextContent,
          status: existing.status,
          featured: existing.featured,
          starts_at: existing.starts_at,
          ends_at: existing.ends_at,
          media_url: existing.media_url,
          external_url: existing.external_url || seed.sourceUrl,
          sort_order: existing.sort_order,
        });
      } else {
        await adminApi.createContent({
          type: 'place',
          title: seed.title,
          slug: seed.slug,
          summary: seed.summary,
          content_json: nextContent,
          status: 'published',
          featured: false,
          starts_at: null,
          ends_at: null,
          media_url: null,
          external_url: seed.sourceUrl,
          sort_order: 0,
        });
      }
      if (refresh) await load();
      setMessage(existing ? `${seed.title}: fuente y vínculo oficial actualizados.` : `${seed.title}: recurso oficial publicado.`);
    } finally {
      setBusyId(null);
    }
  }

  async function importAll() {
    setImportingAll(true); setMessage(null); setError(null);
    let completed = 0;
    try {
      for (const seed of OFFICIAL_DISCOVERY_CATALOG) {
        await importSeed(seed, false);
        completed += 1;
      }
      await load();
      setMessage(`Catálogo oficial importado/actualizado: ${completed} de ${OFFICIAL_DISCOVERY_CATALOG.length} recursos.`);
    } catch {
      await load().catch(() => undefined);
      setError(`La importación se detuvo tras ${completed} recursos. Revisa el registro y vuelve a ejecutar: es idempotente.`);
    } finally {
      setBusyId(null);
      setImportingAll(false);
    }
  }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Patrimonio y turismo</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar">
      <div><a href="/admin/ayuntamientos">← Ayuntamientos</a><h1>Patrimonio y turismo municipal</h1><p>Clasifica lugares existentes e importa recursos oficiales de patrimonio y naturaleza con fuente y fecha de verificación.</p></div>
      <div><a href="/admin/web">Abrir CMS ↗</a></div>
    </header>
    {message ? <div className="admin-notice success">{message}</div> : null}
    {error ? <div className="admin-notice error">{error}</div> : null}

    <section className={`admin-card ${styles.seedCard}`}>
      <div className={styles.toolbar}>
        <div>
          <span className={styles.kicker}>CATÁLOGO VERIFICADO · 16 MUNICIPIOS</span>
          <h2>Descubrimientos oficiales</h2>
          <p>{importedCount}/{OFFICIAL_DISCOVERY_CATALOG.length} recursos ya existen en el CMS · {heritageCount} patrimonio · {natureCount} naturaleza · {tourismCount} turismo. Importar no crea duplicados: actualiza por slug y conserva texto/imagen editados cuando ya existen.</p>
        </div>
        <button type="button" disabled={importingAll || Boolean(busyId)} onClick={() => void importAll()}>{importingAll ? 'Importando…' : `Importar / actualizar los ${OFFICIAL_DISCOVERY_CATALOG.length}`}</button>
      </div>
      <div className={styles.seedGrid}>
        {OFFICIAL_DISCOVERY_CATALOG.map((seed) => {
          const existing = existingBySlug.get(seed.slug);
          const busy = busyId === `seed:${seed.slug}`;
          return <article key={seed.slug} className={styles.seedItem}>
            <div className={styles.seedMeta}><span>{seed.municipalityName} · {ROLE_LABELS[seed.role]}</span><strong>{existing ? `CMS · ${existing.status}` : 'Pendiente'}</strong></div>
            <h3>{seed.title}</h3>
            <p>{seed.summary}</p>
            <div className={styles.seedActions}>
              <a href={seed.sourceUrl} target="_blank" rel="noreferrer">Fuente oficial ↗</a>
              <button type="button" disabled={importingAll || Boolean(busyId)} onClick={() => void importSeed(seed).catch(() => setError(`No se ha podido importar ${seed.title}.`))}>{busy ? 'Guardando…' : existing ? 'Actualizar fuente' : 'Importar y publicar'}</button>
            </div>
          </article>;
        })}
      </div>
      <p className={styles.auditNote}>Cada importación usa la sesión corporativa actual mediante el CMS: `created_by` / `updated_by` quedan asociados al administrador real. La procedencia oficial permanece en `source_url`, `source_label` y `verified_at`.</p>
    </section>

    <section className="admin-card">
      <div className={styles.toolbar}>
        <div><h2>Lugares CMS ({entries.length})</h2><p>`profile` identifica la ficha editorial principal. Patrimonio, naturaleza y turismo alimentan la sección pública “Qué descubrir”.</p></div>
        <input className={styles.search} type="search" value={query} onChange={(event)=>setQuery(event.target.value)} placeholder="Buscar lugar, municipio o función…" />
      </div>
      <div className={styles.list}>
        {rows.map(({ entry, municipalityId, role }) => <article key={entry.id} className={styles.row}>
          <div><small>LUGAR · {entry.status}</small><h3>{entry.title}</h3>{entry.summary ? <p>{entry.summary}</p> : null}</div>
          <label>Municipio<select disabled={busyId===entry.id || importingAll} value={municipalityId} onChange={(event)=>void save(entry,event.target.value,role)}><option value="">Sin municipio</option>{municipalities.map((municipality)=><option key={municipality.id} value={municipality.id}>{municipality.name}</option>)}</select></label>
          <label>Función<select disabled={busyId===entry.id || importingAll} value={role} onChange={(event)=>void save(entry,municipalityId,event.target.value as MunicipalityRole)}><option value="">Sin función municipal</option><option value="profile">Perfil principal</option><option value="heritage">Patrimonio</option><option value="nature">Naturaleza</option><option value="tourism">Recurso turístico</option></select></label>
        </article>)}
        {!rows.length ? <p>No hay lugares que coincidan con la búsqueda.</p> : null}
      </div>
    </section>
  </main>;
}
