'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { MunicipalityAdminNav } from '@/components/municipality-admin-nav';
import { adminApi, type AdminTerritoryMunicipality, type CmsEntry } from '@/lib/admin-data-source';
import { municipalityAdminHref, readMunicipalitySlug, replaceMunicipalityContext } from '@/lib/municipality-admin-context';
import '../../admin.css';
import styles from './gaps.module.css';

// This read-only Admin surface is included in the final municipal 16/16 QA matrix.
type GapCategory = 'all' | 'editorial' | 'discovery' | 'current' | 'economy';
type Signal = { key: string; label: string; category: Exclude<GapCategory, 'all'>; ok: boolean; fixHref: string; fixLabel: string };

function asObject(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function field(entry: CmsEntry, key: string) { const value = asObject(entry.content_json)[key]; return typeof value === 'string' ? value.trim() : ''; }
function municipalityId(entry: CmsEntry) { return field(entry, 'municipality_id'); }
function roleOf(entry: CmsEntry) { return field(entry, 'municipality_role'); }
function visibleNow(entry: CmsEntry) { if (entry.status !== 'published') return false; const now = Date.now(); if (entry.starts_at && new Date(entry.starts_at).getTime() > now) return false; if (entry.ends_at && new Date(entry.ends_at).getTime() < now) return false; return true; }

function buildSignals(municipality: AdminTerritoryMunicipality, entries: CmsEntry[]): Signal[] {
  const municipal = entries.filter((entry) => municipalityId(entry) === municipality.id && visibleNow(entry));
  const places = municipal.filter((entry) => entry.type === 'place');
  const profile = places.find((entry) => roleOf(entry) === 'profile') ?? null;
  const href = (path: string) => municipalityAdminHref(path, municipality.slug);
  return [
    { key: 'profile', label: 'Perfil principal publicado', category: 'editorial', ok: Boolean(profile), fixHref: href('/admin/ayuntamientos/contenido'), fixLabel: 'Crear o asignar perfil' },
    { key: 'summary', label: 'Resumen editorial', category: 'editorial', ok: Boolean(profile?.summary?.trim()), fixHref: href('/admin/ayuntamientos/contenido'), fixLabel: 'Editar resumen' },
    { key: 'image', label: 'Imagen hero real', category: 'editorial', ok: Boolean(profile?.media_url?.trim()), fixHref: href('/admin/ayuntamientos/editorial'), fixLabel: 'Configurar portada' },
    { key: 'source', label: 'Fuente + verificación', category: 'editorial', ok: Boolean(profile && field(profile, 'source_url') && field(profile, 'verified_at')), fixHref: href('/admin/ayuntamientos/contenido'), fixLabel: 'Completar procedencia' },
    { key: 'heritage', label: 'Patrimonio publicado', category: 'discovery', ok: places.some((entry) => roleOf(entry) === 'heritage'), fixHref: href('/admin/ayuntamientos/patrimonio'), fixLabel: 'Gestionar patrimonio' },
    { key: 'nature', label: 'Naturaleza publicada', category: 'discovery', ok: places.some((entry) => roleOf(entry) === 'nature'), fixHref: href('/admin/ayuntamientos/patrimonio'), fixLabel: 'Gestionar naturaleza' },
    { key: 'tourism', label: 'Turismo publicado', category: 'discovery', ok: places.some((entry) => roleOf(entry) === 'tourism'), fixHref: href('/admin/ayuntamientos/patrimonio'), fixLabel: 'Gestionar turismo' },
    { key: 'current', label: 'Actualidad local publicada', category: 'current', ok: municipal.some((entry) => entry.type === 'news' || entry.type === 'event'), fixHref: href('/admin/ayuntamientos/actualidad'), fixLabel: 'Vincular actualidad' },
    { key: 'economy', label: 'Economía local publicada', category: 'economy', ok: municipal.some((entry) => entry.type === 'mill' || entry.type === 'directory'), fixHref: href('/admin/ayuntamientos/cobertura'), fixLabel: 'Revisar cobertura' },
  ];
}

export default function MunicipalityGapMatrixPage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<AdminTerritoryMunicipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [contextId, setContextId] = useState('');
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<GapCategory>('all');
  const [onlyMissing, setOnlyMissing] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [territory, content] = await Promise.all([adminApi.territoryCatalog(), adminApi.content()]);
    const active = territory.municipalities.filter((item) => item.active).sort((a, b) => a.name.localeCompare(b.name, 'es'));
    setMunicipalities(active); setEntries(content.entries);
    setContextId((current) => {
      const requested = readMunicipalitySlug();
      const next = active.find((item) => requested && item.slug === requested) ?? active.find((item) => item.id === current) ?? active[0];
      if (next) replaceMunicipalityContext(next.slug);
      return next?.id ?? '';
    });
  }, []);

  useEffect(() => { if (auth.status === 'authenticated') void load().catch(() => setError('No se ha podido calcular la matriz de huecos.')); }, [auth.status, load]);

  const selectedMunicipality = municipalities.find((item) => item.id === contextId) ?? null;
  const rows = useMemo(() => municipalities.map((municipality) => ({ municipality, signals: buildSignals(municipality, entries) })), [municipalities, entries]);
  const visibleRows = useMemo(() => rows.map((row) => ({ ...row, visibleSignals: row.signals.filter((signal) => category === 'all' || signal.category === category).filter((signal) => !onlyMissing || !signal.ok) })).filter((row) => {
    const needle = query.trim().toLocaleLowerCase('es');
    if (needle && !`${row.municipality.name} ${row.municipality.ine_code}`.toLocaleLowerCase('es').includes(needle)) return false;
    return !onlyMissing || row.visibleSignals.length > 0;
  }), [rows, category, onlyMissing, query]);
  const totals = useMemo(() => {
    const allSignals = rows.flatMap((row) => row.signals);
    return { missing: allSignals.filter((signal) => !signal.ok).length, completeMunicipalities: rows.filter((row) => row.signals.every((signal) => signal.ok)).length };
  }, [rows]);

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Huecos municipales</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar"><div><a href={municipalityAdminHref('/admin/ayuntamientos/cobertura', selectedMunicipality?.slug)}>← Cobertura</a><h1>Mapa de huecos municipal</h1><p>Qué falta exactamente en los 16 municipios y qué herramienta resuelve cada ausencia.</p></div><button type="button" onClick={() => void load()}>Recalcular</button></header>
    {error ? <div className="admin-notice error">{error}</div> : null}
    <MunicipalityAdminNav slug={selectedMunicipality?.slug} name={selectedMunicipality?.name} active="huecos" />

    <section className={styles.summary}>
      <article className="admin-card"><span>HUECOS OBJETIVOS</span><strong>{totals.missing}</strong><small>señales ausentes entre los 16 municipios</small></article>
      <article className="admin-card"><span>SIN HUECOS EN ESTA MATRIZ</span><strong>{totals.completeMunicipalities}</strong><small>municipios con las 9 señales presentes</small></article>
      <article className="admin-card"><span>REGLA</span><strong>9</strong><small>señales binarias, sin nota ni ranking</small></article>
    </section>

    <section className={`admin-card ${styles.filters}`}>
      <label>Buscar municipio<input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Jódar, 23053…" /></label>
      <label>Área<select value={category} onChange={(event) => setCategory(event.target.value as GapCategory)}><option value="all">Todas</option><option value="editorial">Editorial</option><option value="discovery">Descubrimiento</option><option value="current">Actualidad</option><option value="economy">Economía</option></select></label>
      <label className={styles.checkbox}><input type="checkbox" checked={onlyMissing} onChange={(event) => setOnlyMissing(event.target.checked)} /> Solo huecos</label>
    </section>

    <section className={styles.matrix}>{visibleRows.map(({ municipality, signals, visibleSignals }) => {
      const missing = signals.filter((signal) => !signal.ok).length;
      return <article key={municipality.id} className={`admin-card ${styles.row}`} data-current={municipality.id === contextId ? 'true' : 'false'}>
        <div className={styles.identity}><div><small>INE {municipality.ine_code}</small><h2>{municipality.name}</h2><p>{missing ? `${missing} huecos de 9 señales` : 'Sin huecos en esta matriz'}</p></div><a href={municipalityAdminHref('/admin/ayuntamientos/huecos', municipality.slug)}>Trabajar aquí</a></div>
        <div className={styles.signals}>{visibleSignals.map((signal) => <div key={signal.key} className={signal.ok ? styles.ok : styles.missing}><span>{signal.ok ? '✓' : '·'} {signal.label}</span>{!signal.ok ? <a href={signal.fixHref}>{signal.fixLabel} →</a> : null}</div>)}</div>
        {!visibleSignals.length ? <p className={styles.empty}>No hay huecos para este municipio con los filtros actuales.</p> : null}
      </article>;
    })}{!visibleRows.length ? <div className="admin-card"><p>No hay municipios con huecos que coincidan con los filtros.</p></div> : null}</section>
  </main>;
}
