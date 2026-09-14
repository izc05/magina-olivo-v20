'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { MunicipalityAdminNav } from '@/components/municipality-admin-nav';
import { adminApi, type AdminAuditEntry, type AdminTerritoryMunicipality, type CmsEntry } from '@/lib/admin-data-source';
import { municipalityAdminHref, readMunicipalitySlug, replaceMunicipalityContext } from '@/lib/municipality-admin-context';
import '../../admin.css';
import styles from './history.module.css';

function asObject(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function field(entry: CmsEntry, key: string) { const value = asObject(entry.content_json)[key]; return typeof value === 'string' ? value : ''; }
function metadataMentions(value: unknown, needles: string[]): boolean {
  if (typeof value === 'string') return needles.includes(value);
  if (Array.isArray(value)) return value.some((item) => metadataMentions(item, needles));
  if (value && typeof value === 'object') return Object.values(value as Record<string, unknown>).some((item) => metadataMentions(item, needles));
  return false;
}
function actionLabel(action: string) {
  const labels: Record<string, string> = {
    'content.created': 'Contenido creado', 'content.updated': 'Contenido actualizado', 'content.archived': 'Contenido archivado',
    'territory.municipality_directory_changed': 'Ficha institucional actualizada',
  };
  return labels[action] ?? action.replaceAll('.', ' · ');
}
function actorLabel(entry: AdminAuditEntry) { return entry.actor_user_id ? `${entry.actor_role} · ${entry.actor_user_id.slice(0, 8)}` : entry.actor_role; }
function formatDate(value: string) { try { return new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)); } catch { return value; } }

export default function MunicipalityAuditHistoryPage() {
  const auth = useAuth();
  const [municipalities, setMunicipalities] = useState<AdminTerritoryMunicipality[]>([]);
  const [entries, setEntries] = useState<CmsEntry[]>([]);
  const [audit, setAudit] = useState<AdminAuditEntry[]>([]);
  const [municipalityId, setMunicipalityId] = useState('');
  const [query, setQuery] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [territory, content, auditPayload] = await Promise.all([adminApi.territoryCatalog(), adminApi.content(), adminApi.audit()]);
    const active = territory.municipalities.filter((item) => item.active);
    setMunicipalities(active); setEntries(content.entries); setAudit(auditPayload.entries);
    setMunicipalityId((current) => {
      const requested = readMunicipalitySlug();
      const next = active.find((item) => requested && item.slug === requested) ?? active.find((item) => item.id === current) ?? active[0];
      if (next) replaceMunicipalityContext(next.slug);
      return next?.id ?? '';
    });
  }, []);

  useEffect(() => { if (auth.status === 'authenticated') void load().catch(() => setError('No se ha podido cargar el historial municipal.')); }, [auth.status, load]);

  const municipality = municipalities.find((item) => item.id === municipalityId) ?? null;
  const municipalEntries = useMemo(() => entries.filter((entry) => field(entry, 'municipality_id') === municipalityId), [entries, municipalityId]);
  const titleById = useMemo(() => new Map(municipalEntries.map((entry) => [entry.id, entry.title])), [municipalEntries]);
  const municipalTargetIds = useMemo(() => new Set([municipalityId, ...municipalEntries.map((entry) => entry.id)].filter(Boolean)), [municipalityId, municipalEntries]);
  const relevant = useMemo(() => audit.filter((entry) => {
    if (entry.target_id && municipalTargetIds.has(entry.target_id)) return true;
    if (!municipality) return false;
    return metadataMentions(entry.metadata, [municipality.id, municipality.slug]);
  }).filter((entry) => {
    const needle = query.trim().toLocaleLowerCase('es');
    if (!needle) return true;
    return `${entry.action} ${entry.target_type} ${entry.target_id ?? ''} ${titleById.get(entry.target_id ?? '') ?? ''} ${actorLabel(entry)}`.toLocaleLowerCase('es').includes(needle);
  }), [audit, municipalTargetIds, municipality, query, titleById]);

  function changeMunicipality(id: string) { setMunicipalityId(id); setQuery(''); const next = municipalities.find((item) => item.id === id); replaceMunicipalityContext(next?.slug); }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Historial municipal</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar"><div><a href={municipalityAdminHref('/admin/ayuntamientos', municipality?.slug)}>← Ayuntamientos</a><h1>Historial municipal</h1><p>Trazabilidad de cambios institucionales y editoriales usando la auditoría existente de la plataforma.</p></div><button type="button" onClick={() => void load()}>Actualizar</button></header>
    {error ? <div className="admin-notice error">{error}</div> : null}
    <MunicipalityAdminNav slug={municipality?.slug} name={municipality?.name} active="historial" />

    <section className={`admin-card ${styles.toolbar}`}>
      <label>Municipio<select value={municipalityId} onChange={(event) => changeMunicipality(event.target.value)}>{municipalities.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      <label>Buscar en historial<input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="acción, contenido, actor…" /></label>
      <div><strong>{relevant.length}</strong><span>eventos relacionados</span></div>
    </section>

    <section className="admin-card">
      <div className={styles.heading}><div><span>TRAZABILIDAD</span><h2>{municipality?.name ?? 'Municipio'}</h2><p>Solo se muestran eventos cuyo objetivo es el municipio, una pieza CMS actualmente vinculada o cuya metadata contiene su identidad canónica.</p></div><small>Solo lectura</small></div>
      <div className={styles.timeline}>{relevant.map((entry) => <article key={entry.id} className={styles.event}>
        <div className={styles.dot} />
        <div><div className={styles.meta}><time dateTime={entry.created_at}>{formatDate(entry.created_at)}</time><span>{actorLabel(entry)}</span></div><h3>{actionLabel(entry.action)}</h3><p>{titleById.get(entry.target_id ?? '') ?? `${entry.target_type}${entry.target_id ? ` · ${entry.target_id.slice(0, 8)}` : ''}`}</p>{entry.metadata ? <details><summary>Detalles técnicos</summary><pre>{JSON.stringify(entry.metadata, null, 2)}</pre></details> : null}</div>
      </article>)}{!relevant.length ? <div className={styles.empty}><strong>Sin eventos relacionados en la ventana de auditoría disponible.</strong><p>Esto no significa que nunca haya habido cambios: la vista reutiliza el conjunto que devuelve la auditoría corporativa actual.</p></div> : null}</div>
    </section>
  </main>;
}
