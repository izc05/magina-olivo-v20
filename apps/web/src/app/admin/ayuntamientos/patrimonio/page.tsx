'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { adminApi, type AdminTerritoryMunicipality, type CmsEntry } from '@/lib/admin-data-source';
import '../../admin.css';

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

export default function AdminMunicipalityHeritagePage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<AdminTerritoryMunicipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [query, setQuery] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
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

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Patrimonio y turismo</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar">
      <div><a href="/admin/ayuntamientos">← Ayuntamientos</a><h1>Patrimonio y turismo municipal</h1><p>Clasifica las entradas CMS de tipo lugar sin duplicarlas. El municipio y la función editorial se guardan dentro del contenido existente.</p></div>
      <div><a href="/admin/web">Abrir CMS ↗</a></div>
    </header>
    {message ? <div className="admin-notice success">{message}</div> : null}
    {error ? <div className="admin-notice error">{error}</div> : null}
    <section className="admin-card">
      <div style={{display:'flex',gap:'.8rem',justifyContent:'space-between',alignItems:'center',flexWrap:'wrap'}}>
        <div><h2>Lugares CMS ({entries.length})</h2><p>`profile` identifica la ficha editorial principal. Patrimonio, naturaleza y turismo alimentan la sección pública “Qué descubrir”.</p></div>
        <input type="search" value={query} onChange={(event)=>setQuery(event.target.value)} placeholder="Buscar lugar, municipio o función…" style={{minWidth:'280px'}} />
      </div>
      <div style={{display:'grid',gap:'.7rem',marginTop:'1rem'}}>
        {rows.map(({ entry, municipalityId, role }) => <article key={entry.id} style={{display:'grid',gridTemplateColumns:'minmax(0,1fr) minmax(220px,.45fr) minmax(220px,.45fr)',gap:'.8rem',alignItems:'end',padding:'1rem',border:'1px solid #d8ded8',borderRadius:'.9rem'}}>
          <div><small>LUGAR · {entry.status}</small><h3 style={{margin:'.25rem 0'}}>{entry.title}</h3>{entry.summary ? <p style={{margin:0}}>{entry.summary}</p> : null}</div>
          <label>Municipio<select disabled={busyId===entry.id} value={municipalityId} onChange={(event)=>void save(entry,event.target.value,role)}><option value="">Sin municipio</option>{municipalities.map((municipality)=><option key={municipality.id} value={municipality.id}>{municipality.name}</option>)}</select></label>
          <label>Función<select disabled={busyId===entry.id} value={role} onChange={(event)=>void save(entry,municipalityId,event.target.value as MunicipalityRole)}><option value="">Sin función municipal</option><option value="profile">Perfil principal</option><option value="heritage">Patrimonio</option><option value="nature">Naturaleza</option><option value="tourism">Recurso turístico</option></select></label>
        </article>)}
        {!rows.length ? <p>No hay lugares que coincidan con la búsqueda.</p> : null}
      </div>
    </section>
  </main>;
}
