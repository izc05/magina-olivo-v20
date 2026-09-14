'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { MunicipalityAdminNav } from '@/components/municipality-admin-nav';
import { apiFetch } from '@/lib/api-client';
import { municipalityAdminHref, readMunicipalitySlug, replaceMunicipalityContext } from '@/lib/municipality-admin-context';
import '../admin.css';
import styles from './municipalities-admin.module.css';

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
  directory: Directory | null;
};

type FormState = Directory & { id: string; name: string; slug: string; ine_code: string };
type ListFilter = 'all' | 'public' | 'hidden' | 'incomplete';

const completenessFields: Array<{ key: keyof Directory; label: string }> = [
  { key: 'official_website', label: 'Web oficial' },
  { key: 'phone', label: 'Teléfono' },
  { key: 'email', label: 'Email' },
  { key: 'address', label: 'Dirección' },
  { key: 'electronic_office_url', label: 'Sede electrónica' },
  { key: 'tourism_url', label: 'Turismo' },
  { key: 'source_url', label: 'Fuente' },
  { key: 'verified_at', label: 'Verificación' },
];

function completion(directory: Directory | null) {
  if (!directory) return { done: 0, total: completenessFields.length, percent: 0 };
  const done = completenessFields.filter(({ key }) => Boolean(directory[key])).length;
  return { done, total: completenessFields.length, percent: Math.round((done / completenessFields.length) * 100) };
}

