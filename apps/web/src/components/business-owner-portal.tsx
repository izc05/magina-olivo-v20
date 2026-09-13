'use client';

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import { businessPortalApi, type MyBusiness, type MyBusinessDashboard } from '@/lib/business-portal-source';
import styles from './business-admin.module.css';

function slugify(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function money(cents: number, currency = 'EUR') {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency }).format(cents / 100);
}

type ProfileForm = {
  shortDescription: string;
  description: string;
  address: string;
  phone: string;
  whatsapp: string;
  email: string;
  website: string;
  logoUrl: string;
  coverImageUrl: string;
};

function profileFromDashboard(dashboard: MyBusinessDashboard): ProfileForm {
  const business = dashboard.business;
  return {
    shortDescription: business.shortDescription ?? '',
    description: business.description ?? '',
    address: business.address ?? '',
    phone: business.phone ?? '',
    whatsapp: business.whatsapp ?? '',
    email: business.email ?? '',
    website: business.website ?? '',
    logoUrl: business.logoUrl ?? '',
    coverImageUrl: business.coverImageUrl ?? '',
  };
}

export function BusinessOwnerPortal() {
  const auth = useAuth();
  const [businesses, setBusinesses] = useState<MyBusiness[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [dashboard, setDashboard] = useState<MyBusinessDashboard | null>(null);
  const [days, setDays] = useState(30);
  const [profile, setProfile] = useState<ProfileForm | null>(null);
  const [offerTitle, setOfferTitle] = useState('');
  const [offerSummary, setOfferSummary] = useState('');
  const [offerPrice, setOfferPrice] = useState('');
  const [offerStatus, setOfferStatus] = useState<'draft' | 'published'>('draft');
  const [leadValues, setLeadValues] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadBusinesses = useCallback(async () => {
    const payload = await businessPortalApi.list();
    setBusinesses(payload.businesses);
    setSelectedId((current) => current || payload.businesses[0]?.id || '');
  }, []);

  const loadDashboard = useCallback(async (id: string, selectedDays = days) => {
    if (!id) {
      setDashboard(null);
      setProfile(null);
      return;
    }
    const next = await businessPortalApi.dashboard(id, selectedDays);
    setDashboard(next);
    setProfile(profileFromDashboard(next));
  }, [days]);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    void loadBusinesses().catch((cause) => {
      console.error(cause);
      setError('No se han podido cargar tus empresas.');
    });
  }, [auth.status, loadBusinesses]);

  useEffect(() => {
    if (!selectedId || auth.status !== 'authenticated') return;
    void loadDashboard(selectedId).catch((cause) => {
      console.error(cause);
      setError('No se ha podido cargar el panel de la empresa.');
    });
  }, [selectedId, days, auth.status, loadDashboard]);

  const selected = useMemo(() => businesses.find((business) => business.id === selectedId) ?? null, [businesses, selectedId]);
  const canEdit = dashboard ? ['owner', 'manager', 'editor'].includes(dashboard.role) : false;
  const canManageLeads = dashboard ? ['owner', 'manager'].includes(dashboard.role) : false;

  async function run(task: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await task();
      if (selectedId) await loadDashboard(selectedId);
      setMessage(success);
    } catch (cause) {
      console.error(cause);
      setError('No se ha podido guardar el cambio. Revisa los datos e inténtalo de nuevo.');
    } finally {
      setBusy(false);
    }
  }

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    if (!profile || !selectedId) return;
    await run(() => businessPortalApi.updateProfile(selectedId, {
      shortDescription: profile.shortDescription.trim() || null,
      description: profile.description.trim() || null,
      address: profile.address.trim() || null,
      phone: profile.phone.trim() || null,
      whatsapp: profile.whatsapp.trim() || null,
      email: profile.email.trim() || null,
      website: profile.website.trim() || null,
      logoUrl: profile.logoUrl.trim() || null,
      coverImageUrl: profile.coverImageUrl.trim() || null,
    }), 'Ficha actualizada.');
  }

  async function createOffer(event: FormEvent) {
    event.preventDefault();
    if (!selectedId || !offerTitle.trim()) return;
    const cents = offerPrice.trim() ? Math.round(Number(offerPrice.replace(',', '.')) * 100) : null;
    if (cents !== null && !Number.isFinite(cents)) {
      setError('El precio debe ser un número válido.');
      return;
    }
    await run(async () => {
      await businessPortalApi.createOffer(selectedId, {
        slug: slugify(offerTitle),
        title: offerTitle.trim(),
        summary: offerSummary.trim() || null,
        offerType: 'promotion',
        offerPriceCents: cents,
        currency: 'EUR',
        redemptionMode: 'request',
        status: offerStatus,
      });
      setOfferTitle('');
      setOfferSummary('');
      setOfferPrice('');
      setOfferStatus('draft');
    }, 'Oferta creada.');
  }

  async function updateLead(leadId: string, status: 'contacted' | 'qualified' | 'won' | 'lost') {
    if (!selectedId) return;
    const rawValue = leadValues[leadId]?.trim();
    const actualValueCents = status === 'won' && rawValue ? Math.round(Number(rawValue.replace(',', '.')) * 100) : undefined;
    if (actualValueCents !== undefined && !Number.isFinite(actualValueCents)) {
      setError('El valor ganado debe ser un número válido.');
      return;
    }
    await run(
      () => businessPortalApi.updateLead(selectedId, leadId, { status, actualValueCents }),
      status === 'won' ? 'Oportunidad marcada como ganada.' : 'Solicitud actualizada.',
    );
  }

  if (auth.status === 'loading') return <main className={styles.login}><div><strong>Comprobando sesión…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className={styles.login}><div><p className={styles.eyebrow}>MÁGINA OLIVO · EMPRESAS</p><h1>Tu negocio en Mágina</h1><p>Inicia sesión para gestionar una empresa que hayas verificado.</p><GoogleSignInButton /></div></main>;

  return <main className={styles.shell}>
    <header className={styles.header}>
      <div><a href="/explorar/empresas">← Directorio</a><p className={styles.eyebrow}>PORTAL DE EMPRESA</p><h1>Mi negocio</h1><p>Gestiona tu presencia, ofertas y oportunidades sin acceso al administrador general.</p></div>
      <div className={styles.actions}>{selected ? <a className={styles.secondaryButton} href={`/empresas?slug=${encodeURIComponent(selected.slug)}`} target="_blank">Ver ficha pública ↗</a> : null}</div>
    </header>

    {message ? <div className={styles.success}>{message}</div> : null}
    {error ? <div className={styles.error}>{error}</div> : null}

    {!businesses.length ? <section className={styles.panel}>
      <p className={styles.eyebrow}>AÚN NO HAY EMPRESAS VINCULADAS</p>
      <h2>Reclama primero tu ficha</h2>
      <p>Busca tu empresa en el directorio y solicita su gestión. Cuando Administración verifique la solicitud, aparecerá aquí automáticamente.</p>
      <a className={styles.button} href="/explorar/empresas">Buscar mi empresa</a>
    </section> : <>
      <section className={styles.panel}>
        <div className={styles.formGrid}>
          <label>Empresa<select value={selectedId} onChange={(event) => setSelectedId(event.target.value)}>{businesses.map((business) => <option key={business.id} value={business.id}>{business.name} · {business.role}</option>)}</select></label>
          <label>Periodo<select value={days} onChange={(event) => setDays(Number(event.target.value))}><option value={7}>7 días</option><option value={30}>30 días</option><option value={90}>90 días</option><option value={365}>365 días</option></select></label>
        </div>
        {dashboard ? <p>Plan <strong>{dashboard.business.commercialPlan}</strong> · {dashboard.business.verificationStatus === 'verified' ? 'Ficha verificada' : 'Pendiente de verificación'} · permiso <strong>{dashboard.role}</strong></p> : null}
      </section>

      {dashboard ? <>
        <section className={styles.stats}>
          <article><strong>{dashboard.metrics.profileViews}</strong><span>Visitas</span></article>
          <article><strong>{dashboard.metrics.contactClicks}</strong><span>Clics contacto</span></article>
          <article><strong>{dashboard.metrics.leads}</strong><span>Solicitudes</span></article>
          <article><strong>{dashboard.metrics.wonLeads}</strong><span>Ganadas</span></article>
          <article><strong>{money(dashboard.metrics.wonValueCents)}</strong><span>Valor ganado</span></article>
        </section>

        <section className={styles.layout}>
          <article className={styles.panel}>
            <div className={styles.panelTitle}><div><h2>Solicitudes recibidas</h2><p>Responde y marca el resultado para conocer el valor que te genera Mágina Olivo.</p></div></div>
            <div className={styles.claimList}>{dashboard.leads.length ? dashboard.leads.map((lead) => <article key={lead.id} className={styles.claim}>
              <div><strong>{lead.contact_name} · {lead.kind}</strong><p>{[lead.contact_email, lead.contact_phone].filter(Boolean).join(' · ')}</p>{lead.offer_title ? <small>Oferta: {lead.offer_title}</small> : null}{lead.message ? <p>{lead.message}</p> : null}<small>{new Date(lead.created_at).toLocaleString('es-ES')} · {lead.status}</small></div>
              {canManageLeads ? <div className={styles.actions}>
                <input aria-label="Valor conseguido en euros" inputMode="decimal" placeholder="Valor €" value={leadValues[lead.id] ?? ''} onChange={(event) => setLeadValues((current) => ({ ...current, [lead.id]: event.target.value }))} />
                <button className={styles.secondaryButton} disabled={busy} onClick={() => void updateLead(lead.id, 'contacted')}>Contactado</button>
                <button className={styles.secondaryButton} disabled={busy} onClick={() => void updateLead(lead.id, 'qualified')}>Interesado</button>
                <button className={styles.button} disabled={busy} onClick={() => void updateLead(lead.id, 'won')}>Ganado</button>
                <button className={styles.dangerButton} disabled={busy} onClick={() => void updateLead(lead.id, 'lost')}>Perdido</button>
              </div> : null}
            </article>) : <p>Aún no has recibido solicitudes en este periodo.</p>}</div>
          </article>

          <article className={styles.panel}>
            <div className={styles.panelTitle}><div><h2>Ofertas</h2><p>Publica promociones o experiencias en tu ficha.</p></div></div>
            <div className={styles.businessList}>{dashboard.offers.map((offer) => <div key={offer.id} className={styles.businessRow}><span><strong>{offer.title}</strong><small>{offer.offer_type} · {offer.status}{offer.valid_until ? ` · hasta ${new Date(offer.valid_until).toLocaleDateString('es-ES')}` : ''}</small></span>{offer.offer_price_cents !== null ? <b>{money(offer.offer_price_cents, offer.currency)}</b> : null}</div>)}</div>
            {canEdit ? <form className={styles.formGrid} onSubmit={createOffer}>
              <label>Título<input required minLength={2} value={offerTitle} onChange={(event) => setOfferTitle(event.target.value)} /></label>
              <label>Precio €<input inputMode="decimal" value={offerPrice} onChange={(event) => setOfferPrice(event.target.value)} /></label>
              <label>Estado<select value={offerStatus} onChange={(event) => setOfferStatus(event.target.value as typeof offerStatus)}><option value="draft">Borrador</option><option value="published">Publicar</option></select></label>
              <label className={styles.wide}>Resumen<input maxLength={400} value={offerSummary} onChange={(event) => setOfferSummary(event.target.value)} /></label>
              <div className={styles.actions}><button className={styles.button} disabled={busy} type="submit">Crear oferta</button></div>
            </form> : null}
          </article>
        </section>

        {profile && canEdit ? <section className={styles.panel}>
          <div className={styles.panelTitle}><div><h2>Tu ficha pública</h2><p>Puedes actualizar contenido y contacto. Verificación, plan comercial y posicionamiento siguen bajo control de Mágina Olivo.</p></div></div>
          <form className={styles.formGrid} onSubmit={saveProfile}>
            <label className={styles.wide}>Resumen<input maxLength={320} value={profile.shortDescription} onChange={(event) => setProfile({ ...profile, shortDescription: event.target.value })} /></label>
            <label className={styles.wide}>Descripción<textarea rows={5} maxLength={8000} value={profile.description} onChange={(event) => setProfile({ ...profile, description: event.target.value })} /></label>
            <label>Dirección<input value={profile.address} onChange={(event) => setProfile({ ...profile, address: event.target.value })} /></label>
            <label>Teléfono<input value={profile.phone} onChange={(event) => setProfile({ ...profile, phone: event.target.value })} /></label>
            <label>WhatsApp<input value={profile.whatsapp} onChange={(event) => setProfile({ ...profile, whatsapp: event.target.value })} /></label>
            <label>Email<input type="email" value={profile.email} onChange={(event) => setProfile({ ...profile, email: event.target.value })} /></label>
            <label>Web<input type="url" value={profile.website} onChange={(event) => setProfile({ ...profile, website: event.target.value })} /></label>
            <label>Logo URL<input type="url" value={profile.logoUrl} onChange={(event) => setProfile({ ...profile, logoUrl: event.target.value })} /></label>
            <label>Portada URL<input type="url" value={profile.coverImageUrl} onChange={(event) => setProfile({ ...profile, coverImageUrl: event.target.value })} /></label>
            <div className={styles.actions}><button className={styles.button} disabled={busy} type="submit">Guardar ficha</button></div>
          </form>
        </section> : null}
      </> : <section className={styles.panel}><strong>Cargando panel…</strong></section>}
    </>}
  </main>;
}
