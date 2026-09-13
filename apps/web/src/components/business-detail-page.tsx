'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import {
  loadBusiness,
  loadBusinessOffers,
  submitBusinessClaim,
  submitBusinessLead,
  trackBusinessEvent,
  type BusinessDetail,
  type BusinessEventType,
  type BusinessOffer,
} from '@/lib/business-directory-source';
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

function recordBusinessEvent(slug: string, eventType: BusinessEventType, offerId?: string) {
  void trackBusinessEvent(slug, {
    eventType,
    offerId,
    sourceContext: 'business_detail',
  }).catch(() => undefined);
}

function formatMoney(cents: number | null, currency: string) {
  if (cents === null) return null;
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency }).format(cents / 100);
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

function Offers({ business, offers, onChoose }: { business: BusinessDetail; offers: BusinessOffer[]; onChoose: (id: string) => void }) {
  if (!offers.length) return null;
  return <section>
    <p className={styles.eyebrow}>OFERTAS Y EXPERIENCIAS</p>
    <h2>Ahora en {business.name}</h2>
    <div className={styles.detailGrid}>
      {offers.map((offer) => {
        const original = formatMoney(offer.pricing.originalPriceCents, offer.pricing.currency);
        const price = formatMoney(offer.pricing.offerPriceCents, offer.pricing.currency);
        const external = safeUrl(offer.redemption.url);
        return <article key={offer.id} className={styles.infoBox}>
          <small>{offer.offerType.replace('_', ' ')}</small>
          <strong>{offer.title}</strong>
          {offer.summary ? <p>{offer.summary}</p> : null}
          {price ? <p><strong>{price}</strong>{original && original !== price ? ` · antes ${original}` : ''}</p> : null}
          {offer.promoCode ? <p>Código: <strong>{offer.promoCode}</strong></p> : null}
          {offer.validUntil ? <small>Disponible hasta {new Date(offer.validUntil).toLocaleDateString('es-ES')}</small> : null}
          <div className={styles.actionRow}>
            {offer.redemption.mode === 'external_link' && external
              ? <a className={styles.secondaryButton} href={external} target="_blank" rel="noopener noreferrer" onClick={() => recordBusinessEvent(business.slug, 'offer_redeem', offer.id)}>Ver / reservar ↗</a>
              : <a className={styles.secondaryButton} href="#business-lead" onClick={() => { recordBusinessEvent(business.slug, 'offer_redeem', offer.id); onChoose(offer.id); }}>Me interesa</a>}
          </div>
          {offer.terms ? <small>{offer.terms}</small> : null}
        </article>;
      })}
    </div>
  </section>;
}

