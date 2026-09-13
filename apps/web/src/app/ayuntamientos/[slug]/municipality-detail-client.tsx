'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import {
  loadPublicMunicipality,
  type PublicMunicipalityContent,
  type PublicMunicipalityDirectory,
} from '@/lib/public-territory-source';
import styles from '../municipalities.module.css';

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function text(value: unknown) {
  return typeof value === 'string' ? value : '';
}

function strings(value: unknown) {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

function municipalityRole(entry: PublicMunicipalityContent) {
  return text(asObject(entry.content_json).municipality_role);
}

function dateLabel(value: string | null) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' }).format(date);
}

function ContentCard({ entry }: { entry: PublicMunicipalityContent }) {
  const data = asObject(entry.content_json);
  const placeName = text(data.territory_place_name);
  const services = strings(data.services);
  const eventStart = text(data.event_start) || entry.starts_at;
  const href = entry.external_url || null;
  const role = municipalityRole(entry);
  const placeLabel = role === 'heritage' ? 'Patrimonio' : role === 'nature' ? 'Naturaleza' : role === 'tourism' ? 'Turismo' : 'Pueblo';
  const labels: Record<PublicMunicipalityContent['type'], string> = {
    place: placeLabel,
    mill: 'Cooperativa / almazara',
    directory: 'Empresa / servicio',
    news: 'Noticia',
    event: 'Evento',
  };

  return <article className={styles.contentCard}>
    {entry.media_url ? <div className={styles.contentMedia} style={{ backgroundImage: `url("${entry.media_url.replace(/"/g, '%22')}")` }} aria-hidden="true" /> : null}
    <div className={styles.contentBody}>
      <div className={styles.contentMeta}><span>{labels[entry.type]}</span>{entry.featured ? <strong>Destacado</strong> : null}</div>
      <h3>{entry.title}</h3>
      {placeName ? <small>{placeName}</small> : null}
      {entry.summary ? <p>{entry.summary}</p> : null}
      {entry.type === 'event' && dateLabel(eventStart) ? <div className={styles.contentFact}>📅 {dateLabel(eventStart)}</div> : null}
      {services.length ? <div className={styles.tags}>{services.slice(0, 4).map((service) => <span key={service}>{service}</span>)}</div> : null}
      {href ? <a className={styles.textLink} href={href} target="_blank" rel="noreferrer">Más información ↗</a> : null}
    </div>
  </article>;
}

