'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { loadBusiness, submitBusinessClaim, type BusinessDetail } from '@/lib/business-directory-source';
import { BusinessDirectoryMap } from './business-directory-map';
import styles from './business-directory.module.css';

function safeUrl(value: string | null) {
  if (!value) return null;
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.toString() : null;
  } catch {
    return null;
  }
}

function phoneHref(value: string | null) {
  if (!value) return null;
  const digits = value.replace(/\D/g, '');
  if (digits.length < 6 || digits.length > 15) return null;
  return `tel:${value.trim().startsWith('+') ? '+' : ''}${digits}`;
}

function whatsappHref(value: string | null) {
  if (!value) return null;
  const digits = value.replace(/\D/g, '');
  if (digits.length < 8 || digits.length > 15) return null;
  return `https://wa.me/${digits}`;
}

function emailHref(value: string | null) {
  if (!value || value.length > 254 || /[\r\n]/.test(value)) return null;
  return `mailto:${value}`;
}

function openingHoursText(value: Record<string, unknown>) {
  const rows = Object.entries(value)
    .map(([day, hours]) => [day, typeof hours === 'string' ? hours : null] as const)
    .filter((row): row is readonly [string, string] => Boolean(row[1]));
  return rows.length ? rows.map(([day, hours]) => `${day}: ${hours}`).join(' · ') : null;
}

function Gallery({ business }: { business: BusinessDetail }) {
  const media = business.media.filter((item) => ['photo', 'cover', 'logo'].includes(item.kind) && safeUrl(item.url));
  if (!media.length) return null;
  return <section>
    <h2>Galería</h2>
    <div className={styles.gallery}>
      {media.map((item) => <figure key={item.id}>
        <img src={safeUrl(item.url) ?? ''} alt={item.altText ?? ''} loading="lazy" />
        {item.credit || item.aiGenerated ? <figcaption>
          {[item.credit, item.aiGenerated ? (item.aiDisclosure || 'Imagen generada con IA') : null].filter(Boolean).join(' · ')}
        </figcaption> : null}
      </figure>)}
    </div>
  </section>;
}

function ClaimForm({ business }: { business: BusinessDetail }) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [relationship, setRelationship] = useState('');
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const result = await submitBusinessClaim(business.slug, {
        claimantName: name,
        claimantEmail: email,
        claimantPhone: phone || undefined,
        relationship,
        message: message || undefined,
      });
      setSuccess(result.message);
    } catch (cause) {
      console.error('Unable to submit business claim', cause);
      setError('No hemos podido enviar la solicitud. Comprueba los datos o inténtalo de nuevo.');
    } finally {
      setSubmitting(false);
    }
  };

  return <section className={styles.claimCard}>
    <div>
      <p className={styles.eyebrow}>GESTIÓN DE LA FICHA</p>
      <h2>¿Es tu empresa?</h2>
      <p>Puedes solicitar la gestión de esta ficha. La verificación se realiza manualmente y no cambia hasta que Administración compruebe la solicitud.</p>
    </div>
    {success ? <div className={styles.success}>{success}</div> : <form className={styles.claimForm} onSubmit={submit}>
      <div className={styles.claimField}><label htmlFor="claim-name">Nombre</label><input id="claim-name" required minLength={2} maxLength={120} value={name} onChange={(event) => setName(event.target.value)} /></div>
      <div className={styles.claimField}><label htmlFor="claim-email">Email</label><input id="claim-email" required type="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} /></div>
      <div className={styles.claimField}><label htmlFor="claim-phone">Teléfono</label><input id="claim-phone" inputMode="tel" maxLength={40} value={phone} onChange={(event) => setPhone(event.target.value)} /></div>
      <div className={styles.claimField}><label htmlFor="claim-relationship">Relación con la empresa</label><input id="claim-relationship" required minLength={2} maxLength={120} placeholder="Propietario, gerente, responsable…" value={relationship} onChange={(event) => setRelationship(event.target.value)} /></div>
      <div className={`${styles.claimField} ${styles.claimWide}`}><label htmlFor="claim-message">Información para verificar</label><textarea id="claim-message" maxLength={1500} value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Indica cualquier dato que ayude a verificar que puedes gestionar esta ficha." /></div>
      {error ? <div className={`${styles.error} ${styles.claimWide}`} role="alert">{error}</div> : null}
      <div className={styles.claimWide}><button className={styles.button} type="submit" disabled={submitting}>{submitting ? 'Enviando…' : 'Solicitar gestión de la ficha'}</button></div>
    </form>}
  </section>;
}

