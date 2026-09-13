'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { loadPublicMunicipality, type PublicMunicipalityDirectory } from '@/lib/public-territory-source';
import styles from '../municipalities.module.css';

export default function MunicipalityDetailPage() {
  const params = useParams<{ slug: string }>();
  const [item, setItem] = useState<PublicMunicipalityDirectory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const slug = typeof params.slug === 'string' ? params.slug : '';
    if (!slug) { setError(true); setLoading(false); return; }
    loadPublicMunicipality(slug)
      .then((municipality) => { if (!cancelled) { setItem(municipality); setError(false); } })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [params.slug]);

  return <main className="app-shell">
    <Topbar />
    <div className={`page ${styles.page}`}>
      {loading ? <div className={styles.state}><strong>Cargando ficha institucional…</strong></div> : null}
      {!loading && error ? <div className={styles.state}><strong>No se ha podido cargar este ayuntamiento.</strong><p>La ficha no existe o el directorio no está disponible.</p><Link href="/ayuntamientos">Volver al directorio</Link></div> : null}
      {item ? <>
        <section className={`${styles.hero} ${styles.detailHero}`}>
          <div><span className="eyebrow">AYUNTAMIENTO · SIERRA MÁGINA</span><h1>{item.name}</h1><p>Ficha institucional verificada. Código INE {item.ine_code} · {item.province_name}.</p></div>
          <div className={styles.actions}><a href={item.official_website} target="_blank" rel="noreferrer">Abrir web oficial ↗</a></div>
        </section>
        <section className="section">
          <div className={styles.heading}><div><h2>Información oficial</h2><p>Contacto y enlaces institucionales.</p></div><Link href="/ayuntamientos">← Todos los ayuntamientos</Link></div>
          <div className={styles.detailMeta}>
            <div className={styles.detailCard}><small>Web oficial</small><a href={item.official_website} target="_blank" rel="noreferrer">{item.official_website}</a></div>
            <div className={styles.detailCard}><small>Teléfono</small>{item.phone ? <a href={`tel:${item.phone.replace(/\s/g, '')}`}>{item.phone}</a> : <span>No publicado</span>}</div>
            <div className={styles.detailCard}><small>Correo electrónico</small>{item.email ? <a href={`mailto:${item.email}`}>{item.email}</a> : <span>No publicado</span>}</div>
            <div className={styles.detailCard}><small>Dirección</small><span>{[item.address, item.postal_code].filter(Boolean).join(' · ') || 'No publicada'}</span></div>
            {item.electronic_office_url ? <div className={styles.detailCard}><small>Sede electrónica</small><a href={item.electronic_office_url} target="_blank" rel="noreferrer">Abrir sede electrónica ↗</a></div> : null}
            {item.transparency_url ? <div className={styles.detailCard}><small>Transparencia</small><a href={item.transparency_url} target="_blank" rel="noreferrer">Abrir portal ↗</a></div> : null}
            {item.tourism_url ? <div className={styles.detailCard}><small>Turismo</small><a href={item.tourism_url} target="_blank" rel="noreferrer">Información turística ↗</a></div> : null}
            <div className={styles.detailCard}><small>Localidades publicadas</small><span>{item.places.map((place) => place.name).join(', ') || 'Sin localidades publicadas'}</span></div>
          </div>
          <p className={styles.source}>Datos institucionales verificados el {item.verified_at}. <a href={item.source_url} target="_blank" rel="noreferrer">Consultar fuente ↗</a></p>
        </section>
      </> : null}
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
