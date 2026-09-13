'use client';

import Link from 'next/link';
import type { PublicRouteDetail, PublicRouteNearbyBusiness } from '../../../lib/public-routes-source';
import styles from '../routes-public.module.css';

function distanceLabel(business: PublicRouteNearbyBusiness) {
  if (business.distanceBasis === 'spatial' && business.distanceMeters !== null) {
    if (business.distanceMeters < 1_000) return `${Math.round(business.distanceMeters)} m del trazado`;
    return `${(business.distanceMeters / 1_000).toFixed(1)} km del trazado`;
  }
  if (business.distanceBasis === 'same_place') {
    return business.territory.placeName ? `En ${business.territory.placeName}` : 'En la misma localidad';
  }
  return business.territory.municipalityName ? `En ${business.territory.municipalityName}` : 'En el mismo municipio';
}

function categorySummary(business: PublicRouteNearbyBusiness) {
  return business.categories.slice(0, 3).map((category) => category.name).join(' · ');
}

function BusinessCard({ business, sponsored = false }: { business: PublicRouteNearbyBusiness; sponsored?: boolean }) {
  const phone = business.contact.phone?.trim() || null;
  const whatsappDigits = business.contact.whatsapp?.replace(/\D/g, '') || '';
  const website = business.contact.website?.trim() || null;

  return <article className={sponsored ? styles.sponsorCard : styles.reviewCard}>
    <div className={styles.reviewMeta}>
      <span>{sponsored ? 'Patrocinado' : business.placement.label ?? (business.verified ? 'Ficha verificada' : 'Servicio local')}</span>
      <span>{distanceLabel(business)}</span>
    </div>
    <h3>{business.name}</h3>
    {categorySummary(business) ? <small>{categorySummary(business)}</small> : null}
    {business.shortDescription ? <p>{business.shortDescription}</p> : null}
    <div className={styles.actionRow}>
      <Link className={styles.primaryAction} href={`/empresas?slug=${encodeURIComponent(business.slug)}`}>Ver ficha</Link>
      {phone ? <a href={`tel:${phone}`}>Llamar</a> : null}
      {whatsappDigits ? <a href={`https://wa.me/${whatsappDigits}`} target="_blank" rel="noreferrer">WhatsApp</a> : null}
      {website ? <a href={website} target="_blank" rel="noreferrer">Web</a> : null}
    </div>
  </article>;
}

export function RouteNearbyBusinesses({ detail }: { detail: PublicRouteDetail }) {
  const organic = detail.nearbyBusinesses.organic;
  const sponsored = detail.nearbyBusinesses.sponsored;
  const municipality = detail.route.municipality_name;

  if (!organic.length && !sponsored.length) {
    return <section className={styles.communitySection} aria-labelledby="nearby-businesses-title">
      <div className={styles.communityHeader}>
        <div><span className={styles.eyebrow}>TERRITORIO</span><h2 id="nearby-businesses-title">Servicios cerca de la ruta</h2></div>
        <Link href="/explorar/empresas">Explorar directorio →</Link>
      </div>
      <p>No hay empresas publicadas que podamos relacionar con este trazado o con su territorio sin inventar cercanía.</p>
      {municipality ? <p><Link href="/explorar">Explorar {municipality} y el territorio →</Link></p> : null}
    </section>;
  }

  return <section className={styles.communitySection} aria-labelledby="nearby-businesses-title">
    <div className={styles.communityHeader}>
      <div>
        <span className={styles.eyebrow}>RUTA × TERRITORIO × ECONOMÍA LOCAL</span>
        <h2 id="nearby-businesses-title">Servicios cerca de la ruta</h2>
        <p>Restauración, alojamientos, AOVE, turismo activo y otros servicios publicados en Mágina Olivo. Las distancias solo aparecen cuando existe una ubicación geográfica real.</p>
      </div>
      <div className={styles.actionRow}>
        <Link href="/explorar/empresas">Ver todas las empresas</Link>
        {municipality ? <Link href="/explorar">Explorar {municipality}</Link> : null}
      </div>
    </div>

    {organic.length ? <div className={styles.reviewGrid} aria-label="Servicios relacionados con la ruta">
      {organic.map((business) => <BusinessCard key={business.id} business={business} />)}
    </div> : null}

    {sponsored.length ? <div className={styles.sponsorArea} aria-label="Servicios patrocinados relacionados con la ruta">
      {sponsored.map((business) => <BusinessCard key={business.id} business={business} sponsored />)}
    </div> : null}

    <small>{detail.nearbyBusinesses.distanceNote}</small>
    {sponsored.length ? <small>{detail.nearbyBusinesses.disclosure}</small> : null}
  </section>;
}
