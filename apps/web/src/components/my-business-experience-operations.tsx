'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import { businessPortalApi, type MyBusiness } from '@/lib/business-portal-source';
import {
  myExperienceApi,
  type ExperienceStatus,
  type ExperienceSlotStatus,
  type MyExperienceCatalog,
} from '@/lib/business-experience-source';
import styles from './business-admin.module.css';

function euros(value: unknown) {
  return typeof value === 'number' ? String(value / 100).replace('.', ',') : '';
}

export function MyBusinessExperienceOperations() {
  const auth = useAuth();
  const [businesses, setBusinesses] = useState<MyBusiness[]>([]);
  const [businessId, setBusinessId] = useState('');
  const [catalog, setCatalog] = useState<MyExperienceCatalog | null>(null);
  const [selectedId, setSelectedId] = useState('');
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [price, setPrice] = useState('');
  const [maxParty, setMaxParty] = useState('');
  const [status, setStatus] = useState<ExperienceStatus>('draft');
  const [slotCapacity, setSlotCapacity] = useState<Record<string,string>>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadBusinesses = useCallback(async () => {
    const payload = await businessPortalApi.list();
    setBusinesses(payload.businesses);
    setBusinessId((current) => current || payload.businesses[0]?.id || '');
  }, []);

  const loadCatalog = useCallback(async (id: string) => {
    if (!id) { setCatalog(null); return; }
    const next = await myExperienceApi.catalog(id);
    setCatalog(next);
    setSelectedId((current) => current && next.experiences.some((item) => item.id === current) ? current : next.experiences[0]?.id || '');
  }, []);

  useEffect(() => {
    if (auth.status === 'authenticated') void loadBusinesses().catch(() => setError('No se han podido cargar tus empresas.'));
  }, [auth.status, loadBusinesses]);
  useEffect(() => { if (businessId) void loadCatalog(businessId).catch(() => setError('No se han podido cargar tus experiencias.')); }, [businessId, loadCatalog]);

  const selected = useMemo(() => catalog?.experiences.find((item) => item.id === selectedId) ?? null, [catalog, selectedId]);
  const slots = useMemo(() => catalog?.slots.filter((item) => item.experience_id === selectedId) ?? [], [catalog, selectedId]);
  const canEdit = catalog ? ['owner','manager','editor'].includes(catalog.role) : false;

  useEffect(() => {
    if (!selected) return;
    setTitle(selected.title);
    setSummary(typeof selected.summary === 'string' ? selected.summary : '');
    setPrice(euros(selected.price_cents));
    setMaxParty(typeof selected.max_party_size === 'number' ? String(selected.max_party_size) : '');
    setStatus(selected.status);
  }, [selected]);

  async function run(task: () => Promise<unknown>, success: string) {
    setBusy(true); setError(null); setMessage(null);
    try { await task(); await loadCatalog(businessId); setMessage(success); }
    catch (cause) { console.error(cause); setError('No se ha podido guardar el cambio. Revisa las reservas confirmadas y vuelve a intentarlo.'); }
    finally { setBusy(false); }
  }

  async function save() {
    if (!selected || !canEdit) return;
    const priceCents = price.trim() ? Math.round(Number(price.replace(',', '.')) * 100) : null;
    const maxPartySize = maxParty.trim() ? Number(maxParty) : null;
    if ((priceCents !== null && !Number.isFinite(priceCents)) || (maxPartySize !== null && (!Number.isInteger(maxPartySize) || maxPartySize < 1))) {
      setError('Revisa precio y tamaño máximo del grupo.'); return;
    }
    await run(() => myExperienceApi.update(businessId, selected.id, { title:title.trim(), summary:summary.trim() || null, priceCents, maxPartySize, status }), 'Experiencia actualizada.');
  }

  async function slotChange(id: string, confirmed: number, nextStatus?: ExperienceSlotStatus) {
    const raw = slotCapacity[id];
    const capacity = raw ? Number(raw) : undefined;
    if (capacity !== undefined && (!Number.isInteger(capacity) || capacity < Math.max(1, confirmed))) { setError(`El aforo no puede bajar de ${confirmed} plazas confirmadas.`); return; }
    await run(() => myExperienceApi.updateSlot(businessId, id, { ...(capacity !== undefined ? {capacity} : {}), ...(nextStatus ? {status:nextStatus} : {}) }), 'Sesión actualizada.');
    setSlotCapacity((current) => ({...current,[id]:''}));
  }

  if (auth.status !== 'authenticated' || !businesses.length || !catalog || !catalog.experiences.length) return null;
  return <section className={styles.shell}>
    <section className={styles.panel}>
      <div className={styles.panelTitle}><div><h2>Editar experiencias publicadas</h2><p>Gestiona contenido y sesiones de tu negocio sin acceso a controles comerciales de plataforma.</p></div></div>
      {message ? <div className={styles.success}>{message}</div> : null}{error ? <div className={styles.error}>{error}</div> : null}
      <div className={styles.formGrid}>
        <label>Empresa<select value={businessId} onChange={(event) => setBusinessId(event.target.value)}>{businesses.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label>Experiencia<select value={selectedId} onChange={(event) => setSelectedId(event.target.value)}>{catalog.experiences.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}</select></label>
        <label>Estado<select disabled={!canEdit} value={status} onChange={(event) => setStatus(event.target.value as ExperienceStatus)}><option value="draft">Borrador</option><option value="published">Publicada</option><option value="archived">Archivada</option></select></label>
        <label className={styles.wide}>Título<input disabled={!canEdit} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
        <label className={styles.wide}>Resumen<input disabled={!canEdit} maxLength={400} value={summary} onChange={(event) => setSummary(event.target.value)} /></label>
        <label>Precio €/persona<input disabled={!canEdit} inputMode="decimal" value={price} onChange={(event) => setPrice(event.target.value)} /></label>
        <label>Máximo grupo<input disabled={!canEdit} type="number" min="1" value={maxParty} onChange={(event) => setMaxParty(event.target.value)} /></label>
      </div>
      {canEdit ? <div className={styles.actions}><button className={styles.button} disabled={busy || !title.trim()} onClick={() => void save()}>Guardar cambios</button></div> : <p>Tu rol es de consulta.</p>}
      <div className={styles.operations}><h3>Sesiones</h3><div className={styles.claimList}>{slots.map((slot) => <article className={styles.claim} key={slot.id}><div><strong>{new Date(slot.starts_at).toLocaleString('es-ES')}</strong><p>{slot.confirmed_count}/{slot.capacity} confirmadas · {slot.status}</p></div>{canEdit ? <div className={styles.actions}><input type="number" min={Math.max(1,slot.confirmed_count)} placeholder={`Aforo ${slot.capacity}`} value={slotCapacity[slot.id] ?? ''} onChange={(event) => setSlotCapacity((current) => ({...current,[slot.id]:event.target.value}))}/><button className={styles.secondaryButton} disabled={busy || !slotCapacity[slot.id]} onClick={() => void slotChange(slot.id,slot.confirmed_count)}>Cambiar aforo</button>{slot.status === 'hidden' || slot.status === 'cancelled' ? <button className={styles.secondaryButton} disabled={busy} onClick={() => void slotChange(slot.id,slot.confirmed_count,'open')}>Reabrir</button> : <button className={styles.secondaryButton} disabled={busy} onClick={() => void slotChange(slot.id,slot.confirmed_count,'hidden')}>Ocultar</button>}{slot.confirmed_count===0 && slot.status!=='cancelled' ? <button className={styles.dangerButton} disabled={busy} onClick={() => void slotChange(slot.id,slot.confirmed_count,'cancelled')}>Cancelar</button> : null}</div> : null}</article>)}</div></div>
    </section>
  </section>;
}