export default function AdminMunicipalitiesPage() {
  const auth = useAuth();
  const [items, setItems] = useState<Municipality[]>([]);
  const [selected, setSelected] = useState<FormState | null>(null);
  const [query, setQuery] = useState('');
  const [listFilter, setListFilter] = useState<ListFilter>('all');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const payload = await apiFetch<{ municipalities: Municipality[] }>('/api/v1/admin/territory/catalog');
    setItems(payload.municipalities);
    setSelected((current) => {
      const requestedSlug = readMunicipalitySlug();
      const municipality = payload.municipalities.find((item) => requestedSlug && item.slug === requestedSlug)
        ?? payload.municipalities.find((item) => item.id === current?.id)
        ?? payload.municipalities[0];
      if (!municipality?.directory) return null;
      replaceMunicipalityContext(municipality.slug);
      return {
        id: municipality.id,
        name: municipality.name,
        slug: municipality.slug,
        ine_code: municipality.ine_code,
        ...municipality.directory,
      };
    });
  }, []);

  useEffect(() => {
    if (auth.status === 'authenticated') void load().catch(() => setError('No se ha podido cargar el directorio.'));
  }, [auth.status, load]);

  const stats = useMemo(() => {
    const publicCount = items.filter((item) => item.directory?.public_enabled).length;
    const hiddenCount = items.filter((item) => item.directory && !item.directory.public_enabled).length;
    const incompleteCount = items.filter((item) => completion(item.directory).percent < 100).length;
    const tourismCount = items.filter((item) => Boolean(item.directory?.tourism_url)).length;
    return { publicCount, hiddenCount, incompleteCount, tourismCount };
  }, [items]);

  const visibleItems = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es-ES');
    return items.filter((item) => {
      const matchesQuery = !needle || `${item.name} ${item.ine_code}`.toLocaleLowerCase('es-ES').includes(needle);
      if (!matchesQuery) return false;
      if (listFilter === 'public') return Boolean(item.directory?.public_enabled);
      if (listFilter === 'hidden') return Boolean(item.directory && !item.directory.public_enabled);
      if (listFilter === 'incomplete') return completion(item.directory).percent < 100;
      return true;
    });
  }, [items, listFilter, query]);

  const selectedCompletion = useMemo(() => completion(selected), [selected]);

  function choose(item: Municipality) {
    if (!item.directory) return;
    setSelected({ id: item.id, name: item.name, slug: item.slug, ine_code: item.ine_code, ...item.directory });
    replaceMunicipalityContext(item.slug);
    setMessage(null);
    setError(null);
  }

  async function save() {
    if (!selected) return;
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      const { id, name: _name, slug: _slug, ine_code: _ineCode, ...payload } = selected;
      await apiFetch(`/api/v1/admin/territory/municipalities/${id}/directory`, {
        method: 'PATCH',
        body: JSON.stringify(payload),
      });
      await load();
      setMessage('Ficha institucional actualizada y auditada.');
    } catch {
      setError('No se ha podido guardar. Revisa URLs, email y fecha de verificación.');
    } finally {
      setBusy(false);
    }
  }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Ayuntamientos</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className={`admin-topbar ${styles.topbar}`}>
      <div>
        <a href="/admin/territorio">← Territorio</a>
        <h1>Centro de control municipal</h1>
        <p>Gobierno, contenido y publicación de los 16 municipios de Sierra Mágina.</p>
      </div>
      <div className={styles.headerActions}>
        <a href={municipalityAdminHref('/admin/ayuntamientos/contenido', selected?.slug)}>Contenido</a>
        <a href={municipalityAdminHref('/admin/ayuntamientos/editorial', selected?.slug)}>Portada editorial</a>
        <a href={municipalityAdminHref('/admin/ayuntamientos/cobertura', selected?.slug)}>Cobertura</a>
        <a href={municipalityAdminHref('/admin/ayuntamientos/patrimonio', selected?.slug)}>Patrimonio y turismo</a>
        <a href={municipalityAdminHref('/admin/ayuntamientos/actualidad', selected?.slug)}>Noticias y eventos</a>
      </div>
    </header>

    {message ? <div className="admin-notice success">{message}</div> : null}
    {error ? <div className="admin-notice error">{error}</div> : null}
    <MunicipalityAdminNav slug={selected?.slug} name={selected?.name} active="ficha" />

    <section className={styles.summaryGrid} aria-label="Resumen municipal">
      <article className="admin-card"><span>Publicados</span><strong>{stats.publicCount}</strong><small>de {items.length || 16} municipios</small></article>
      <article className="admin-card"><span>Ocultos</span><strong>{stats.hiddenCount}</strong><small>no visibles en la web</small></article>
      <article className="admin-card"><span>Con huecos</span><strong>{stats.incompleteCount}</strong><small>fichas institucionales incompletas</small></article>
      <article className="admin-card"><span>Turismo oficial</span><strong>{stats.tourismCount}</strong><small>con enlace institucional</small></article>
    </section>

    <section className={styles.controlLayout}>
      <aside className={`admin-card ${styles.sidebar}`}>
        <div className={styles.sidebarHeading}><div><h2>Municipios</h2><p>{visibleItems.length} visibles en este filtro</p></div></div>
        <label className={styles.searchLabel}><span>Buscar municipio o INE</span><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Bedmar, 23902…" /></label>
        <div className={styles.filters} aria-label="Filtrar municipios">
          {([['all', 'Todos'], ['public', 'Públicos'], ['hidden', 'Ocultos'], ['incomplete', 'Con huecos']] as Array<[ListFilter, string]>).map(([value, label]) => <button key={value} type="button" aria-pressed={listFilter === value} className={listFilter === value ? styles.filterActive : styles.filter} onClick={() => setListFilter(value)}>{label}</button>)}
        </div>
        <div className={styles.municipalityList}>
          {visibleItems.map((item) => {
            const status = completion(item.directory);
            return <button id={item.slug} key={item.id} type="button" onClick={() => choose(item)} className={selected?.id === item.id ? styles.municipalityActive : styles.municipalityButton}>
              <span><strong>{item.name}</strong><small>INE {item.ine_code}</small></span>
              <span className={styles.listMeta}><b>{item.directory?.public_enabled ? 'Público' : 'Oculto'}</b><small>{status.percent}%</small></span>
            </button>;
          })}
          {visibleItems.length === 0 ? <p className={styles.empty}>No hay municipios que coincidan con el filtro.</p> : null}
        </div>
      </aside>

      <div className={styles.mainColumn}>
        {selected ? <>
          <section className={`admin-card ${styles.selectedHeader}`}>
            <div><span className={styles.kicker}>MUNICIPIO · INE {selected.ine_code}</span><h2>{selected.name}</h2><p>Desde aquí controlas la ficha institucional y accedes a todas las capas editoriales del municipio.</p></div>
            <div className={styles.publicState} data-enabled={selected.public_enabled ? 'true' : 'false'}><strong>{selected.public_enabled ? 'Publicado' : 'Oculto'}</strong><span>{selected.public_enabled ? 'Visible en la web pública' : 'No aparece públicamente'}</span></div>
          </section>

          <section className={styles.quickActions} aria-label={`Gestionar ${selected.name}`}>
            <a className="admin-card" href={`/ayuntamientos/${selected.slug}`} target="_blank" rel="noreferrer"><strong>Ver ficha pública ↗</strong><span>Comprueba cómo ve el usuario este municipio.</span></a>
            <a className="admin-card" href={municipalityAdminHref('/admin/ayuntamientos/contenido', selected.slug)}><strong>Editar contenido</strong><span>Crea y modifica perfil, patrimonio, naturaleza y turismo.</span></a>
            <a className="admin-card" href={municipalityAdminHref('/admin/ayuntamientos/editorial', selected.slug)}><strong>Portada editorial</strong><span>Elige perfil, hero y hasta tres imprescindibles.</span></a>
            <a className="admin-card" href={municipalityAdminHref('/admin/ayuntamientos/cobertura', selected.slug)}><strong>Revisar cobertura</strong><span>Detecta datos y contenido que todavía faltan.</span></a>
            <a className="admin-card" href={municipalityAdminHref('/admin/ayuntamientos/patrimonio', selected.slug)}><strong>Patrimonio y turismo</strong><span>Gestiona perfil, patrimonio, naturaleza y turismo.</span></a>
            <a className="admin-card" href={municipalityAdminHref('/admin/ayuntamientos/actualidad', selected.slug)}><strong>Noticias y eventos</strong><span>Vincula actualidad al municipio canónico.</span></a>
          </section>

          <section className={`admin-card ${styles.completeness}`}>
            <div className={styles.completenessHeader}><div><h3>Calidad de la ficha institucional</h3><p>{selectedCompletion.done} de {selectedCompletion.total} señales disponibles.</p></div><strong>{selectedCompletion.percent}%</strong></div>
            <div className={styles.progress}><span style={{ width: `${selectedCompletion.percent}%` }} /></div>
            <div className={styles.signalGrid}>{completenessFields.map(({ key, label }) => <span key={key} data-complete={Boolean(selected[key]) ? 'true' : 'false'}>{Boolean(selected[key]) ? '✓' : '–'} {label}</span>)}</div>
          </section>

          <section className={`admin-card ${styles.formCard}`}>
            <div className={styles.formHeading}><div><h3>Ficha institucional</h3><p>Los cambios se guardan en la fuente canónica y quedan auditados.</p></div><a href={selected.source_url} target="_blank" rel="noreferrer">Abrir fuente ↗</a></div>
            <div className={styles.formGrid}>
              <label>Web oficial<input value={selected.official_website} onChange={(e) => setSelected({ ...selected, official_website: e.target.value })} /></label>
              <label>Sede electrónica<input value={selected.electronic_office_url ?? ''} onChange={(e) => setSelected({ ...selected, electronic_office_url: e.target.value || null })} /></label>
              <label>Portal transparencia<input value={selected.transparency_url ?? ''} onChange={(e) => setSelected({ ...selected, transparency_url: e.target.value || null })} /></label>
              <label>Turismo<input value={selected.tourism_url ?? ''} onChange={(e) => setSelected({ ...selected, tourism_url: e.target.value || null })} /></label>
              <label>Teléfono<input value={selected.phone ?? ''} onChange={(e) => setSelected({ ...selected, phone: e.target.value || null })} /></label>
              <label>Email<input type="email" value={selected.email ?? ''} onChange={(e) => setSelected({ ...selected, email: e.target.value || null })} /></label>
              <label>Dirección<input value={selected.address ?? ''} onChange={(e) => setSelected({ ...selected, address: e.target.value || null })} /></label>
              <label>Código postal<input value={selected.postal_code ?? ''} onChange={(e) => setSelected({ ...selected, postal_code: e.target.value || null })} /></label>
              <label>Fuente de verificación<input value={selected.source_url} onChange={(e) => setSelected({ ...selected, source_url: e.target.value })} /></label>
              <label>Fecha verificación<input type="date" value={selected.verified_at.slice(0, 10)} onChange={(e) => setSelected({ ...selected, verified_at: e.target.value })} /></label>
            </div>
            <div className={styles.saveBar}><label className={styles.visibilityToggle}><input type="checkbox" checked={selected.public_enabled} onChange={(e) => setSelected({ ...selected, public_enabled: e.target.checked })} /><span>Visible públicamente</span></label><button type="button" disabled={busy} onClick={() => void save()}>{busy ? 'Guardando…' : 'Guardar cambios'}</button></div>
          </section>
        </> : <section className="admin-card"><p>No hay ficha seleccionada.</p></section>}
      </div>
    </section>
  </main>;
}
