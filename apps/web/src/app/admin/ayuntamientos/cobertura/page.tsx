'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { adminApi, type CmsEntry } from '@/lib/admin-data-source';
import { apiFetch } from '@/lib/api-client';
import '../../admin.css';

type Directory = {
  official_website: string;
  electronic_office_url: string | null;
  transparency_url: string | null;
  tourism_url: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  postal_code: string | null;
  source_url: string;
  verified_at: string;
  public_enabled: boolean;
};

type Municipality = {
  id: string;
  ine_code: string;
  name: string;
  slug: string;
  active: boolean;
  place_count: number;
  public_place_count: number;
  directory: Directory | null;
};

type Counts = Record<'place' | 'mill' | 'directory' | 'news' | 'event', number>;

type CoverageRow = {
  municipality: Municipality;
  counts: Counts;
  checks: Array<{ key: string; label: string; ok: boolean }>;
  completed: number;
};

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function municipalityId(entry: CmsEntry) {
  const value = asObject(entry.content_json).municipality_id;
  return typeof value === 'string' ? value : '';
}

function visibleNow(entry: CmsEntry) {
  if (entry.status !== 'published') return false;
  const now = Date.now();
  if (entry.starts_at && new Date(entry.starts_at).getTime() > now) return false;
  if (entry.ends_at && new Date(entry.ends_at).getTime() < now) return false;
  return true;
}

function entryCounts(entries: CmsEntry[], id: string): Counts {
  const result: Counts = { place: 0, mill: 0, directory: 0, news: 0, event: 0 };
  for (const entry of entries) {
    if (!visibleNow(entry) || municipalityId(entry) !== id) continue;
    if (entry.type in result) result[entry.type as keyof Counts] += 1;
  }
  return result;
}

function buildChecks(municipality: Municipality, counts: Counts) {
  const directory = municipality.directory;
  return [
    { key: 'public', label: 'Ficha pública', ok: Boolean(directory?.public_enabled) },
    { key: 'website', label: 'Web oficial', ok: Boolean(directory?.official_website) },
    { key: 'phone', label: 'Teléfono', ok: Boolean(directory?.phone) },
    { key: 'email', label: 'Email', ok: Boolean(directory?.email) },
    { key: 'address', label: 'Dirección', ok: Boolean(directory?.address) },
    { key: 'institutional', label: 'Sede / transparencia / turismo', ok: Boolean(directory?.electronic_office_url || directory?.transparency_url || directory?.tourism_url) },
    { key: 'places', label: 'Localidades públicas', ok: municipality.public_place_count > 0 },
    { key: 'profile', label: 'Perfil editorial', ok: counts.place > 0 },
    { key: 'economy', label: 'Cooperativas / empresas', ok: counts.mill + counts.directory > 0 },
    { key: 'current', label: 'Noticias / eventos', ok: counts.news + counts.event > 0 },
  ];
}

