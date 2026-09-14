'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { MunicipalityAdminNav } from '@/components/municipality-admin-nav';
import { adminApi, type AdminTerritoryMunicipality, type CmsEntry } from '@/lib/admin-data-source';
import { municipalityAdminHref, readMunicipalitySlug, replaceMunicipalityContext } from '@/lib/municipality-admin-context';
import '../../admin.css';

type NoticePriority = 'normal' | 'important' | 'urgent';
type Row = {
  entry: CmsEntry;
  municipalityId: string;
  isNotice: boolean;
  priority: NoticePriority;
};

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function rowFromEntry(entry: CmsEntry): Row {
  const content = asObject(entry.content_json);
  const rawPriority = content.notice_priority;
  return {
    entry,
    municipalityId: typeof content.municipality_id === 'string' ? content.municipality_id : '',
    isNotice: content.municipal_notice === true,
    priority: rawPriority === 'urgent' || rawPriority === 'important' || rawPriority === 'normal' ? rawPriority : 'normal',
  };
}

function priorityLabel(priority: NoticePriority) {
  if (priority === 'urgent') return 'Urgente';
  if (priority === 'important') return 'Importante';
  return 'Normal';
}

export default function AdminMunicipalityNoticesPage() {
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
      const next = active.find((item) => requested && item.slug === requested)
        ?? active.find((item) => item.id === current)
        ?? active[0];
      if (next) replaceMunicipalityContext(next.slug);
      return next?.id ?? '';
    });
  }, []);

  useEffect(() => {
    if (auth.status === 'authenticated') {
      void load().catch(() => setError('No se han podido cargar los avisos municipales.'));
    }
  }, [auth.status, load]);

  const selectedMunicipality = municipalities.find((item) => item.id === contextId) ?? null;

  const rows = useMemo(() => entries
    .map(rowFromEntry)
    .filter((row) => {
      if (!contextId || row.municipalityId !== contextId) return false;
      const needle = query.trim().toLocaleLowerCase('es');
      if (!needle) return true;
      return `${row.entry.title} ${row.entry.summary ?? ''}`.toLocaleLowerCase('es').includes(needle);
    }), [entries, query, contextId]);

  const activeNoticeCount = rows.filter((row) => row.isNotice).length;

  function changeContext(id: string) {
    setContextId(id);
    const next = municipalities.find((item) => item.id === id);
    replaceMunicipalityContext(next?.slug);
    setMessage(null);
    setError(null);
  }

  async function saveNotice(entry: CmsEntry, enabled: boolean, priority: NoticePriority) {
    setBusyId(entry.id);
    setMessage(null);
    setError(null);
    try {
      const existing = asObject(entry.content_json);
      const nextContent = { ...existing };
      if (enabled) {
        nextContent.municipal_notice = true;
        nextContent.notice_priority = priority;
      } else {
        delete nextContent.municipal_notice;
        delete nextContent.notice_priority;
      }

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
      setMessage(enabled
        ? `Aviso activado como ${priorityLabel(priority).toLocaleLowerCase('es')}.`
        : 'El contenido deja de tratarse como aviso municipal.');
    } catch {
      setError('No se ha podido actualizar el aviso municipal.');
    } finally {
      setBusyId(null);
    }
  }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Avisos municipales</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar">
      <div>
        <a href={municipalityAdminHref('/admin/ayuntamientos', selectedMunicipality?.slug)}>← Ayuntamientos</a>
        <h1>Avisos municipales</h1>
        <p>Convierte noticias o eventos ya vinculados al municipio en avisos prioritarios para las personas que siguen ese pueblo.</p>
      </div>
      <div><a href={municipalityAdminHref('/admin/ayuntamientos/actualidad', selectedMunicipality?.slug)}>Gestionar actualidad ↗</a></div>
    </header>

    {message ? <div className="admin-notice success">{message}</div> : null}
    {error ? <div className="admin-notice error">{error}</div> : null}
    <MunicipalityAdminNav slug={selectedMunicipality?.slug} name={selectedMunicipality?.name} active="avisos" />

    <section className="admin-card" style={{ display: 'grid', gap: '1rem', marginBottom: '1rem' }}>
      <label>Municipio actual
        <select value={contextId} onChange={(event) => changeContext(event.target.value)}>
          {municipalities.map((municipality) => <option key={municipality.id} value={municipality.id}>{municipality.name}</option>)}
        </select>
      </label>
      <div style={{ display: 'flex', gap: '.75rem', flexWrap: 'wrap' }}>
        <strong>{activeNoticeCount} aviso{activeNoticeCount === 1 ? '' : 's'} activo{activeNoticeCount === 1 ? '' : 's'}</strong>
        <span>Los avisos solo usan contenido ya publicado/vinculado; no crean una copia paralela.</span>
      </div>
    </section>

    <section className="admin-card">
      <div style={{ display: 'flex', gap: '.8rem', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap' }}>
        <div>
          <h2>Actualidad de {selectedMunicipality?.name ?? 'municipio'} ({rows.length})</h2>
          <p>Usa Normal para información útil, Importante para incidencias relevantes y Urgente solo cuando necesite máxima prioridad.</p>
        </div>
        <input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar contenido…" style={{ minWidth: '260px' }} />
      </div>

      <div style={{ display: 'grid', gap: '.65rem', marginTop: '1rem' }}>
        {rows.map(({ entry, isNotice, priority }) => <article key={entry.id} style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(250px,.45fr)', gap: '1rem', alignItems: 'center', padding: '1rem', border: '1px solid #d8ded8', borderRadius: '.9rem' }}>
          <div>
            <small>{entry.type === 'event' ? 'EVENTO' : 'NOTICIA'} · {entry.status}{isNotice ? ` · AVISO ${priorityLabel(priority).toLocaleUpperCase('es')}` : ''}</small>
            <h3 style={{ margin: '.25rem 0' }}>{entry.title}</h3>
            {entry.summary ? <p style={{ margin: 0 }}>{entry.summary}</p> : null}
          </div>
          <div style={{ display: 'grid', gap: '.55rem' }}>
            <label>Prioridad
              <select disabled={busyId === entry.id || !isNotice} value={priority} onChange={(event) => void saveNotice(entry, true, event.target.value as NoticePriority)}>
                <option value="normal">Normal</option>
                <option value="important">Importante</option>
                <option value="urgent">Urgente</option>
              </select>
            </label>
            <button type="button" disabled={busyId === entry.id} onClick={() => void saveNotice(entry, !isNotice, priority)}>
              {busyId === entry.id ? 'Guardando…' : isNotice ? 'Desactivar aviso' : 'Activar como aviso'}
            </button>
          </div>
        </article>)}
        {!rows.length ? <p>No hay noticias o eventos vinculados a este municipio. Asígnalos primero desde Actualidad municipal.</p> : null}
      </div>
    </section>
  </main>;
}
