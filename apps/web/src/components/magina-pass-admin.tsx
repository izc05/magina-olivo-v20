'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import { businessAdminApi, type AdminBusiness } from '@/lib/business-admin-source';
import { businessPassAdminApi, type MaginaPassAdminCatalog } from '@/lib/business-pass-admin-source';
import styles from './business-admin.module.css';

function slugify(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

export function MaginaPassAdmin() {
  const auth = useAuth();
  const [catalog, setCatalog] = useState<MaginaPassAdminCatalog | null>(null);
  const [businesses, setBusinesses] = useState<AdminBusiness[]>([]);
  const [selectedProgram, setSelectedProgram] = useState('');
  const [programName, setProgramName] = useState('');
  const [programSlug, setProgramSlug] = useState('');
  const [programDescription, setProgramDescription] = useState('');
  const [programType, setProgramType] = useState<'points' | 'stamps' | 'challenge'>('points');
  const [defaultPoints, setDefaultPoints] = useState('10');
  const [cooldown, setCooldown] = useState('20');
  const [programStatus, setProgramStatus] = useState<'draft' | 'published'>('draft');
  const [stopBusinessId, setStopBusinessId] = useState('');
  const [stopPoints, setStopPoints] = useState('');
  const [stopCooldown, setStopCooldown] = useState('');
  const [featuredStop, setFeaturedStop] = useState(false);
  const [qrLabel, setQrLabel] = useState('Mostrador');
  const [qrResult, setQrResult] = useState<{ businessName: string; payload: string; code: string } | null>(null);
  const [rewardBusinessId, setRewardBusinessId] = useState('');
  const [rewardTitle, setRewardTitle] = useState('');
  const [rewardDescription, setRewardDescription] = useState('');
  const [rewardCost, setRewardCost] = useState('50');
  const [rewardType, setRewardType] = useState<'benefit' | 'discount' | 'gift' | 'experience' | 'offer'>('benefit');
  const [rewardStatus, setRewardStatus] = useState<'draft' | 'published'>('draft');
  const [busy, setBusy] = useState(false);
  const [denied, setDenied] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [pass, directory] = await Promise.all([businessPassAdminApi.catalog(), businessAdminApi.catalog()]);
    setCatalog(pass);
    setBusinesses(directory.businesses.filter((business) => business.status === 'published'));
    setSelectedProgram((current) => current || pass.programs[0]?.id || '');
    setStopBusinessId((current) => current || directory.businesses.find((business) => business.status === 'published')?.id || '');
  }, []);

  useEffect(() => {
    if (auth.status !== 'authenticated') return;
    setDenied(false);
    void load().catch((cause: unknown) => {
      const status = typeof cause === 'object' && cause && 'status' in cause ? Number((cause as { status?: unknown }).status) : 0;
      if (status === 403) setDenied(true);
      else setError('No se ha podido cargar Mágina Pass.');
    });
  }, [auth.status, load]);

  const currentProgram = useMemo(() => catalog?.programs.find((item) => item.id === selectedProgram) ?? null, [catalog, selectedProgram]);
  const stops = useMemo(() => catalog?.stops.filter((item) => item.program_id === selectedProgram) ?? [], [catalog, selectedProgram]);
  const rewards = useMemo(() => catalog?.rewards.filter((item) => item.program_id === selectedProgram) ?? [], [catalog, selectedProgram]);

  async function run(task: () => Promise<unknown>, success: string) {
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      await task();
      await load();
      setMessage(success);
    } catch (cause) {
      console.error(cause);
      setError('No se ha podido guardar el cambio. Comprueba los datos.');
    } finally {
      setBusy(false);
    }
  }

  async function createProgram() {
    if (!programName.trim()) return setError('Indica el nombre del pasaporte.');
    const points = Number(defaultPoints);
    const hours = Number(cooldown);
    if (!Number.isInteger(points) || points < 1 || !Number.isInteger(hours) || hours < 1) return setError('Puntos y cooldown deben ser números enteros positivos.');
    await run(async () => {
      const created = await businessPassAdminApi.createProgram({
        slug: slugify(programSlug || programName),
        name: programName.trim(),
        description: programDescription.trim() || null,
        programType,
        defaultCheckinPoints: points,
        defaultCooldownHours: hours,
        status: programStatus,
      });
      setSelectedProgram(created.program.id);
      setProgramName('');
      setProgramSlug('');
      setProgramDescription('');
      setProgramStatus('draft');
    }, 'Pasaporte creado.');
  }

  async function addStop() {
    if (!selectedProgram || !stopBusinessId) return setError('Selecciona un pasaporte y un negocio.');
    const points = stopPoints.trim() ? Number(stopPoints) : null;
    const hours = stopCooldown.trim() ? Number(stopCooldown) : null;
    if ((points !== null && (!Number.isInteger(points) || points < 1)) || (hours !== null && (!Number.isInteger(hours) || hours < 1))) return setError('Los valores personalizados deben ser enteros positivos.');
    await run(() => businessPassAdminApi.upsertStop(selectedProgram, {
      businessId: stopBusinessId,
      checkinPoints: points,
      cooldownHours: hours,
      featuredStop,
      active: true,
    }), 'Negocio añadido al pasaporte.');
  }

  async function issueQr(businessId: string, businessName: string) {
    if (!selectedProgram) return;
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      const result = await businessPassAdminApi.createQr(selectedProgram, businessId, { label: qrLabel.trim() || null });
      setQrResult({ businessName, payload: result.qr.payload, code: result.qr.code });
      await load();
      setMessage('QR emitido. Guarda el código: el servidor no conserva el token en claro.');
    } catch (cause) {
      console.error(cause);
      setError('No se ha podido emitir el QR.');
    } finally {
      setBusy(false);
    }
  }

  async function createReward() {
    if (!selectedProgram || !rewardTitle.trim()) return setError('Selecciona un pasaporte e indica la recompensa.');
    const pointsCost = Number(rewardCost);
    if (!Number.isInteger(pointsCost) || pointsCost < 1) return setError('El coste debe ser un número entero positivo.');
    await run(async () => {
      await businessPassAdminApi.createReward(selectedProgram, {
        businessId: rewardBusinessId || null,
        title: rewardTitle.trim(),
        description: rewardDescription.trim() || null,
        pointsCost,
        rewardType,
        status: rewardStatus,
      });
      setRewardTitle('');
      setRewardDescription('');
      setRewardStatus('draft');
    }, 'Recompensa creada.');
  }

  if (auth.status === 'loading') return <main className={styles.login}><div><strong>Comprobando acceso…</strong></div></main>;
  if (auth.status === 'anonymous') return <main className={styles.login}><div><p className={styles.eyebrow}>MÁGINA PASS · ADMIN</p><h1>Acceso corporativo</h1><GoogleSignInButton /></div></main>;
  if (denied) return <main className={styles.login}><div><p className={styles.eyebrow}>ACCESO RESTRINGIDO</p><h1>Sin permisos</h1><p>Necesitas permisos de plataforma para administrar Mágina Pass.</p></div></main>;
  if (!catalog) return <main className={styles.login}><div><strong>Cargando Mágina Pass…</strong>{error ? <p>{error}</p> : null}</div></main>;

  return <main className={styles.shell}>
    <header className={styles.header}>
      <div><a href="/admin/empresas">← Empresas</a><p className={styles.eyebrow}>FIDELIZACIÓN TERRITORIAL</p><h1>Mágina Pass</h1><p>Crea campañas de puntos, negocios participantes, QR y recompensas.</p></div>
      <div className={styles.actions}><a className={styles.secondaryButton} href="/magina-pass" target="_blank">Ver público ↗</a><button className={styles.secondaryButton} disabled={busy} onClick={() => void load()}>Actualizar</button></div>
    </header>

    {message ? <div className={styles.success}>{message}</div> : null}
    {error ? <div className={styles.error}>{error}</div> : null}

    <section className={styles.stats}>
      <article><strong>{catalog.programs.length}</strong><span>Programas</span></article>
      <article><strong>{currentProgram?.business_count ?? 0}</strong><span>Negocios</span></article>
      <article><strong>{currentProgram?.wallet_count ?? 0}</strong><span>Participantes</span></article>
      <article><strong>{currentProgram?.checkin_count ?? 0}</strong><span>Check-ins</span></article>
      <article><strong>{currentProgram?.redemption_count ?? 0}</strong><span>Canjes</span></article>
    </section>

    <section className={styles.layout}>
      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>Programas</h2><p>Un mismo territorio puede tener pasaportes por temporada, tema o campaña.</p></div></div>
        <label>Programa activo<select value={selectedProgram} onChange={(event) => setSelectedProgram(event.target.value)}><option value="">Ninguno</option>{catalog.programs.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.status}</option>)}</select></label>
        <div className={styles.formGrid}>
          <label>Nombre<input value={programName} onChange={(event) => { setProgramName(event.target.value); if (!programSlug) setProgramSlug(slugify(event.target.value)); }} /></label>
          <label>Slug<input value={programSlug} onChange={(event) => setProgramSlug(slugify(event.target.value))} /></label>
          <label>Tipo<select value={programType} onChange={(event) => setProgramType(event.target.value as typeof programType)}><option value="points">Puntos</option><option value="stamps">Sellos</option><option value="challenge">Reto</option></select></label>
          <label>Puntos por defecto<input type="number" min="1" value={defaultPoints} onChange={(event) => setDefaultPoints(event.target.value)} /></label>
          <label>Cooldown horas<input type="number" min="1" value={cooldown} onChange={(event) => setCooldown(event.target.value)} /></label>
          <label>Estado<select value={programStatus} onChange={(event) => setProgramStatus(event.target.value as typeof programStatus)}><option value="draft">Borrador</option><option value="published">Publicar</option></select></label>
          <label className={styles.wide}>Descripción<input value={programDescription} onChange={(event) => setProgramDescription(event.target.value)} /></label>
        </div>
        <div className={styles.actions}><button className={styles.button} disabled={busy} onClick={() => void createProgram()}>Crear pasaporte</button></div>
      </article>

      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>Añadir parada</h2><p>El negocio debe estar publicado en Empresas.</p></div></div>
        <div className={styles.formGrid}>
          <label>Negocio<select value={stopBusinessId} onChange={(event) => setStopBusinessId(event.target.value)}><option value="">Selecciona</option>{businesses.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label>Puntos propios<input type="number" min="1" placeholder={String(currentProgram?.default_checkin_points ?? 10)} value={stopPoints} onChange={(event) => setStopPoints(event.target.value)} /></label>
          <label>Cooldown propio<input type="number" min="1" placeholder={String(currentProgram?.default_cooldown_hours ?? 20)} value={stopCooldown} onChange={(event) => setStopCooldown(event.target.value)} /></label>
          <label className={styles.checkbox}><input type="checkbox" checked={featuredStop} onChange={(event) => setFeaturedStop(event.target.checked)} />Parada destacada</label>
        </div>
        <div className={styles.actions}><button className={styles.button} disabled={busy || !selectedProgram} onClick={() => void addStop()}>Añadir / actualizar</button></div>
      </article>
    </section>

    <section className={styles.panel}>
      <div className={styles.panelTitle}><div><h2>Paradas y QR</h2><p>Cada QR usa un token aleatorio. Guarda el payload al crearlo porque solo se conserva el hash.</p></div><label>Etiqueta QR<input value={qrLabel} onChange={(event) => setQrLabel(event.target.value)} /></label></div>
      <div className={styles.businessList}>{stops.map((stop) => <div key={stop.business_id} className={styles.businessRow}>
        <span><strong>{stop.business_name}</strong><small>{stop.featured_stop ? 'Destacada · ' : ''}{stop.checkin_points ?? currentProgram?.default_checkin_points} puntos · {stop.active_qr_count} QR activos</small></span>
        <button className={styles.secondaryButton} disabled={busy} onClick={() => void issueQr(stop.business_id, stop.business_name)}>Emitir QR</button>
      </div>)}</div>
      {qrResult ? <div className={styles.success}><strong>QR para {qrResult.businessName}</strong><p>Payload para codificar/imprimir: <code>{qrResult.payload}</code></p><p>Token: <code>{qrResult.code}</code></p></div> : null}
    </section>

    <section className={styles.layout}>
      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>Nueva recompensa</h2><p>Puede ser territorial o ligada a un negocio concreto.</p></div></div>
        <div className={styles.formGrid}>
          <label>Negocio<select value={rewardBusinessId} onChange={(event) => setRewardBusinessId(event.target.value)}><option value="">Territorial</option>{businesses.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label>Título<input value={rewardTitle} onChange={(event) => setRewardTitle(event.target.value)} /></label>
          <label>Coste puntos<input type="number" min="1" value={rewardCost} onChange={(event) => setRewardCost(event.target.value)} /></label>
          <label>Tipo<select value={rewardType} onChange={(event) => setRewardType(event.target.value as typeof rewardType)}><option value="benefit">Ventaja</option><option value="discount">Descuento</option><option value="gift">Regalo</option><option value="experience">Experiencia</option><option value="offer">Oferta</option></select></label>
          <label>Estado<select value={rewardStatus} onChange={(event) => setRewardStatus(event.target.value as typeof rewardStatus)}><option value="draft">Borrador</option><option value="published">Publicar</option></select></label>
          <label className={styles.wide}>Descripción<input value={rewardDescription} onChange={(event) => setRewardDescription(event.target.value)} /></label>
        </div>
        <div className={styles.actions}><button className={styles.button} disabled={busy || !selectedProgram} onClick={() => void createReward()}>Crear recompensa</button></div>
      </article>

      <article className={styles.panel}>
        <div className={styles.panelTitle}><div><h2>Recompensas del programa</h2><p>El histórico de canjes se conserva aunque se archive una recompensa.</p></div></div>
        <div className={styles.businessList}>{rewards.map((reward) => <div key={reward.id} className={styles.businessRow}><span><strong>{reward.title}</strong><small>{reward.business_name ?? 'Territorial'} · {reward.points_cost} puntos · {reward.status}</small></span><b>{reward.redemption_count} canjes</b></div>)}</div>
      </article>
    </section>
  </main>;
}
