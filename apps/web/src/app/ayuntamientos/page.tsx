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
    return items.filter((item) => `${item.name} ${item.ine_code}`.toLocaleLowerCase('es-ES').includes(needle));
  }, [items, query]);

  return <main className="app-shell">
    <Topbar />
    <div className={`page ${styles.page}`}>
      <section className={styles.hero}>
        <span className="eyebrow">INSTITUCIONES DE SIERRA MÁGINA</span>
        <h1>Ayuntamientos</h1>
        <p>Directorio institucional de los municipios de Sierra Mágina. Los enlaces conducen a las webs oficiales y cada registro conserva su fuente y fecha de verificación.</p>
        <label><span className="sr-only">Buscar ayuntamiento</span><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar municipio…" /></label>
      </section>

      <section className="section">
        <div className={styles.heading}><div><h2>Municipios</h2><p>{items.length ? `${items.length} fichas institucionales` : 'Directorio institucional'}</p></div><Link href="/explorar">← Volver a Explorar</Link></div>
        {loading ? <div className={styles.state}><strong>Cargando ayuntamientos…</strong></div> : null}
        {!loading && error ? <div className={styles.state}><strong>El directorio no está disponible.</strong><p>No mostramos enlaces de sustitución no verificados.</p></div> : null}
        {!loading && !error && filtered.length === 0 ? <div className={styles.state}><strong>No hay coincidencias.</strong></div> : null}
        {!loading && !error ? <div className={styles.grid}>{filtered.map((item) => <article className={styles.card} key={item.id}>
          <div><span className={styles.code}>INE {item.ine_code}</span><h2>{item.name}</h2><p>{item.province_name}</p></div>
          <dl>{item.phone ? <><dt>Teléfono</dt><dd><a href={`tel:${item.phone.replace(/\s/g, '')}`}>{item.phone}</a></dd></> : null}{item.email ? <><dt>Email</dt><dd><a href={`mailto:${item.email}`}>{item.email}</a></dd></> : null}</dl>
          <div className={styles.actions}><Link href={`/ayuntamientos/${item.slug}`}>Ver ficha</Link><a href={item.official_website} target="_blank" rel="noreferrer">Web oficial ↗</a></div>
        </article>)}</div> : null}
      </section>
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
