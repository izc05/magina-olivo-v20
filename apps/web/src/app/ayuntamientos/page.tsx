'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { loadPublicMunicipalities, type PublicMunicipalityDirectory } from '@/lib/public-territory-source';
import styles from './municipalities.module.css';

type DirectoryFilter = 'all' | 'tourism' | 'current' | 'economy';
type DirectorySort = 'alpha' | 'content' | 'current';

const DIRECTORY_FILTERS: Array<{ value: DirectoryFilter; label: string }> = [
  { value: 'all', label: 'Todos' },
  { value: 'tourism', label: 'Turismo oficial' },
  { value: 'current', label: 'Con actualidad' },
  { value: 'economy', label: 'Economía local' },
];

function economyCount(item: PublicMunicipalityDirectory) {
  return (item.content_counts?.mill ?? 0) + (item.content_counts?.directory ?? 0);
}

function currentCount(item: PublicMunicipalityDirectory) {
  return (item.content_counts?.news ?? 0) + (item.content_counts?.event ?? 0);
}

function publishedContentCount(item: PublicMunicipalityDirectory) {
  return Object.values(item.content_counts ?? {}).reduce((sum, value) => sum + (typeof value === 'number' ? value : 0), 0);
}

export default function MunicipalitiesPage() {
  const [items, setItems] = useState<PublicMunicipalityDirectory[]>([]);
  const [query, setQuery] = useState('');
  const [directoryFilter, setDirectoryFilter] = useState<DirectoryFilter>('all');
  const [directorySort, setDirectorySort] = useState<DirectorySort>('alpha');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadPublicMunicipalities()
      .then((municipalities) => { if (!cancelled) { setItems(municipalities); setError(false); } })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const filterCounts = useMemo(() => ({
    all: items.length,
    tourism: items.filter((item) => Boolean(item.tourism_url)).length,
    current: items.filter((item) => currentCount(item) > 0).length,
    economy: items.filter((item) => economyCount(item) > 0).length,
  }), [items]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es-ES');
    const matches = items.filter((item) => {
      const haystack = [item.name, item.ine_code, item.province_name, ...item.places.map((place) => place.name)].join(' ');
      const queryMatches = !needle || haystack.toLocaleLowerCase('es-ES').includes(needle);
      if (!queryMatches) return false;
      if (directoryFilter === 'tourism') return Boolean(item.tourism_url);
      if (directoryFilter === 'current') return currentCount(item) > 0;
      if (directoryFilter === 'economy') return economyCount(item) > 0;
      return true;
    });

    return [...matches].sort((a, b) => {
      if (directorySort === 'content') return publishedContentCount(b) - publishedContentCount(a) || a.name.localeCompare(b.name, 'es');
      if (directorySort === 'current') return currentCount(b) - currentCount(a) || a.name.localeCompare(b.name, 'es');
      return a.name.localeCompare(b.name, 'es');
    });
  }, [items, query, directoryFilter, directorySort]);

  const totals = useMemo(() => items.reduce((acc, item) => ({
    localities: acc.localities + item.places.length,
    mills: acc.mills + (item.content_counts?.mill ?? 0),
    services: acc.services + (item.content_counts?.directory ?? 0),
    current: acc.current + currentCount(item),
  }), { localities: 0, mills: 0, services: 0, current: 0 }), [items]);

  return <main className="app-shell">
    <Topbar />
    <div className={`page ${styles.page}`}>
      <section className={`${styles.hero} ${styles.directoryHero}`}>
        <span className="eyebrow">EXPLORA · SIERRA MÁGINA</span>
        <h1>16 municipios. Un territorio por descubrir.</h1>
        <p>Entra por cada pueblo para conocer sus lugares, patrimonio, naturaleza, ayuntamiento, cooperativas, servicios y actualidad publicada en Mágina Olivo.</p>
        <div className={styles.directoryHeroStats} aria-label="Resumen territorial">
          <div><strong>{items.length || 16}</strong><span>municipios</span></div>
          <div><strong>{totals.localities}</strong><span>localidades publicadas</span></div>
          <div><strong>{filterCounts.tourism}</strong><span>con turismo oficial</span></div>
          <div><strong>{totals.current}</strong><span>noticias y eventos</span></div>
        </div>
        <label className={styles.directorySearch}>
          <span className="sr-only">Buscar municipio o localidad</span>
          <input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Busca Bedmar, Jimena, Solera, Arbuniel…" />
        </label>
      </section>

      <section className={styles.directoryControls} aria-label="Explorar municipios">
        <div className={styles.directoryFilters}>
          {DIRECTORY_FILTERS.map((filter) => <button
            key={filter.value}
            type="button"
            className={directoryFilter === filter.value ? styles.directoryFilterActive : styles.directoryFilter}
            aria-pressed={directoryFilter === filter.value}
            onClick={() => setDirectoryFilter(filter.value)}
          >
            <span>{filter.label}</span><strong>{filterCounts[filter.value]}</strong>
          </button>)}
        </div>
        <label className={styles.directorySort}>
          <span>Ordenar</span>
          <select value={directorySort} onChange={(event) => setDirectorySort(event.target.value as DirectorySort)}>
            <option value="alpha">A–Z</option>
            <option value="content">Más contenido publicado</option>
            <option value="current">Más actualidad</option>
          </select>
        </label>
      </section>

      <section className="section">
        <div className={styles.heading}>
          <div><h2>Explora los pueblos</h2><p aria-live="polite">{loading ? 'Cargando directorio…' : `${filtered.length} de ${items.length || 16} municipios visibles`}</p></div>
          <Link href="/explorar">← Volver a Explorar</Link>
        </div>
        {loading ? <div className={styles.state}><strong>Cargando municipios…</strong></div> : null}
        {!loading && error ? <div className={styles.state}><strong>El directorio no está disponible.</strong><p>No mostramos enlaces de sustitución no verificados.</p></div> : null}
        {!loading && !error && filtered.length === 0 ? <div className={styles.state}><strong>No hay coincidencias.</strong><p>Cambia el filtro o prueba con el nombre de un municipio, localidad o código INE.</p><button type="button" className={styles.resetDirectory} onClick={() => { setQuery(''); setDirectoryFilter('all'); setDirectorySort('alpha'); }}>Ver los 16 municipios</button></div> : null}
        {!loading && !error ? <div className={styles.directoryGrid}>{filtered.map((item) => {
          const localContent = economyCount(item);
          const currentContent = currentCount(item);
          const totalContent = publishedContentCount(item);
          return <article className={styles.directoryCard} key={item.id}>
            <div className={styles.directoryCardTop}>
              <div><span className={styles.code}>INE {item.ine_code}</span><h2>{item.name}</h2></div>
              <strong className={styles.directoryContentTotal}>{totalContent}<small>contenidos</small></strong>
            </div>
            <p className={styles.directoryPlaces}>{item.places.map((place) => place.name).join(' · ') || item.province_name}</p>
            <div className={styles.directorySignals}>
              <span><strong>{item.places.length}</strong> localidades</span>
              <span><strong>{localContent}</strong> economía local</span>
              <span><strong>{currentContent}</strong> actualidad</span>
            </div>
            <div className={styles.directoryBadges} aria-label={`Servicios disponibles en ${item.name}`}>
              {item.tourism_url ? <span>Turismo oficial</span> : null}
              {item.electronic_office_url ? <span>Sede electrónica</span> : null}
              {currentContent > 0 ? <span>Actualidad local</span> : null}
              {localContent > 0 ? <span>Economía local</span> : null}
            </div>
            <div className={styles.directoryContact}>
              {item.phone ? <a href={`tel:${item.phone.replace(/\s/g, '')}`}>{item.phone}</a> : <span>Teléfono no publicado</span>}
              {item.email ? <a href={`mailto:${item.email}`}>Correo ↗</a> : null}
            </div>
            <div className={styles.directoryActions}>
              <Link href={`/ayuntamientos/${item.slug}`}>Descubrir {item.name}</Link>
              <a href={item.official_website} target="_blank" rel="noreferrer">Ayuntamiento ↗</a>
            </div>
          </article>;
        })}</div> : null}
      </section>
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
