'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import {
  experienceAdminApi,
  type ExperienceAdminCatalog,
  type ExperienceAdminItem,
  type ExperienceAdminSlot,
  type ExperienceStatus,
  type ExperienceSlotStatus,
} from '@/lib/business-experience-source';
import styles from './business-admin.module.css';

function euros(cents: number | null | undefined) {
  return cents == null ? '' : String(cents / 100).replace('.', ',');
}

export function BusinessExperienceOperations() {
  const auth = useAuth();
  const [catalog, setCatalog] = useState<ExperienceAdminCatalog | null>(null);
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

  const load = useCallback(async () => {
    const next = await experienceAdminApi.catalog();
    setCatalog(next);
    setSelectedId((current) => current && next.experiences.some((item) => item.id === current) ? current : next.experiences[0]?.id ?? '');
  }, []);

  useEffect(() => {
    if (auth.status === 'authenticated') void load().catch(() => setError('No se ha podido cargar la gestión de experiencias.'));
  }, [auth.status, load]);

  const selected = useMemo(() => catalog?.experiences.find((item) => item.id === selectedId) ?? null, [catalog, selectedId]);
  const slots = useMemo(() => catalog?.slots.filter((item) => item.experience_id === selectedId) ?? [], [catalog, selectedId]);

  useEffect(() => {
    if (!selected) return;
    setTitle(selected.title);
    setSummary(typeof selected.summary === 'string' ? selected.summary : '');
    setPrice(euros(typeof selected.price_cents === 'number' ? selected.price_cents : null));
    setMaxParty(typeof selected.max_party_size === 'number' ? String(selected.max_party_size) : '');
    setStatus(selected.status);
  }, [selected]);

  async function run(task: () => Promise<unknown>, success: string) {
    setBusy(true); setError(null); setMessage(null);
    try { await task(); await load(); setMessage(success); }
    catch (cause) { console.error(cause); setError('No se ha podido aplicar el cambio. Comprueba los datos y las reservas confirmadas.'); }
    finally { setBusy(false); }
  }

  async function saveExperience() {
    if (!selected) return;
    const priceCents = price.trim() ? Math.round(Number(price.replace(',', '.')) * 100) : null;
    const maxPartySize = maxParty.trim() ? Number(maxParty) : null;
    if ((priceCents !== null && !Number.isFinite(priceCents)) || (maxPartySize !== null && (!Number.isInteger(maxPartySize) || maxPartySize < 1))) {
      setError('Revisa el precio y el tamaño máximo del grupo.'); return;
    }
    await run(() => experienceAdminApi.update(selected.id, {
      title: title.trim(), summary: summary.trim() || null, priceCents, maxPartySize, status,
    }), 'Experiencia actualizada.');
  }

  async function updateSlot(slot: ExperienceAdminSlot, nextStatus?: ExperienceSlotStatus) {
    const raw = slotCapacity[slot.id];
    const capacity = raw === undefined || raw === '' ? undefined : Number(raw);
    if (capacity !== undefined && (!Number.isInteger(capacity) || capacity < 1)) { setError('El aforo debe ser un entero positivo.'); return; }
    await run(() => experienceAdminApi.updateSlot(slot.id, { ...(capacity !== undefined ? { capacity } : {}), ...(nextStatus ? { status: nextStatus } : {}) }), 'Sesión actualizada.');
    setSlotCapacity((current) => ({ ...current, [slot.id]: '' }));
  }

  if (auth.status !== 'authenticated' || !catalog) return null;
  if (!catalog.experiences.length) return null;

  return <section className={styles.panel}>
    <div className={styles.panelTitle}><div><h2>Editar y cerrar experiencias</h2><p>Cambia contenido y disponibilidad sin tocar base de datos.</p></div></div>
    {message ? <div className={styles.success}>{message}</div> : null}
    {error ? <div className={styles.error}>{error}</div> : null}
    <div className={styles.formGrid}>
      <label>Experiencia<select value={selectedId} onChange={(event) => setSelectedId(event.target.value)}>{catalog.experiences.map((item: ExperienceAdminItem) => <option key={item.id} value={item.id}>{item.title} · {item.business_name}</option>)}</select></label>
      <label>Estado<select value={status} onChange={(event) => setStatus(event.target.value as ExperienceStatus)}><option value="draft">Borrador</option><option value="published">Publicada</option><option value="archived">Archivada</option></select></label>
      <label className={styles.wide}>Título<input value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      <label className={styles.wide}>Resumen<input maxLength={400} value={summary} onChange={(event) => setSummary(event.target.value)} /></label>
      <label>Precio €/persona<input inputMode="decimal" value={price} onChange={(event) => setPrice(event.target.value)} /></label>
      <label>Máximo grupo<input type="number" min="1" value={maxParty} onChange={(event) => setMaxParty(event.target.value)} /></label>
    </div>
    <div className={styles.actions}><button className={styles.button} disabled={busy || !title.trim()} onClick={() => void saveExperience()}>Guardar experiencia</button></div>

    <div className={styles.operations}>
      <h3>Sesiones</h3>
      <div className={styles.claimList}>{slots.length ? slots.map((slot) => <article className={styles.claim} key={slot.id}>
        <div><strong>{new Date(slot.starts_at).toLocaleString('es-ES')}</strong><p>Aforo {slot.confirmed_count}/{slot.capacity} · {slot.status}</p></div>
        <div className={styles.actions}>
          <input aria-label={`Nuevo aforo de ${slot.experience_title}`} type="number" min={Math.max(1, slot.confirmed_count)} placeholder={`Aforo ${slot.capacity}`} value={slotCapacity[slot.id] ?? ''} onChange={(event) => setSlotCapacity((current) => ({ ...current, [slot.id]: event.target.value }))} />
          <button className={styles.secondaryButton} disabled={busy || !slotCapacity[slot.id]} onClick={() => void updateSlot(slot)}>Cambiar aforo</button>
          {slot.status === 'hidden' || slot.status === 'cancelled' ? <button className={styles.secondaryButton} disabled={busy} onClick={() => void updateSlot(slot, 'open')}>Reabrir</button> : <button className={styles.secondaryButton} disabled={busy} onClick={() => void updateSlot(slot, 'hidden')}>Ocultar</button>}
          {slot.confirmed_count === 0 && slot.status !== 'cancelled' ? <button className={styles.dangerButton} disabled={busy} onClick={() => void updateSlot(slot, 'cancelled')}>Cancelar sesión</button> : null}
        </div>
      </article>) : <p>No hay sesiones para esta experiencia.</p>}</div>
    </div>
  </section>;
}
