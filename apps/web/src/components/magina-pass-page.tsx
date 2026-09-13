'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useAuth } from './auth-provider';
import { GoogleSignInButton } from './google-sign-in-button';
import {
  businessPassApi,
  type MaginaPassProgram,
  type MaginaPassReward,
  type MaginaPassState,
  type MaginaPassStop,
} from '@/lib/business-pass-source';
import styles from './business-directory.module.css';

function moneyPoints(value: number) {
  return new Intl.NumberFormat('es-ES').format(value);
}

function rewardTypeLabel(type: MaginaPassReward['reward_type']) {
  const labels: Record<MaginaPassReward['reward_type'], string> = {
    benefit: 'Ventaja',
    discount: 'Descuento',
    gift: 'Regalo',
    experience: 'Experiencia',
    offer: 'Oferta',
  };
  return labels[type];
}

export function MaginaPassPage() {
  const auth = useAuth();
  const [programs, setPrograms] = useState<MaginaPassProgram[]>([]);
  const [selectedSlug, setSelectedSlug] = useState('');
  const [program, setProgram] = useState<MaginaPassProgram | null>(null);
  const [stops, setStops] = useState<MaginaPassStop[]>([]);
  const [rewards, setRewards] = useState<MaginaPassReward[]>([]);
  const [passState, setPassState] = useState<MaginaPassState | null>(null);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [redemptionCode, setRedemptionCode] = useState<{ title: string; code: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    void businessPassApi.programs().then((payload) => {
      if (cancelled) return;
      setPrograms(payload.programs);
      setSelectedSlug((current) => current || payload.programs[0]?.slug || '');
    }).catch((cause) => {
      console.error(cause);
      if (!cancelled) setError('No hemos podido cargar Mágina Pass.');
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!selectedSlug) {
      setProgram(null);
      setStops([]);
      setRewards([]);
      return;
    }
    let cancelled = false;
    void businessPassApi.program(selectedSlug).then((payload) => {
      if (cancelled) return;
      setProgram(payload.program);
      setStops(payload.stops);
      setRewards(payload.rewards);
    }).catch((cause) => {
      console.error(cause);
      if (!cancelled) setError('No hemos podido cargar este pasaporte.');
    });
    return () => { cancelled = true; };
  }, [selectedSlug]);

  async function refreshWallet() {
    if (auth.status !== 'authenticated') {
      setPassState(null);
      return;
    }
    try {
      setPassState(await businessPassApi.myPass());
    } catch (cause) {
      console.error(cause);
      setError('No hemos podido cargar tu saldo del pasaporte.');
    }
  }

  useEffect(() => {
    void refreshWallet();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.status]);

  const wallet = useMemo(() => passState?.wallets.find((item) => item.program_slug === selectedSlug) ?? null, [passState, selectedSlug]);
  const programCheckins = useMemo(() => passState?.checkins.filter((item) => item.program_id === program?.id) ?? [], [passState, program]);
  const programRedemptions = useMemo(() => passState?.redemptions.filter((item) => item.program_id === program?.id) ?? [], [passState, program]);

  async function submitCheckin(event: FormEvent) {
    event.preventDefault();
    const normalized = code.trim().replace(/^MAGINA_PASS:/i, '');
    if (!normalized) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    setRedemptionCode(null);
    try {
      const result = await businessPassApi.checkin(normalized);
      setMessage(`Visita validada en ${result.checkin.businessName}: +${result.checkin.pointsAwarded} puntos.`);
      setCode('');
      await refreshWallet();
    } catch (cause) {
      console.error(cause);
      const text = cause instanceof Error ? cause.message : '';
      setError(text.includes('409') ? 'Ya has validado esta parada recientemente. Podrás volver cuando termine el tiempo de espera.' : 'El código no es válido, ha caducado o no puede utilizarse ahora.');
    } finally {
      setBusy(false);
    }
  }

  async function redeem(reward: MaginaPassReward) {
    setBusy(true);
    setError(null);
    setMessage(null);
    setRedemptionCode(null);
    try {
      const result = await businessPassApi.redeem(reward.id);
      setRedemptionCode({ title: reward.title, code: result.redemption.code });
      setMessage(`Recompensa desbloqueada. Has usado ${result.redemption.pointsSpent} puntos.`);
      await refreshWallet();
    } catch (cause) {
      console.error(cause);
      setError('No se ha podido canjear esta recompensa. Comprueba tu saldo y su disponibilidad.');
    } finally {
      setBusy(false);
    }
  }

  return <main className={styles.page}>
    <header className={styles.hero}>
      <div className={styles.heroCopy}>
        <p className={styles.eyebrow}>DESCUBRE · VISITA · APOYA LO LOCAL</p>
        <h1>Mágina Pass</h1>
        <p>Convierte tu visita a Sierra Mágina en un pasaporte: descubre negocios y experiencias, valida paradas y desbloquea ventajas del territorio.</p>
        {programs.length > 1 ? <label>Pasaporte activo<select value={selectedSlug} onChange={(event) => setSelectedSlug(event.target.value)}>{programs.map((item) => <option key={item.id} value={item.slug}>{item.name}</option>)}</select></label> : null}
      </div>
      <div className={styles.heroStat}><strong>{program?.participating_businesses ?? 0}</strong><span>paradas participantes</span></div>
      <div className={styles.heroStat}><strong>{program?.rewards ?? 0}</strong><span>recompensas</span></div>
    </header>

    {error ? <div className={styles.error} role="alert">{error}</div> : null}
    {message ? <div className={styles.success}>{message}</div> : null}

    {!program ? <section className={styles.state}><strong>{programs.length ? 'Cargando pasaporte…' : 'Aún no hay un Mágina Pass publicado.'}</strong><p>Cuando haya un programa activo aparecerá aquí.</p></section> : <>
      <section className={styles.detailGrid}>
        <article className={styles.infoBox}><small>Programa</small><strong>{program.name}</strong><p>{program.description ?? 'Recorre Mágina y suma progreso en negocios participantes.'}</p></article>
        <article className={styles.infoBox}><small>Tu saldo</small><strong>{auth.status === 'authenticated' ? `${moneyPoints(wallet?.points_balance ?? 0)} puntos` : 'Inicia sesión para participar'}</strong><p>{wallet ? `${wallet.total_checkins} visitas · ${moneyPoints(wallet.total_points_earned)} puntos conseguidos en total` : 'Tu progreso se guarda en tu cuenta.'}</p></article>
      </section>

      {auth.status === 'anonymous' ? <section className={styles.claimCard}><div><p className={styles.eyebrow}>GUARDA TU PROGRESO</p><h2>Entra para empezar tu pasaporte</h2><p>Necesitamos una cuenta para evitar duplicados y conservar tus puntos entre dispositivos.</p></div><GoogleSignInButton /></section> : null}

      {auth.status === 'authenticated' ? <section className={styles.claimCard}>
        <div><p className={styles.eyebrow}>VALIDAR PARADA</p><h2>Escanea o introduce el código del establecimiento</h2><p>El QR de una parada contiene un código Mágina Pass. Puedes pegarlo aquí si tu dispositivo no abre directamente el enlace.</p></div>
        <form className={styles.claimForm} onSubmit={submitCheckin}>
          <div className={`${styles.claimField} ${styles.claimWide}`}><label htmlFor="pass-code">Código QR / Mágina Pass</label><input id="pass-code" autoComplete="off" value={code} onChange={(event) => setCode(event.target.value)} placeholder="MAGINA_PASS:…" /></div>
          <div className={styles.claimWide}><button className={styles.button} disabled={busy || !code.trim()} type="submit">{busy ? 'Validando…' : 'Validar visita'}</button></div>
        </form>
      </section> : null}

      {redemptionCode ? <section className={styles.claimCard}>
        <div><p className={styles.eyebrow}>CÓDIGO DE CANJE</p><h2>{redemptionCode.title}</h2><p>Enséñalo en el establecimiento. Este código se muestra ahora para que puedas conservarlo; el servidor guarda únicamente su huella criptográfica.</p></div>
        <div className={styles.infoBox}><small>Código</small><strong>{redemptionCode.code}</strong></div>
      </section> : null}

      <section>
        <p className={styles.eyebrow}>PARADAS</p>
        <h2>Descubre negocios participantes</h2>
        <div className={styles.resultsGrid}>{stops.map((stop) => <article key={stop.business_id} className={styles.card}>
          <div className={styles.cardImageWrap}>{stop.logo_url ? <img className={styles.cardImage} src={stop.logo_url} alt="" loading="lazy" /> : <div className={styles.cardPlaceholder} aria-hidden="true">◉</div>}</div>
          <div className={styles.cardBody}>
            <div><p className={styles.eyebrow}>{stop.featured_stop ? 'PARADA DESTACADA · ' : ''}+{stop.checkin_points} puntos</p><h3>{stop.name}</h3></div>
            <p>{stop.short_description ?? 'Parada participante en Mágina Pass.'}</p>
            <small>{stop.place_name ?? stop.municipality_name ?? 'Sierra Mágina'}</small>
            <div className={styles.actionRow}><Link className={styles.secondaryButton} href={`/empresas?slug=${encodeURIComponent(stop.slug)}`}>Ver negocio</Link></div>
          </div>
        </article>)}</div>
      </section>

      <section>
        <p className={styles.eyebrow}>RECOMPENSAS</p>
        <h2>Canjea tu progreso</h2>
        <div className={styles.detailGrid}>{rewards.map((reward) => {
          const affordable = (wallet?.points_balance ?? 0) >= reward.points_cost;
          return <article key={reward.id} className={styles.infoBox}>
            <small>{rewardTypeLabel(reward.reward_type)}{reward.business_name ? ` · ${reward.business_name}` : ''}</small>
            <strong>{reward.title}</strong>
            {reward.description ? <p>{reward.description}</p> : null}
            <p><strong>{moneyPoints(reward.points_cost)} puntos</strong></p>
            {reward.valid_until ? <small>Hasta {new Date(reward.valid_until).toLocaleDateString('es-ES')}</small> : null}
            {auth.status === 'authenticated' ? <div className={styles.actionRow}><button type="button" className={styles.button} disabled={busy || !affordable} onClick={() => void redeem(reward)}>{affordable ? 'Canjear' : 'Te faltan puntos'}</button></div> : null}
          </article>;
        })}</div>
      </section>

      {auth.status === 'authenticated' && programCheckins.length ? <section>
        <h2>Tu recorrido reciente</h2>
        <div className={styles.detailGrid}>{programCheckins.slice(0, 12).map((item) => <article key={item.id} className={styles.infoBox}><small>{new Date(item.occurred_at).toLocaleString('es-ES')}</small><strong>{item.business_name ?? item.source_type}</strong><p>+{item.points_awarded} puntos</p></article>)}</div>
      </section> : null}

      {auth.status === 'authenticated' && programRedemptions.length ? <section>
        <h2>Tus recompensas</h2>
        <div className={styles.detailGrid}>{programRedemptions.slice(0, 12).map((item) => <article key={item.id} className={styles.infoBox}><small>{item.status}</small><strong>{item.reward_title}</strong><p>{item.business_name ?? 'Recompensa territorial'} · {item.points_spent} puntos</p></article>)}</div>
      </section> : null}
    </>}
  </main>;
}
