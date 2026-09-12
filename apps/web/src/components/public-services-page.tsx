'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { loadPublicServices, type PublicService } from '@/lib/public-services-source';
import styles from './public-services.module.css';

function safeExternalUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
  } catch {
    return null;
  }
}

function safeMediaUrl(value: string | null): string | null {
  if (!value) return null;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  return safeExternalUrl(value);
}

function safePhoneHref(value: string | null): string | null {
  if (!value) return null;
  const trimmed = value.trim();
  const digits = trimmed.replace(/\D/g, '');
  if (digits.length < 6 || digits.length > 15) return null;
  return `tel:${trimmed.startsWith('+') ? '+' : ''}${digits}`;
}

function safeEmailHref(value: string | null): string | null {
  if (!value || value.length > 254 || /[\r\n]/.test(value)) return null;
  const email = value.trim();
  if (!/^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i.test(email)) return null;
  return `mailto:${email}`;
}

function searchableText(item: PublicService) {
  return [
    item.title,
    item.summary,
    item.placeName,
    item.municipalityName,
    item.address,
    ...item.services,
  ].filter(Boolean).join(' ').toLocaleLowerCase('es');
}

function ServiceCard({ item }: { item: PublicService }) {
  const image = safeMediaUrl(item.mediaUrl);
  return <article className={styles.card}>
    {image
      ? <img className={styles.cardImage} src={image} alt="" loading="lazy" />
      : <div className={styles.cardPlaceholder} aria-hidden="true">🛠️</div>}
    <div className={styles.cardBody}>
      <div className={styles.metaRow}>
        <span>{item.featured ? 'Destacado' : 'Servicio local'}</span>
        {item.placeName ? <span>{item.placeName}</span> : null}
      </div>
      <h2>{item.title}</h2>
      {item.summary ? <p>{item.summary}</p> : null}
      {item.services.length ? <ul className={styles.chips} aria-label={`Servicios de ${item.title}`}>
        {item.services.slice(0, 4).map((service) => <li key={service}>{service}</li>)}
      </ul> : null}
      <div className={styles.locationLine}>{[item.municipalityName, item.address].filter(Boolean).join(' · ') || 'Ubicación pendiente'}</div>
      <Link className={styles.primaryLink} href={`/servicios?slug=${encodeURIComponent(item.slug)}`}>Ver servicio →</Link>
    </div>
  </article>;
}

function ServiceDetail({ item }: { item: PublicService }) {
  const image = safeMediaUrl(item.mediaUrl);
  const externalUrl = safeExternalUrl(item.externalUrl);
  const instagram = safeExternalUrl(item.instagram);
  const facebook = safeExternalUrl(item.facebook);
  const phoneHref = safePhoneHref(item.phone);
  const emailHref = safeEmailHref(item.email);

  return <>
    <Link className={styles.backLink} href="/servicios">← Servicios de Sierra Mágina</Link>
    <article className={styles.detail}>
      {image
        ? <img className={styles.heroImage} src={image} alt="" />
        : <div className={styles.heroPlaceholder} aria-hidden="true">🛠️</div>}
      <div className={styles.detailBody}>
        <div className={styles.metaRow}><span>Empresa / servicio local</span>{item.featured ? <span>Destacado</span> : null}</div>
        <h1>{item.title}</h1>
        {item.summary ? <p className={styles.lead}>{item.summary}</p> : null}
        {item.body ? <p className={styles.bodyText}>{item.body}</p> : null}

        {item.services.length ? <section className={styles.detailSection} aria-labelledby="service-list-title">
          <h2 id="service-list-title">Servicios</h2>
          <ul className={styles.chips}>{item.services.map((service) => <li key={service}>{service}</li>)}</ul>
        </section> : null}

        <dl className={styles.infoGrid}>
          {item.placeName ? <div><dt>Pueblo / localidad</dt><dd>{item.placeName}</dd></div> : null}
          {item.municipalityName ? <div><dt>Municipio</dt><dd>{item.municipalityName}</dd></div> : null}
          {item.address ? <div><dt>Dirección</dt><dd>{item.address}</dd></div> : null}
          {item.openingHours ? <div><dt>Horario</dt><dd className={styles.preLine}>{item.openingHours}</dd></div> : null}
        </dl>

        {item.campaignNotes ? <section className={styles.noteBox}><h2>Información de campaña</h2><p className={styles.preLine}>{item.campaignNotes}</p></section> : null}

        {phoneHref || emailHref || externalUrl || instagram || facebook ? <section className={styles.contactBlock} aria-labelledby="service-contact-title">
          <h2 id="service-contact-title">Contacto</h2>
          <div className={styles.actions}>
            {phoneHref ? <a className={styles.secondaryLink} href={phoneHref}>Llamar · {item.phone}</a> : null}
            {emailHref ? <a className={styles.secondaryLink} href={emailHref}>Email · {item.email}</a> : null}
            {externalUrl ? <a className={styles.primaryLink} href={externalUrl} target="_blank" rel="noopener noreferrer">{item.ctaLabel || 'Más información'} ↗</a> : null}
            {instagram ? <a className={styles.secondaryLink} href={instagram} target="_blank" rel="noopener noreferrer">Instagram ↗</a> : null}
            {facebook ? <a className={styles.secondaryLink} href={facebook} target="_blank" rel="noopener noreferrer">Facebook ↗</a> : null}
          </div>
        </section> : null}
      </div>
    </article>
  </>;
}