export function MunicipalityDetailClient({ slug }: { slug: string }) {
  const [item, setItem] = useState<PublicMunicipalityDirectory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadPublicMunicipality(slug)
      .then((municipality) => { if (!cancelled) { setItem(municipality); setError(false); } })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [slug]);

  const content = item?.related_content ?? [];
  const places = useMemo(() => content.filter((entry) => entry.type === 'place'), [content]);
  const profile = useMemo(() => places.find((entry) => municipalityRole(entry) === 'profile') ?? places.find((entry) => !municipalityRole(entry)) ?? null, [places]);
  const discoveries = useMemo(() => places.filter((entry) => ['heritage', 'nature', 'tourism'].includes(municipalityRole(entry))), [places]);
  const mills = useMemo(() => content.filter((entry) => entry.type === 'mill'), [content]);
  const directory = useMemo(() => content.filter((entry) => entry.type === 'directory'), [content]);
  const news = useMemo(() => content.filter((entry) => entry.type === 'news'), [content]);
  const events = useMemo(() => content.filter((entry) => entry.type === 'event'), [content]);
  const profileData = asObject(profile?.content_json);
  const profileBody = text(profileData.body);
  const profileServices = strings(profileData.services);

  return <main className="app-shell">
    <Topbar />
    <div className={`page ${styles.page}`}>
      {loading ? <div className={styles.state}><strong>Cargando ficha municipal…</strong></div> : null}
      {!loading && error ? <div className={styles.state}><strong>No se ha podido cargar este ayuntamiento.</strong><p>La ficha no existe o el directorio no está disponible.</p><Link href="/ayuntamientos">Volver al directorio</Link></div> : null}
      {item ? <>
        <section className={`${styles.hero} ${styles.detailHero}`}>
          <div>
            <span className="eyebrow">MUNICIPIO · SIERRA MÁGINA</span>
            <h1>{item.name}</h1>
            <p>{profile?.summary || `Información institucional y contenido local verificado de ${item.name}. Código INE ${item.ine_code} · ${item.province_name}.`}</p>
            <div className={styles.heroFacts}>
              <span>{item.places.length} {item.places.length === 1 ? 'localidad' : 'localidades'}</span>
              <span>{discoveries.length} lugares para descubrir</span>
              <span>{item.content_counts?.mill ?? 0} cooperativas / almazaras</span>
              <span>{item.content_counts?.directory ?? 0} servicios</span>
            </div>
          </div>
          <div className={styles.actions}>
            <a href={item.official_website} target="_blank" rel="noreferrer">Web oficial ↗</a>
            {item.electronic_office_url ? <a href={item.electronic_office_url} target="_blank" rel="noreferrer">Sede electrónica ↗</a> : null}
          </div>
        </section>

        <nav className={styles.quickNav} aria-label="Secciones de la ficha municipal">
          <a href="#municipio">Municipio</a><a href="#descubrir">Qué descubrir</a><a href="#ayuntamiento">Ayuntamiento</a><a href="#economia-local">Economía local</a><a href="#actualidad">Actualidad</a>
        </nav>

        <section className="section" id="municipio">
          <div className={styles.heading}><div><h2>Descubre el municipio</h2><p>La ficha territorial crece con el contenido publicado desde Mágina Olivo.</p></div><Link href="/explorar">Explorar Mágina →</Link></div>
          <div className={styles.municipalityGrid}>
            <article className={`${styles.detailCard} ${styles.profileCard}`}>
              {profile?.media_url ? <div className={styles.profileImage} style={{ backgroundImage: `url("${profile.media_url.replace(/"/g, '%22')}")` }} aria-hidden="true" /> : null}
              <div><small>Perfil local</small><h3>{profile?.title || item.name}</h3>{profileBody ? <p>{profileBody}</p> : <p>El contenido editorial de este municipio se puede completar desde Administración sin modificar sus datos institucionales o GIS.</p>}{profileServices.length ? <div className={styles.tags}>{profileServices.slice(0, 6).map((service) => <span key={service}>{service}</span>)}</div> : null}</div>
            </article>
            <article className={styles.detailCard}>
              <small>Localidades y núcleos publicados</small>
              <div className={styles.placeList}>{item.places.map((place) => <span key={place.id}>{place.name}</span>)}</div>
            </article>
          </div>
        </section>

        <section className="section" id="descubrir">
          <div className={styles.heading}><div><h2>Patrimonio, naturaleza y lugares para descubrir</h2><p>Contenido turístico vinculado explícitamente al municipio y publicado desde el CMS de Mágina Olivo.</p></div>{item.tourism_url ? <a href={item.tourism_url} target="_blank" rel="noreferrer">Turismo oficial ↗</a> : null}</div>
          {discoveries.length ? <div className={styles.contentGrid}>{discoveries.map((entry) => <ContentCard key={entry.id} entry={entry} />)}</div> : <div className={styles.emptySection}><strong>Catálogo turístico preparado</strong><p>Aún no hay patrimonio, naturaleza o recursos turísticos publicados para {item.name}. No se generan lugares ficticios: aparecerán aquí cuando se clasifiquen explícitamente desde Administración.</p></div>}
        </section>

        <section className="section" id="ayuntamiento">
          <div className={styles.heading}><div><h2>Ayuntamiento</h2><p>Contacto y enlaces institucionales verificados.</p></div><Link href="/ayuntamientos">← Todos los ayuntamientos</Link></div>
          <div className={styles.detailMeta}>
            <div className={styles.detailCard}><small>Web oficial</small><a href={item.official_website} target="_blank" rel="noreferrer">{item.official_website}</a></div>
            <div className={styles.detailCard}><small>Teléfono</small>{item.phone ? <a href={`tel:${item.phone.replace(/\s/g, '')}`}>{item.phone}</a> : <span>No publicado</span>}</div>
            <div className={styles.detailCard}><small>Correo electrónico</small>{item.email ? <a href={`mailto:${item.email}`}>{item.email}</a> : <span>No publicado</span>}</div>
            <div className={styles.detailCard}><small>Dirección</small><span>{[item.address, item.postal_code].filter(Boolean).join(' · ') || 'No publicada'}</span></div>
            {item.electronic_office_url ? <div className={styles.detailCard}><small>Sede electrónica</small><a href={item.electronic_office_url} target="_blank" rel="noreferrer">Abrir sede electrónica ↗</a></div> : null}
            {item.transparency_url ? <div className={styles.detailCard}><small>Transparencia</small><a href={item.transparency_url} target="_blank" rel="noreferrer">Abrir portal ↗</a></div> : null}
            {item.tourism_url ? <div className={styles.detailCard}><small>Turismo</small><a href={item.tourism_url} target="_blank" rel="noreferrer">Información turística ↗</a></div> : null}
            <div className={styles.detailCard}><small>Identificación</small><span>INE {item.ine_code}{item.aemet_code ? ` · AEMET ${item.aemet_code}` : ''}</span></div>
          </div>
          <p className={styles.source}>Datos institucionales verificados el {item.verified_at}. <a href={item.source_url} target="_blank" rel="noreferrer">Consultar fuente ↗</a></p>
        </section>

        <section className="section" id="economia-local">
          <div className={styles.heading}><div><h2>Olivar y economía local</h2><p>Cooperativas, almazaras, empresas y servicios publicados y vinculados a este municipio.</p></div></div>
          {mills.length || directory.length ? <div className={styles.contentGrid}>{[...mills, ...directory].map((entry) => <ContentCard key={entry.id} entry={entry} />)}</div> : <div className={styles.emptySection}><strong>Directorio preparado</strong><p>Aún no hay cooperativas, almazaras o servicios publicados para este municipio. Cuando se añadan desde Admin aparecerán aquí automáticamente.</p></div>}
        </section>

        <section className="section" id="actualidad">
          <div className={styles.heading}><div><h2>Actualidad local</h2><p>Noticias y eventos que estén vinculados explícitamente al municipio.</p></div></div>
          {news.length || events.length ? <div className={styles.contentGrid}>{[...events, ...news].map((entry) => <ContentCard key={entry.id} entry={entry} />)}</div> : <div className={styles.emptySection}><strong>Sin actualidad vinculada</strong><p>No mostramos contenido por coincidencias de texto. Solo aparecerán noticias y eventos cuando el CMS los relacione de forma explícita con {item.name}.</p></div>}
        </section>
      </> : null}
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