function LeadForm({ business, offers, selectedOfferId, onOfferChange }: {
  business: BusinessDetail;
  offers: BusinessOffer[];
  selectedOfferId: string;
  onOfferChange: (id: string) => void;
}) {
  const [kind, setKind] = useState<'contact' | 'quote' | 'booking' | 'availability' | 'order'>('quote');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [requestedFor, setRequestedFor] = useState('');
  const [partySize, setPartySize] = useState('');
  const [message, setMessage] = useState('');
  const [consent, setConsent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!email.trim() && !phone.trim()) {
      setError('Indica al menos un email o un teléfono para que la empresa pueda responderte.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const result = await submitBusinessLead(business.slug, {
        kind,
        contactName: name,
        contactEmail: email.trim() || undefined,
        contactPhone: phone.trim() || undefined,
        message: message.trim() || undefined,
        requestedFor: requestedFor ? new Date(requestedFor).toISOString() : null,
        partySize: partySize ? Number(partySize) : null,
        offerId: selectedOfferId || undefined,
        sourceContext: 'business_detail',
        sourceKey: selectedOfferId ? 'offer' : 'profile',
        consentBusinessContact: true,
      });
      setSuccess(result.message);
    } catch (cause) {
      console.error('Unable to submit business lead', cause);
      setError('No hemos podido enviar la solicitud. Comprueba los datos o inténtalo de nuevo.');
    } finally {
      setSubmitting(false);
    }
  };

  return <section id="business-lead" className={styles.claimCard}>
    <div>
      <p className={styles.eyebrow}>CONTACTO DESDE MÁGINA OLIVO</p>
      <h2>Solicita información a {business.name}</h2>
      <p>Envía una consulta, pide presupuesto o pregunta por disponibilidad. La empresa recibe tus datos únicamente para responder a esta solicitud.</p>
    </div>
    {success ? <div className={styles.success}>{success}</div> : <form className={styles.claimForm} onSubmit={submit}>
      <div className={styles.claimField}><label htmlFor="lead-kind">¿Qué necesitas?</label><select id="lead-kind" value={kind} onChange={(event) => setKind(event.target.value as typeof kind)}><option value="quote">Pedir presupuesto</option><option value="availability">Consultar disponibilidad</option><option value="booking">Solicitar reserva</option><option value="order">Consultar pedido / compra</option><option value="contact">Información general</option></select></div>
      {offers.length ? <div className={styles.claimField}><label htmlFor="lead-offer">Oferta relacionada</label><select id="lead-offer" value={selectedOfferId} onChange={(event) => onOfferChange(event.target.value)}><option value="">Ninguna en concreto</option>{offers.map((offer) => <option key={offer.id} value={offer.id}>{offer.title}</option>)}</select></div> : null}
      <div className={styles.claimField}><label htmlFor="lead-name">Nombre</label><input id="lead-name" required minLength={2} maxLength={140} value={name} onChange={(event) => setName(event.target.value)} /></div>
      <div className={styles.claimField}><label htmlFor="lead-email">Email</label><input id="lead-email" type="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} /></div>
      <div className={styles.claimField}><label htmlFor="lead-phone">Teléfono</label><input id="lead-phone" inputMode="tel" maxLength={40} value={phone} onChange={(event) => setPhone(event.target.value)} /></div>
      <div className={styles.claimField}><label htmlFor="lead-date">Fecha / momento deseado</label><input id="lead-date" type="datetime-local" value={requestedFor} onChange={(event) => setRequestedFor(event.target.value)} /></div>
      <div className={styles.claimField}><label htmlFor="lead-party">Personas / unidades</label><input id="lead-party" type="number" min="1" max="500" value={partySize} onChange={(event) => setPartySize(event.target.value)} /></div>
      <div className={`${styles.claimField} ${styles.claimWide}`}><label htmlFor="lead-message">Mensaje</label><textarea id="lead-message" maxLength={2500} value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Cuéntale a la empresa qué necesitas…" /></div>
      <label className={`${styles.claimField} ${styles.claimWide}`}><span><input type="checkbox" required checked={consent} onChange={(event) => setConsent(event.target.checked)} /> Autorizo a compartir estos datos con {business.name} para que responda a mi solicitud.</span></label>
      {error ? <div className={`${styles.error} ${styles.claimWide}`} role="alert">{error}</div> : null}
      <div className={styles.claimWide}><button className={styles.button} type="submit" disabled={submitting || !consent}>{submitting ? 'Enviando…' : 'Enviar solicitud'}</button></div>
    </form>}
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
  const [offers, setOffers] = useState<BusinessOffer[]>([]);
  const [selectedOfferId, setSelectedOfferId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<'missing' | 'unavailable' | null>(null);
  const viewedBusinessesRef = useRef(new Set<string>());
  const viewedOffersRef = useRef(new Set<string>());

  useEffect(() => {
    if (!slug) {
      setLoading(false);
      setError('missing');
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([
      loadBusiness(slug),
      loadBusinessOffers(slug).catch(() => [] as BusinessOffer[]),
    ]).then(([value, activeOffers]) => {
      if (!cancelled) {
        setBusiness(value);
        setOffers(activeOffers);
      }
    }).catch((cause) => {
      console.error('Unable to load business', cause);
      if (!cancelled) {
        setBusiness(null);
        setOffers([]);
        setError('unavailable');
      }
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [slug]);

  useEffect(() => {
    if (!business || viewedBusinessesRef.current.has(business.slug)) return;
    viewedBusinessesRef.current.add(business.slug);
    recordBusinessEvent(business.slug, 'profile_view');
  }, [business]);

  useEffect(() => {
    if (!business || !offers.length) return;
    for (const offer of offers) {
      if (viewedOffersRef.current.has(offer.id)) continue;
      viewedOffersRef.current.add(offer.id);
      recordBusinessEvent(business.slug, 'offer_view', offer.id);
    }
  }, [business, offers]);

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
  const directions = business.location
    ? `https://www.google.com/maps/dir/?api=1&destination=${business.location.latitude},${business.location.longitude}`
    : null;

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

        {phone || whatsapp || email || website || directions ? <section>
          <h2>Contacto</h2>
          <div className={styles.actionRow}>
            {phone ? <a className={styles.button} href={phone} onClick={() => recordBusinessEvent(business.slug, 'phone_click')}>Llamar</a> : null}
            {whatsapp ? <a className={styles.secondaryButton} href={whatsapp} target="_blank" rel="noopener noreferrer" onClick={() => recordBusinessEvent(business.slug, 'whatsapp_click')}>WhatsApp ↗</a> : null}
            {email ? <a className={styles.secondaryButton} href={email} onClick={() => recordBusinessEvent(business.slug, 'email_click')}>Email</a> : null}
            {website ? <a className={styles.secondaryButton} href={website} target="_blank" rel="noopener noreferrer" onClick={() => recordBusinessEvent(business.slug, 'website_click')}>Web ↗</a> : null}
            {directions ? <a className={styles.secondaryButton} href={directions} target="_blank" rel="noopener noreferrer" onClick={() => recordBusinessEvent(business.slug, 'directions_click')}>Cómo llegar ↗</a> : null}
            <a className={styles.secondaryButton} href="#business-lead">Pedir información</a>
          </div>
        </section> : null}

        <Offers business={business} offers={offers} onChoose={setSelectedOfferId} />
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
    <LeadForm business={business} offers={offers} selectedOfferId={selectedOfferId} onOfferChange={setSelectedOfferId} />
    <ClaimForm business={business} />
  </main>;
}
