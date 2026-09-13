'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { loadPublicMunicipalities, type PublicMunicipalityDirectory } from '@/lib/public-territory-source';
import styles from './municipalities.module.css';

export default function MunicipalitiesPage() {
  const [items, setItems] = useState<PublicMunicipalityDirectory[]>([]);
  const [query, setQuery] = useState('');
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

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es-ES');
    if (!needle) return items;
    return items.filter((item) => {
      const haystack = [item.name, item.ine_code, item.province_name, ...item.places.map((place) => place.name)].join(' ');
      return haystack.toLocaleLowerCase('es-ES').includes(needle);
    });
  }, [items, query]);

  const totals = useMemo(() => items.reduce((acc, item) => ({
    mills: acc.mills + (item.content_counts?.mill ?? 0),
    services: acc.services + (item.content_counts?.directory ?? 0),
    current: acc.current + (item.content_counts?.news ?? 0) + (item.content_counts?.event ?? 0),
  }), { mills: 0, services: 0, current: 0 }), [items]);

  return <main className="app-shell">
    <Topbar />
    <div className={`page ${styles.page}`}>
      <section className={styles.hero}>
        <span className="eyebrow">TERRITORIO · SIERRA MÁGINA</span>
        <h1>Municipios y ayuntamientos</h1>
        <p>Directorio institucional y puerta de entrada a la información local de Sierra Mágina: ayuntamientos, localidades, cooperativas, servicios y actualidad vinculada.</p>
        <div className={styles.heroFacts}>
          <span>{items.length || 16} municipios</span>
          <span>{totals.mills} cooperativas / almazaras publicadas</span>
          <span>{totals.services} servicios publicados</span>
        </div>
        <label><span className="sr-only">Buscar municipio o localidad</span><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar municipio, localidad o código INE…" /></label>
      </section>

      <section className="section">
        <div className={styles.heading}><div><h2>Municipios</h2><p>{items.length ? `${items.length} fichas territoriales verificadas` : 'Directorio territorial'}</p></div><Link href="/explorar">← Volver a Explorar</Link></div>
        {loading ? <div className={styles.state}><strong>Cargando municipios…</strong></div> : null}
        {!loading && error ? <div className={styles.state}><strong>El directorio no está disponible.</strong><p>No mostramos enlaces de sustitución no verificados.</p></div> : null}
        {!loading && !error && filtered.length === 0 ? <div className={styles.state}><strong>No hay coincidencias.</strong><p>Prueba con el nombre del municipio, una localidad o el código INE.</p></div> : null}
        {!loading && !error ? <div className={styles.grid}>{filtered.map((item) => {
          const localContent = (item.content_counts?.mill ?? 0) + (item.content_counts?.directory ?? 0);
          const currentContent = (item.content_counts?.news ?? 0) + (item.content_counts?.event ?? 0);
          return <article className={styles.card} key={item.id}>
            <div><span className={styles.code}>INE {item.ine_code}</span><h2>{item.name}</h2><p>{item.places.map((place) => place.name).join(' · ') || item.province_name}</p></div>
            <div className={styles.cardStats}>
              <span><strong>{item.places.length}</strong> localidades</span>
              <span><strong>{localContent}</strong> economía local</span>
              <span><strong>{currentContent}</strong> actualidad</span>
            </div>
            <dl>{item.phone ? <><dt>Teléfono</dt><dd><a href={`tel:${item.phone.replace(/\s/g, '')}`}>{item.phone}</a></dd></> : null}{item.email ? <><dt>Email</dt><dd><a href={`mailto:${item.email}`}>{item.email}</a></dd></> : null}</dl>
            <div className={styles.actions}><Link href={`/ayuntamientos/${item.slug}`}>Explorar municipio</Link><a href={item.official_website} target="_blank" rel="noreferrer">Web oficial ↗</a></div>
          </article>;
        })}</div> : null}
      </section>
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
