'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { MunicipalityAdminNav } from '@/components/municipality-admin-nav';
import { adminApi, type AdminTerritoryMunicipality, type CmsEntry } from '@/lib/admin-data-source';
import { municipalityAdminHref, readMunicipalitySlug, replaceMunicipalityContext } from '@/lib/municipality-admin-context';
import '../../admin.css';

type Row = { entry: CmsEntry; municipalityId: string; };
function asObject(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function municipalityId(entry: CmsEntry) { const value = asObject(entry.content_json).municipality_id; return typeof value === 'string' ? value : ''; }

export default function AdminMunicipalityCurrentAffairsPage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<AdminTerritoryMunicipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [contextId, setContextId] = useState('');
  const [query, setQuery] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [content, territory] = await Promise.all([adminApi.content(), adminApi.territoryCatalog()]);
    const active = territory.municipalities.filter((municipality) => municipality.active);
    setEntries(content.entries.filter((entry) => entry.type === 'news' || entry.type === 'event'));
    setMunicipalities(active);
    setContextId((current) => {
      const requested = readMunicipalitySlug();
      const next = active.find((item) => requested && item.slug === requested) ?? active.find((item) => item.id === current) ?? active[0];
      if (next) replaceMunicipalityContext(next.slug);
      return next?.id ?? '';
    });
  }, []);

  useEffect(() => { if (auth.status === 'authenticated') void load().catch(() => setError('No se ha podido cargar la actualidad y el catálogo municipal.')); }, [auth.status, load]);

  const selectedMunicipality = municipalities.find((item) => item.id === contextId) ?? null;
  const rows = useMemo<Row[]>(() => entries.map((entry) => ({ entry, municipalityId: municipalityId(entry) })).filter(({ entry, municipalityId: linkedId }) => {
    if (contextId && linkedId !== contextId && linkedId !== '') return false;
    const needle = query.trim().toLocaleLowerCase('es');
    if (!needle) return true;
    const municipality = municipalities.find((item) => item.id === linkedId);
    return `${entry.title} ${entry.summary ?? ''} ${municipality?.name ?? ''}`.toLocaleLowerCase('es').includes(needle);
  }), [entries, municipalities, query, contextId]);

  function changeContext(id: string) {
    setContextId(id);
    const next = municipalities.find((item) => item.id === id);
    replaceMunicipalityContext(next?.slug);
    setMessage(null); setError(null);
  }

  async function link(entry: CmsEntry, nextMunicipalityId: string) {
    setBusyId(entry.id); setMessage(null); setError(null);
    try {
      const existing = asObject(entry.content_json);
      const municipality = municipalities.find((item) => item.id === nextMunicipalityId);
      const nextContent = { ...existing };
      if (municipality) { nextContent.municipality_id = municipality.id; nextContent.municipality_name = municipality.name; nextContent.municipality_slug = municipality.slug; }
      else { delete nextContent.municipality_id; delete nextContent.municipality_name; delete nextContent.municipality_slug; }
      await adminApi.updateContent(entry.id, { type: entry.type, title: entry.title, slug: entry.slug, summary: entry.summary, content_json: nextContent, status: entry.status, featured: entry.featured, starts_at: entry.starts_at, ends_at: entry.ends_at, media_url: entry.media_url, external_url: entry.external_url, sort_order: entry.sort_order });
      await load();
      setMessage(municipality ? `${entry.type === 'event' ? 'Evento' : 'Noticia'} vinculado a ${municipality.name}.` : 'Vínculo municipal eliminado; el contenido vuelve a ser de ámbito general.');
    } catch { setError('No se ha podido guardar el vínculo municipal.'); }
    finally { setBusyId(null); }
  }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Actualidad municipal</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar"><div><a href={municipalityAdminHref('/admin/ayuntamientos', selectedMunicipality?.slug)}>← Ayuntamientos</a><h1>Actualidad municipal</h1><p>Relaciona noticias y eventos con un municipio canónico. El contenido solo aparecerá en su ficha cuando exista este vínculo explícito.</p></div><div><a href="/admin/web">Abrir CMS ↗</a></div></header>
    {message ? <div className="admin-notice success">{message}</div> : null}{error ? <div className="admin-notice error">{error}</div> : null}
    <MunicipalityAdminNav slug={selectedMunicipality?.slug} name={selectedMunicipality?.name} active="actualidad" />
    <section className="admin-card" style={{display:'grid',gap:'1rem',marginBottom:'1rem'}}>
      <label>Municipio actual<select value={contextId} onChange={(event)=>changeContext(event.target.value)}>{municipalities.map((municipality)=><option key={municipality.id} value={municipality.id}>{municipality.name}</option>)}</select></label>
      <p style={{margin:0}}>Se muestran contenidos ya vinculados a este municipio y contenidos de ámbito general que puedes asignarle.</p>
    </section>
    <section className="admin-card">
      <div style={{display:'flex',gap:'.8rem',justifyContent:'space-between',alignItems:'center',flexWrap:'wrap'}}><div><h2>Noticias y eventos ({rows.length})</h2><p>Seleccionar “Ámbito general” elimina la relación municipal sin borrar el contenido.</p></div><input type="search" value={query} onChange={(event)=>setQuery(event.target.value)} placeholder="Buscar contenido…" style={{minWidth:'260px'}} /></div>
      <div style={{display:'grid',gap:'.65rem',marginTop:'1rem'}}>{rows.map(({ entry, municipalityId: current }) => <article key={entry.id} style={{display:'grid',gridTemplateColumns:'minmax(0,1fr) minmax(220px,.45fr)',gap:'1rem',alignItems:'center',padding:'1rem',border:'1px solid #d8ded8',borderRadius:'.9rem'}}>
        <div><small>{entry.type === 'event' ? 'EVENTO' : 'NOTICIA'} · {entry.status}</small><h3 style={{margin:'.25rem 0'}}>{entry.title}</h3>{entry.summary ? <p style={{margin:0}}>{entry.summary}</p> : null}</div>
        <label>Municipio<select disabled={busyId === entry.id} value={current} onChange={(event)=>void link(entry,event.target.value)}><option value="">Ámbito general</option>{municipalities.map((municipality)=><option key={municipality.id} value={municipality.id}>{municipality.name}</option>)}</select></label>
      </article>)}{!rows.length ? <p>No hay noticias o eventos que coincidan con el municipio/filtro.</p> : null}</div>
    </section>
  </main>;
}