export function BusinessDetailPage() {
  const params = useSearchParams();
  const slug = params.get('slug')?.trim() ?? '';
  const [business, setBusiness] = useState<BusinessDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<'missing' | 'unavailable' | null>(null);

  useEffect(() => {
    if (!slug) {
      setLoading(false);
      setError('missing');
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    loadBusiness(slug).then((value) => {
      if (!cancelled) setBusiness(value);
    }).catch((cause) => {
      console.error('Unable to load business', cause);
      if (!cancelled) {
        setBusiness(null);
        setError('unavailable');
      }
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [slug]);

  const openingHours = useMemo(() => business ? openingHoursText(business.openingHours) : null, [business]);

  if (loading) return <main className={styles.page}><section className={styles.state}><strong>Cargando ficha…</strong><p>Consultando la información publicada de la empresa.</p></section></main>;
  if (!business || error) return <main className={styles.page}>
    <Link className={styles.backLink} href="/explorar/empresas">← Volver a empresas</Link>
    <section className={styles.state}><strong>{error === 'missing' ? 'Falta indicar la empresa.' : 'No podemos mostrar esta ficha.'}</strong><p>La ficha puede no estar publicada, haber cambiado o el directorio estar temporalmente no disponible.</p></section>
  </main>;

  const cover = safeUrl(business.coverImageUrl ?? business.logoUrl);
  const website = safeUrl(business.contact.website);
  const phone = phoneHref(business.contact.phone);
  const whatsapp = whatsappHref(business.contact.whatsapp);
  const email = emailHref(business.contact.email);
  const territory = business.territory.placeName ?? business.territory.municipalityName ?? 'Sierra Mágina';

  return <main className={styles.page}>
    <Link className={styles.backLink} href="/explorar/empresas">← Empresas de Sierra Mágina</Link>
    <article className={styles.detail}>
      <div className={styles.detailCoverWrap}>{cover ? <img className={styles.detailCover} src={cover} alt="" /> : <div className={styles.detailCoverPlaceholder} aria-hidden="true">◉</div>}</div>
      <div className={styles.detailBody}>
        <div>
          <p className={styles.eyebrow}>{territory}{business.placement.label ? ` · ${business.placement.label}` : ''}</p>
          <div className={styles.detailTitleRow}><h1>{business.name}</h1>{business.verified ? <span className={styles.verified} title="Ficha verificada">✓</span> : null}</div>
        </div>
        {business.shortDescription ? <p className={styles.detailLead}>{business.shortDescription}</p> : null}
        {business.description ? <p>{business.description}</p> : null}
        {business.placement.label ? <div className={styles.disclosure}>Esta ficha tiene presencia comercial identificada como <strong>{business.placement.label}</strong>. La verificación y la información factual se gestionan por separado del plan comercial.</div> : null}

        {business.categories.length ? <ul className={styles.categoryList}>{business.categories.map((category) => <li key={category.slug}>{category.name}</li>)}</ul> : null}

        <section className={styles.detailGrid} aria-label="Información de la empresa">
          <div className={styles.infoBox}><small>Ubicación</small><strong>{[territory, business.address].filter(Boolean).join(' · ') || 'No publicada'}</strong></div>
          <div className={styles.infoBox}><small>Estado</small><strong>{business.verified ? 'Ficha verificada' : 'Ficha sin verificar'}</strong></div>
          {openingHours ? <div className={styles.infoBox}><small>Horario</small><strong>{openingHours}</strong></div> : null}
          <div className={styles.infoBox}><small>Actualización</small><strong>{new Date(business.updatedAt).toLocaleDateString('es-ES')}</strong></div>
        </section>

        {phone || whatsapp || email || website ? <section>
          <h2>Contacto</h2>
          <div className={styles.actionRow}>
            {phone ? <a className={styles.button} href={phone}>Llamar</a> : null}
            {whatsapp ? <a className={styles.secondaryButton} href={whatsapp} target="_blank" rel="noopener noreferrer">WhatsApp ↗</a> : null}
            {email ? <a className={styles.secondaryButton} href={email}>Email</a> : null}
            {website ? <a className={styles.secondaryButton} href={website} target="_blank" rel="noopener noreferrer">Web ↗</a> : null}
          </div>
        </section> : null}

        <Gallery business={business} />

        {business.location ? <section>
          <h2>En el mapa</h2>
          <BusinessDirectoryMap businesses={[business]} />
        </section> : null}

        {business.sources.length ? <section>
          <h2>Fuentes y actualización</h2>
          <ul className={styles.categoryList}>
            {business.sources.map((source, index) => <li key={`${source.sourceName}-${index}`}>{source.sourceName}{source.syncStatus === 'stale' ? ' · pendiente de actualizar' : ''}</li>)}
          </ul>
        </section> : null}
      </div>
    </article>
    <ClaimForm business={business} />
  </main>;
}