export function PublicServicesPage() {
  const params = useSearchParams();
  const slug = params.get('slug')?.trim() || null;
  const [items, setItems] = useState<PublicService[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    loadPublicServices()
      .then((rows) => { if (!cancelled) setItems(rows); })
      .catch((cause) => {
        console.error('Unable to load public services', cause);
        if (!cancelled) {
          setItems([]);
          setError(true);
        }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const selected = useMemo(
    () => slug ? items.find((item) => item.slug === slug) ?? null : null,
    [items, slug],
  );

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es');
    return needle ? items.filter((item) => searchableText(item).includes(needle)) : items;
  }, [items, query]);

  if (loading) return <main className={styles.page}>
    <section className={styles.stateCard} aria-live="polite"><strong>Cargando servicios locales…</strong><p>Consultando el directorio público mantenido desde Mágina Olivo.</p></section>
  </main>;

  if (error) return <main className={styles.page}>
    <header className={styles.header}><span>SIERRA MÁGINA</span><h1>Servicios locales</h1></header>
    <section className={styles.stateCard} role="alert"><strong>El directorio no está disponible ahora</strong><p>No mostramos empresas o contactos inventados. Vuelve a intentarlo cuando el servicio público esté disponible.</p><button type="button" onClick={() => window.location.reload()}>Reintentar</button></section>
  </main>;

  if (slug && !selected) return <main className={styles.page}>
    <header className={styles.header}><span>SIERRA MÁGINA</span><h1>Servicios locales</h1></header>
    <section className={styles.stateCard}><strong>No encontramos este servicio</strong><p>La ficha puede haber sido retirada o el enlace haber cambiado.</p><Link href="/servicios">Volver al directorio</Link></section>
  </main>;

  if (selected) return <main className={styles.page}><ServiceDetail item={selected} /></main>;

  return <main className={styles.page}>
    <header className={styles.header}>
      <span>SIERRA MÁGINA · DIRECTORIO LOCAL</span>
      <h1>Servicios locales</h1>
      <p>Empresas y profesionales publicados desde Mágina Olivo. Consulta qué ofrecen y contacta solo a través de los datos que cada ficha tenga publicados.</p>
    </header>

    <section className={styles.toolbar} aria-label="Buscar servicios locales">
      <label htmlFor="service-search">Buscar empresa, servicio, pueblo o municipio</label>
      <div className={styles.searchRow}>
        <input id="service-search" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Ej. maquinaria, Bedmar, reparación…" />
        {query ? <button type="button" onClick={() => setQuery('')}>Limpiar</button> : null}
      </div>
      <small>{filtered.length} {filtered.length === 1 ? 'resultado' : 'resultados'}</small>
    </section>

    {!items.length ? <section className={styles.stateCard}><strong>Todavía no hay servicios publicados</strong><p>Las empresas y profesionales aparecerán aquí cuando sus fichas se publiquen desde Administración.</p></section> : null}
    {items.length > 0 && !filtered.length ? <section className={styles.stateCard}><strong>Sin coincidencias</strong><p>Prueba con otro servicio, empresa o localidad.</p><button type="button" onClick={() => setQuery('')}>Ver todos</button></section> : null}

    <section className={styles.grid} aria-label="Servicios locales publicados">
      {filtered.map((item) => <ServiceCard key={item.id} item={item} />)}
    </section>
  </main>;
}
