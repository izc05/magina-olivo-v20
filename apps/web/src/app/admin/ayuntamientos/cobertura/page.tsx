'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { MunicipalityAdminNav } from '@/components/municipality-admin-nav';
import { adminApi, type CmsEntry } from '@/lib/admin-data-source';
import { apiFetch } from '@/lib/api-client';
import { municipalityAdminHref, readMunicipalitySlug, replaceMunicipalityContext } from '@/lib/municipality-admin-context';
import '../../admin.css';
import styles from './coverage.module.css';

type Directory = { official_website: string; electronic_office_url: string | null; transparency_url: string | null; tourism_url: string | null; phone: string | null; email: string | null; address: string | null; postal_code: string | null; source_url: string; verified_at: string; public_enabled: boolean; };
type Municipality = { id: string; ine_code: string; name: string; slug: string; active: boolean; place_count: number; public_place_count: number; directory: Directory | null; };
type Counts = Record<'place' | 'mill' | 'directory' | 'news' | 'event', number>;
type Signal = { key: string; label: string; ok: boolean };
type CoverageRow = { municipality: Municipality; counts: Counts; checks: Signal[]; completed: number; readiness: Signal[]; readinessCompleted: number };
function asObject(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function municipalityId(entry: CmsEntry) { const value = asObject(entry.content_json).municipality_id; return typeof value === 'string' ? value : ''; }
function municipalityRole(entry: CmsEntry) { const value = asObject(entry.content_json).municipality_role; return typeof value === 'string' ? value : ''; }
function textField(entry: CmsEntry, key: string) { const value = asObject(entry.content_json)[key]; return typeof value === 'string' ? value.trim() : ''; }
function visibleNow(entry: CmsEntry) { if (entry.status !== 'published') return false; const now = Date.now(); if (entry.starts_at && new Date(entry.starts_at).getTime() > now) return false; if (entry.ends_at && new Date(entry.ends_at).getTime() < now) return false; return true; }
function entryCounts(entries: CmsEntry[], id: string): Counts { const result: Counts = { place: 0, mill: 0, directory: 0, news: 0, event: 0 }; for (const entry of entries) { if (!visibleNow(entry) || municipalityId(entry) !== id) continue; if (entry.type in result) result[entry.type as keyof Counts] += 1; } return result; }
function buildChecks(municipality: Municipality, counts: Counts): Signal[] { const directory = municipality.directory; return [
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
]; }
function editorialReadiness(entries: CmsEntry[], id: string): Signal[] {
  const municipalPlaces = entries.filter((entry) => entry.type === 'place' && municipalityId(entry) === id && visibleNow(entry));
  const profile = municipalPlaces.find((entry) => municipalityRole(entry) === 'profile') ?? null;
  const discoveries = municipalPlaces.filter((entry) => ['heritage', 'nature', 'tourism'].includes(municipalityRole(entry)));
  return [
    { key: 'profile-main', label: 'Perfil principal publicado', ok: Boolean(profile) },
    { key: 'profile-summary', label: 'Resumen para buscadores y portada', ok: Boolean(profile?.summary?.trim()) },
    { key: 'profile-image', label: 'Imagen real para portada y compartir', ok: Boolean(profile?.media_url?.trim()) },
    { key: 'profile-source', label: 'Fuente y verificación del perfil', ok: Boolean(profile && textField(profile, 'source_url') && textField(profile, 'verified_at')) },
    { key: 'discovery', label: 'Al menos un descubrimiento publicado', ok: discoveries.length > 0 },
  ];
}

export default function AdminMunicipalityCoveragePage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<Municipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [contextId, setContextId] = useState('');
  const [query, setQuery] = useState('');
  const [onlyIncomplete, setOnlyIncomplete] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [territory, content] = await Promise.all([apiFetch<{ municipalities: Municipality[] }>('/api/v1/admin/territory/catalog'), adminApi.content()]);
    const active = territory.municipalities.filter((municipality) => municipality.active);
    setMunicipalities(active); setEntries(content.entries);
    setContextId((current) => {
      const requested = readMunicipalitySlug();
      const next = active.find((item) => requested && item.slug === requested) ?? active.find((item) => item.id === current) ?? active[0];
      if (next) replaceMunicipalityContext(next.slug);
      return next?.id ?? '';
    });
  }, []);

  useEffect(() => { if (auth.status === 'authenticated') void load().catch(() => setError('No se ha podido calcular la cobertura municipal.')); }, [auth.status, load]);

  const selectedMunicipality = municipalities.find((item) => item.id === contextId) ?? null;
  const allRows = useMemo<CoverageRow[]>(() => municipalities.map((municipality) => {
    const counts = entryCounts(entries, municipality.id);
    const checks = buildChecks(municipality, counts);
    const readiness = editorialReadiness(entries, municipality.id);
    return { municipality, counts, checks, completed: checks.filter((check) => check.ok).length, readiness, readinessCompleted: readiness.filter((check) => check.ok).length };
  }).sort((a, b) => a.completed - b.completed || a.municipality.name.localeCompare(b.municipality.name, 'es')), [municipalities, entries]);
  const rows = useMemo(() => allRows.filter((row) => {
    if (contextId && row.municipality.id !== contextId) return false;
    const needle = query.trim().toLocaleLowerCase('es');
    if (onlyIncomplete && row.completed === row.checks.length && row.readinessCompleted === row.readiness.length) return false;
    if (!needle) return true;
    return `${row.municipality.name} ${row.municipality.ine_code}`.toLocaleLowerCase('es').includes(needle);
  }), [allRows, query, onlyIncomplete, contextId]);
  const summary = useMemo(() => ({
    complete: allRows.filter((row) => row.completed === 10).length,
    editorialReady: allRows.filter((row) => row.readinessCompleted === 5).length,
    withEconomy: allRows.filter((row) => row.counts.mill + row.counts.directory > 0).length,
    withCurrent: allRows.filter((row) => row.counts.news + row.counts.event > 0).length,
  }), [allRows]);

  function changeContext(id: string) { setContextId(id); const next = municipalities.find((item) => item.id === id); replaceMunicipalityContext(next?.slug); setQuery(''); }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Cobertura municipal</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar"><div><a href={municipalityAdminHref('/admin/ayuntamientos', selectedMunicipality?.slug)}>← Ayuntamientos</a><h1>Cobertura municipal</h1><p>Control operativo y preparación editorial de los 16 municipios con señales objetivas.</p></div><div className={styles.actions}><a href={municipalityAdminHref('/admin/ayuntamientos/actualidad', selectedMunicipality?.slug)}>Actualidad</a><a href="/admin/web">CMS ↗</a></div></header>
    {error ? <div className="admin-notice error">{error}</div> : null}
    <MunicipalityAdminNav slug={selectedMunicipality?.slug} name={selectedMunicipality?.name} active="cobertura" />
    <section className="admin-card" style={{marginBottom:'1rem'}}><label>Municipio actual<select value={contextId} onChange={(event)=>changeContext(event.target.value)}>{municipalities.map((municipality)=><option key={municipality.id} value={municipality.id}>{municipality.name}</option>)}</select></label></section>
    <section className={styles.summaryGrid}>
      <article className="admin-card"><small>COBERTURA 10/10</small><h2>{summary.complete}</h2><p>Municipios con las diez señales institucionales y de contenido.</p></article>
      <article className="admin-card"><small>LISTOS EDITORIALMENTE 5/5</small><h2>{summary.editorialReady}</h2><p>Perfil, resumen, imagen, procedencia y descubrimiento publicados.</p></article>
      <article className="admin-card"><small>ECONOMÍA LOCAL</small><h2>{summary.withEconomy}</h2><p>Con cooperativas, almazaras o servicios publicados.</p></article>
      <article className="admin-card"><small>ACTUALIDAD LOCAL</small><h2>{summary.withCurrent}</h2><p>Con noticias o eventos publicados y vinculados.</p></article>
    </section>
    <section className="admin-card">
      <div className={styles.toolbar}><div><h2>Estado de {selectedMunicipality?.name ?? 'municipio'}</h2><p>La preparación editorial es diagnóstica: no inventa datos ni publica automáticamente nada.</p></div><div className={styles.filters}><label className={styles.checkbox}><input type="checkbox" checked={onlyIncomplete} onChange={(event)=>setOnlyIncomplete(event.target.checked)}/> Solo con huecos</label><input className={styles.search} type="search" value={query} onChange={(event)=>setQuery(event.target.value)} placeholder="Filtrar…" /></div></div>
      <div className={styles.rows}>{rows.map((row) => <article key={row.municipality.id} className={styles.row}>
        <div className={styles.rowHeader}><div><small>INE {row.municipality.ine_code}</small><h3 className={styles.rowTitle}>{row.municipality.name}</h3><p className={styles.rowText}>{row.completed}/10 cobertura · {row.readinessCompleted}/5 preparación editorial · {row.municipality.public_place_count} localidades públicas</p></div><div className={styles.actions}><a href={municipalityAdminHref('/admin/ayuntamientos', row.municipality.slug)}>Ficha institucional</a><a href={municipalityAdminHref('/admin/ayuntamientos/contenido', row.municipality.slug)}>Contenido</a><a href={municipalityAdminHref('/admin/ayuntamientos/editorial', row.municipality.slug)}>Portada</a><a href={`/ayuntamientos/${row.municipality.slug}`} target="_blank" rel="noreferrer">Ver público ↗</a></div></div>
        <h4>Comprobaciones de cobertura</h4>
        <div className={styles.signals}>{row.checks.map((check) => <div key={check.key} className={`${styles.signal} ${check.ok ? styles.signalOk : styles.signalMissing}`}><strong>{check.ok ? '✓' : '·'} {check.label}</strong></div>)}</div>
        <h4>Preparación editorial y SEO</h4>
        <div className={styles.signals}>{row.readiness.map((check) => <div key={check.key} className={`${styles.signal} ${check.ok ? styles.signalOk : styles.signalMissing}`}><strong>{check.ok ? '✓' : '·'} {check.label}</strong></div>)}</div>
        <div className={styles.meta}><small>Perfil/lugares: {row.counts.place}</small><small>Cooperativas/almazaras: {row.counts.mill}</small><small>Empresas/servicios: {row.counts.directory}</small><small>Noticias: {row.counts.news}</small><small>Eventos: {row.counts.event}</small></div>
      </article>)}{!rows.length ? <p>Este municipio no coincide con los filtros activos.</p> : null}</div>
    </section>
  </main>;
}