export default function AdminMunicipalityCoveragePage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<Municipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [query, setQuery] = useState('');
  const [onlyIncomplete, setOnlyIncomplete] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [territory, content] = await Promise.all([
      apiFetch<{ municipalities: Municipality[] }>('/api/v1/admin/territory/catalog'),
      adminApi.content(),
    ]);
    setMunicipalities(territory.municipalities.filter((municipality) => municipality.active));
    setEntries(content.entries);
  }, []);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    void load().catch(() => setError('No se ha podido calcular la cobertura municipal.'));
  }, [auth.status, load]);

  const rows = useMemo<CoverageRow[]>(() => municipalities.map((municipality) => {
    const counts = entryCounts(entries, municipality.id);
    const checks = buildChecks(municipality, counts);
    return { municipality, counts, checks, completed: checks.filter((check) => check.ok).length };
  }).filter((row) => {
    const needle = query.trim().toLocaleLowerCase('es');
    if (onlyIncomplete && row.completed === row.checks.length) return false;
    if (!needle) return true;
    return `${row.municipality.name} ${row.municipality.ine_code}`.toLocaleLowerCase('es').includes(needle);
  }).sort((a, b) => a.completed - b.completed || a.municipality.name.localeCompare(b.municipality.name, 'es')), [municipalities, entries, query, onlyIncomplete]);

  const summary = useMemo(() => ({
    complete: rows.filter((row) => row.completed === 10).length,
    withEconomy: rows.filter((row) => row.counts.mill + row.counts.directory > 0).length,
    withCurrent: rows.filter((row) => row.counts.news + row.counts.event > 0).length,
    missingTourism: rows.filter((row) => !row.municipality.directory?.tourism_url).length,
  }), [rows]);

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Cobertura municipal</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar"><div><a href="/admin/ayuntamientos">← Ayuntamientos</a><h1>Cobertura municipal</h1><p>Control operativo de los 16 municipios. Son diez comprobaciones explícitas, no una puntuación editorial subjetiva.</p></div><div style={{display:'flex',gap:'.75rem',flexWrap:'wrap'}}><a href="/admin/ayuntamientos/actualidad">Actualidad</a><a href="/admin/web">CMS ↗</a></div></header>
    {error ? <div className="admin-notice error">{error}</div> : null}

    <section style={{display:'grid',gridTemplateColumns:'repeat(4,minmax(0,1fr))',gap:'.75rem',marginBottom:'1rem'}}>
      <article className="admin-card"><small>COBERTURA 10/10</small><h2>{summary.complete}</h2><p>Municipios con las diez señales.</p></article>
      <article className="admin-card"><small>ECONOMÍA LOCAL</small><h2>{summary.withEconomy}</h2><p>Con cooperativas, almazaras o servicios publicados.</p></article>
      <article className="admin-card"><small>ACTUALIDAD LOCAL</small><h2>{summary.withCurrent}</h2><p>Con noticias o eventos publicados y vinculados.</p></article>
      <article className="admin-card"><small>TURISMO</small><h2>{summary.missingTourism}</h2><p>Municipios aún sin enlace turístico institucional.</p></article>
    </section>

    <section className="admin-card">
      <div style={{display:'flex',gap:'.8rem',justifyContent:'space-between',alignItems:'center',flexWrap:'wrap'}}>
        <div><h2>Estado por municipio ({rows.length})</h2><p>El contenido editorial solo cuenta si está publicado, vigente y vinculado con `municipality_id`.</p></div>
        <div style={{display:'flex',gap:'.8rem',alignItems:'center',flexWrap:'wrap'}}>
          <label style={{display:'flex',gap:'.45rem',alignItems:'center'}}><input type="checkbox" checked={onlyIncomplete} onChange={(event)=>setOnlyIncomplete(event.target.checked)}/> Solo con huecos</label>
          <input type="search" value={query} onChange={(event)=>setQuery(event.target.value)} placeholder="Buscar municipio o INE…" style={{minWidth:'240px'}} />
        </div>
      </div>

      <div style={{display:'grid',gap:'.8rem',marginTop:'1rem'}}>{rows.map((row) => <article key={row.municipality.id} style={{padding:'1rem',border:'1px solid #d8ded8',borderRadius:'.9rem',background:'#fff'}}>
        <div style={{display:'flex',justifyContent:'space-between',gap:'1rem',alignItems:'flex-start',flexWrap:'wrap'}}>
          <div><small>INE {row.municipality.ine_code}</small><h3 style={{margin:'.2rem 0'}}>{row.municipality.name}</h3><p style={{margin:0}}>{row.completed}/10 comprobaciones · {row.municipality.public_place_count} localidades públicas</p></div>
          <div style={{display:'flex',gap:'.55rem',flexWrap:'wrap'}}><a href={`/admin/ayuntamientos#${row.municipality.slug}`}>Ficha institucional</a><a href="/admin/web">Contenido</a><a href={`/ayuntamientos/${row.municipality.slug}`} target="_blank">Ver público ↗</a></div>
        </div>
        <div style={{display:'grid',gridTemplateColumns:'repeat(5,minmax(0,1fr))',gap:'.45rem',marginTop:'.8rem'}}>{row.checks.map((check) => <div key={check.key} style={{padding:'.55rem .65rem',borderRadius:'.65rem',background:check.ok?'#eef7ee':'#fff3e6',border:`1px solid ${check.ok?'#cae0ca':'#ecd2b2'}`}}><strong>{check.ok ? '✓' : '·'} {check.label}</strong></div>)}</div>
        <div style={{display:'flex',gap:'.8rem',flexWrap:'wrap',marginTop:'.75rem'}}><small>Perfil: {row.counts.place}</small><small>Cooperativas/almazaras: {row.counts.mill}</small><small>Empresas/servicios: {row.counts.directory}</small><small>Noticias: {row.counts.news}</small><small>Eventos: {row.counts.event}</small></div>
      </article>)}{!rows.length ? <p>No hay municipios que coincidan con los filtros.</p> : null}</div>
    </section>
  </main>;
}
