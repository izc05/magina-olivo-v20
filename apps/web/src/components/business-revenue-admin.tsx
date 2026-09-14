'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import {
  businessRevenueAdminApi,
  type BusinessLeadAdmin,
  type BusinessRevenueDashboard,
} from '@/lib/business-revenue-admin-source';
import styles from './business-admin.module.css';

function slugify(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function money(cents: number, currency = 'EUR') {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency }).format(cents / 100);
}

function leadLabel(kind: BusinessLeadAdmin['kind']) {
  const labels: Record<BusinessLeadAdmin['kind'], string> = {
    contact: 'Información',
    quote: 'Presupuesto',
    booking: 'Reserva',
    availability: 'Disponibilidad',
    order: 'Pedido / compra',
  };
  return labels[kind];
}

export function BusinessRevenueAdmin() {
  const auth = useAuth();
  const [dashboard, setDashboard] = useState<BusinessRevenueDashboard | null>(null);
  const [days, setDays] = useState(30);
  const [selectedBusiness, setSelectedBusiness] = useState('');
  const [offerBusinessId, setOfferBusinessId] = useState('');
  const [title, setTitle] = useState('');
  const [slug, setSlug] = useState('');
  const [summary, setSummary] = useState('');
  const [offerType, setOfferType] = useState<'promotion' | 'discount' | 'fixed_price' | 'bundle' | 'gift' | 'experience' | 'seasonal'>('promotion');
  const [originalPrice, setOriginalPrice] = useState('');
  const [offerPrice, setOfferPrice] = useState('');
  const [promoCode, setPromoCode] = useState('');
  const [redemptionMode, setRedemptionMode] = useState<'contact' | 'request' | 'external_link' | 'show_code'>('request');
  const [redemptionUrl, setRedemptionUrl] = useState('');
  const [validUntil, setValidUntil] = useState('');
  const [offerStatus, setOfferStatus] = useState<'draft' | 'published'>('draft');
  const [leadValues, setLeadValues] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [denied, setDenied] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const next = await businessRevenueAdminApi.dashboard({ days });
    setDashboard(next);
    setOfferBusinessId((current) => current || next.businesses[0]?.id || '');
  }, [days]);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    setDenied(false);
    setError(null);
    void load().catch((caught: unknown) => {
      const status = typeof caught === 'object' && caught && 'status' in caught ? Number((caught as { status?: unknown }).status) : 0;
      if (status === 403) {
        setDenied(true);
        return;
      }
      setError('No se ha podido cargar el rendimiento comercial.');
    });
  }, [auth.status, load]);

  const businesses = dashboard?.businesses ?? [];
  const visibleMetrics = selectedBusiness ? businesses.filter((item) => item.id === selectedBusiness) : businesses;
  const visibleLeads = (dashboard?.leads ?? []).filter((lead) => !selectedBusiness || lead.business_id === selectedBusiness);
  const visibleOffers = (dashboard?.offers ?? []).filter((offer) => !selectedBusiness || offer.business_id === selectedBusiness);

  const totals = useMemo(() => visibleMetrics.reduce((acc, row) => ({
    profileViews: acc.profileViews + row.profileViews,
    contactClicks: acc.contactClicks + row.contactClicks,
    leads: acc.leads + row.leads,
    wonLeads: acc.wonLeads + row.wonLeads,
    wonValueCents: acc.wonValueCents + row.wonValueCents,
    activeOffers: acc.activeOffers + row.activeOffers,
  }), { profileViews: 0, contactClicks: 0, leads: 0, wonLeads: 0, wonValueCents: 0, activeOffers: 0 }), [visibleMetrics]);

  async function run(task: () => Promise<unknown>, success: string) {
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      await task();
      await load();
      setMessage(success);
    } catch (caught) {
      console.error(caught);
      setError('No se ha podido guardar el cambio. Revisa los datos e inténtalo de nuevo.');
    } finally {
      setBusy(false);
    }
  }

  async function createOffer() {
    if (!offerBusinessId || !title.trim()) {
      setError('Selecciona una empresa e indica el título de la oferta.');
      return;
    }
    const parsedOriginal = originalPrice.trim() ? Math.round(Number(originalPrice.replace(',', '.')) * 100) : null;
    const parsedOffer = offerPrice.trim() ? Math.round(Number(offerPrice.replace(',', '.')) * 100) : null;
    if ((parsedOriginal !== null && !Number.isFinite(parsedOriginal)) || (parsedOffer !== null && !Number.isFinite(parsedOffer))) {
      setError('Los precios deben ser números válidos.');
      return;
    }
    await run(async () => {
      await businessRevenueAdminApi.createOffer(offerBusinessId, {
        slug: slugify(slug || title),
        title: title.trim(),
        summary: summary.trim() || null,
        offerType,
        originalPriceCents: parsedOriginal,
        offerPriceCents: parsedOffer,
        promoCode: promoCode.trim() || null,
        redemptionMode,
        redemptionUrl: redemptionMode === 'external_link' ? (redemptionUrl.trim() || null) : null,
        validUntil: validUntil ? new Date(validUntil).toISOString() : null,
        status: offerStatus,
      });
      setTitle('');
      setSlug('');
      setSummary('');
      setOriginalPrice('');
      setOfferPrice('');
      setPromoCode('');
      setRedemptionUrl('');
      setValidUntil('');
      setOfferStatus('draft');
    }, 'Oferta creada.');
  }

  async function updateLead(id: string, status: BusinessLeadAdmin['status']) {
    const value = leadValues[id]?.trim();
    const actualValueCents = status === 'won' && value
      ? Math.round(Number(value.replace(',', '.')) * 100)
      : undefined;
    if (status === 'won' && value && !Number.isFinite(actualValueCents)) {
      setError('El valor de la oportunidad debe ser un número válido.');
      return;
    }
    await run(() => businessRevenueAdminApi.updateLead(id, { status, actualValueCents }), status === 'won' ? 'Lead marcado como ganado.' : 'Lead actualizado.');
  }

  if (auth.status === 'loading') return <main className={styles.login}><div><strong>Comprobando acceso corporativo…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className={styles.login}><div><p className={styles.eyebrow}>Mágina Olivo · Empresas</p><h1>Acceso corporativo</h1><p>Inicia sesión para consultar rendimiento, leads y ofertas.</p><GoogleSignInButton /></div></main>;
  if (denied) return <main className={styles.login}><div><p className={styles.eyebrow}>Acceso restringido</p><h1>Sin permisos de plataforma</h1><p>Esta cuenta no puede consultar el rendimiento comercial.</p></div></main>;
  if (!dashboard) return <main className={styles.login}><div><strong>Cargando rendimiento…</strong>{error ? <p>{error}</p> : null}</div></main>;

  return <main className={styles.shell}>
    <header className={styles.header}>
      <div><a href="/admin/empresas">← Empresas</a><p className={styles.eyebrow}>MOTOR COMERCIAL</p><h1>Rendimiento, leads y ofertas</h1><p>Convierte la visibilidad del directorio en acciones y valor medible.</p></div>
      <div className={styles.actions}><a className={styles.secondaryButton} href="/explorar/empresas" target="_blank">Ver directorio ↗</a><button className={styles.secondaryButton} disabled={busy} onClick={() => void load()}>Actualizar</button></div>
    </header>

    {message ? <div className={styles.success}>{message}</div> : null}
    {error ? <div className={styles.error}>{error}</div> : null}

    <section className={styles.panel}>
      <div className={styles.formGrid}>
        <label>Empresa<select value={selectedBusiness} onChange={(event) => { setSelectedBusiness(event.target.value); if (event.target.value) setOfferBusinessId(event.target.value); }}><option value="">Todas</option>{businesses.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label>Periodo<select value={days} onChange={(event) => setDays(Number(event.target.value))}><option value={7}>7 días</option><option value={30}>30 días</option><option value={90}>90 días</option><option value={365}>365 días</option></select></label>
      </div>
    </section>

    <section className={styles.stats}>
      <article><strong>{totals.profileViews}</strong><span>Visitas de ficha</span></article>
      <article><strong>{totals.contactClicks}</strong><span>Clics de contacto</span></article>
      <article><strong>{totals.leads}</strong><span>Solicitudes</span></article>
      <article><strong>{totals.wonLeads}</strong><span>Ganadas</span></article>
      <article><strong>{money(totals.wonValueCents)}</strong><span>Valor ganado</span></article>
      <article><strong>{totals.activeOffers}</strong><span>Ofertas activas</span></article>
    </section>

    <section className={styles.panel}>
      <div className={styles.panelTitle}><div><h2>Rendimiento por empresa</h2><p>Datos del periodo seleccionado. Las promociones pagadas siguen identificándose públicamente.</p></div></div>
      <div className={styles.businessList}>{visibleMetrics.map((item) => <a key={item.id} className={styles.businessRow} href={`/empresas?slug=${encodeURIComponent(item.slug)}`} target="_blank">
        <span><strong>{item.name}</strong><small>{item.commercialPlan} · {item.activeOffers} ofertas activas</small></span>
        <span className={styles.rowBadges}><i>{item.profileViews} vistas</i><i>{item.contactClicks} clics</i><i>{item.leads} leads</i><b>{money(item.wonValueCents)}</b></span>
      </a>)}</div>
    </section>

    <section className={styles.layout}>
      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>Leads y oportunidades</h2><p>Marca el avance para poder medir la conversión real del directorio.</p></div></div>
        <div className={styles.claimList}>{visibleLeads.length ? visibleLeads.map((lead) => <article key={lead.id} className={styles.claim}>
          <div><strong>{lead.business_name} · {leadLabel(lead.kind)}</strong><p>{lead.contact_name}{lead.contact_email ? ` · ${lead.contact_email}` : ''}{lead.contact_phone ? ` · ${lead.contact_phone}` : ''}</p>{lead.offer_title ? <small>Oferta: {lead.offer_title}</small> : null}{lead.message ? <p>{lead.message}</p> : null}<small>{new Date(lead.created_at).toLocaleString('es-ES')} · {lead.status}</small></div>
          <div className={styles.actions}>
            <input aria-label="Valor ganado en euros" inputMode="decimal" placeholder="Valor €" value={leadValues[lead.id] ?? ''} onChange={(event) => setLeadValues((current) => ({ ...current, [lead.id]: event.target.value }))} />
            <button className={styles.secondaryButton} disabled={busy} onClick={() => void updateLead(lead.id, 'contacted')}>Contactado</button>
            <button className={styles.button} disabled={busy} onClick={() => void updateLead(lead.id, 'won')}>Ganado</button>
            <button className={styles.dangerButton} disabled={busy} onClick={() => void updateLead(lead.id, 'lost')}>Perdido</button>
          </div>
        </article>) : <p>No hay solicitudes en este periodo.</p>}</div>
      </article>

      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>Nueva oferta</h2><p>Promoción, paquete o experiencia visible en la ficha pública.</p></div></div>
        <div className={styles.formGrid}>
          <label>Empresa<select value={offerBusinessId} onChange={(event) => setOfferBusinessId(event.target.value)}><option value="">Selecciona</option>{businesses.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label>Título<input value={title} onChange={(event) => { setTitle(event.target.value); if (!slug) setSlug(slugify(event.target.value)); }} /></label>
          <label>Slug<input value={slug} onChange={(event) => setSlug(slugify(event.target.value))} /></label>
          <label>Tipo<select value={offerType} onChange={(event) => setOfferType(event.target.value as typeof offerType)}><option value="promotion">Promoción</option><option value="discount">Descuento</option><option value="fixed_price">Precio especial</option><option value="bundle">Pack</option><option value="gift">Regalo</option><option value="experience">Experiencia</option><option value="seasonal">Temporada</option></select></label>
          <label>Precio anterior €<input inputMode="decimal" value={originalPrice} onChange={(event) => setOriginalPrice(event.target.value)} /></label>
          <label>Precio oferta €<input inputMode="decimal" value={offerPrice} onChange={(event) => setOfferPrice(event.target.value)} /></label>
          <label>Código promocional<input value={promoCode} onChange={(event) => setPromoCode(event.target.value)} /></label>
          <label>Canje<select value={redemptionMode} onChange={(event) => setRedemptionMode(event.target.value as typeof redemptionMode)}><option value="request">Solicitud en Mágina</option><option value="contact">Contacto</option><option value="show_code">Mostrar código</option><option value="external_link">Enlace externo</option></select></label>
          {redemptionMode === 'external_link' ? <label>URL de reserva<input type="url" value={redemptionUrl} onChange={(event) => setRedemptionUrl(event.target.value)} /></label> : null}
          <label>Válida hasta<input type="datetime-local" value={validUntil} onChange={(event) => setValidUntil(event.target.value)} /></label>
          <label>Estado<select value={offerStatus} onChange={(event) => setOfferStatus(event.target.value as typeof offerStatus)}><option value="draft">Borrador</option><option value="published">Publicar</option></select></label>
          <label className={styles.wide}>Resumen<input maxLength={400} value={summary} onChange={(event) => setSummary(event.target.value)} /></label>
        </div>
        <div className={styles.actions}><button className={styles.button} disabled={busy} onClick={() => void createOffer()}>{busy ? 'Guardando…' : 'Crear oferta'}</button></div>
      </article>
    </section>

    <section className={styles.panel}>
      <div className={styles.panelTitle}><div><h2>Ofertas existentes</h2><p>Publica o archiva sin borrar el histórico de medición.</p></div></div>
      <div className={styles.businessList}>{visibleOffers.length ? visibleOffers.map((offer) => <div key={offer.id} className={styles.businessRow}>
        <span><strong>{offer.business_name} · {offer.title}</strong><small>{offer.offer_type} · {offer.status}{offer.valid_until ? ` · hasta ${new Date(offer.valid_until).toLocaleDateString('es-ES')}` : ''}</small></span>
        <span className={styles.actions}>{offer.offer_price_cents !== null ? <b>{money(offer.offer_price_cents, offer.currency)}</b> : null}{offer.status !== 'published' ? <button className={styles.button} disabled={busy} onClick={() => void run(() => businessRevenueAdminApi.updateOffer(offer.id, { status: 'published' }), 'Oferta publicada.')}>Publicar</button> : <button className={styles.secondaryButton} disabled={busy} onClick={() => void run(() => businessRevenueAdminApi.updateOffer(offer.id, { status: 'archived' }), 'Oferta archivada.')}>Archivar</button>}</span>
      </div>) : <p>No hay ofertas para esta selección.</p>}</div>
    </section>
  </main>;
}
