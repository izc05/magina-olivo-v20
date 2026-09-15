'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import {
  loadMillRewards,
  loadMyMillRewardUnlocks,
  loadPublicMills,
  redeemMillReward,
  type MillRewardUnlockState,
  type PublicMill,
  type PublicMillReward,
} from '@/lib/public-mills-source';
import { RewardQr } from './reward-qr';
import styles from './public-mills.module.css';

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
  if (value.startsWith('/')) return value;
  return safeExternalUrl(value);
}

function phoneHref(phone: string | null): string | null {
  if (!phone) return null;
  const normalized = phone.replace(/[^+\d]/g, '');
  return normalized.length >= 6 ? `tel:${normalized}` : null;
}

function searchableText(item: PublicMill) {
  return [item.title, item.summary, item.town, item.location, item.address, ...item.oliveVarieties].filter(Boolean).join(' ').toLocaleLowerCase('es');
}

function MillCard({ item, basePath }: { item: PublicMill; basePath: string }) {
  const image = safeMediaUrl(item.mediaUrl);
  return <article className={styles.card}>
    {image ? <img className={styles.cardImage} src={image} alt="" loading="lazy" /> : <div className={styles.cardPlaceholder} aria-hidden="true"><span>AOVE</span></div>}
    <div className={styles.cardBody}>
      <div className={styles.metaRow}>
        <span>{item.millKind === 'cooperativa' ? 'Cooperativa' : 'Almazara'}</span>
        {item.town ? <span>{item.town}</span> : null}
      </div>
      <h2>{item.title}</h2>
      {item.summary ? <p>{item.summary}</p> : null}
      {item.oliveVarieties.length ? <p><strong>Variedades:</strong> {item.oliveVarieties.join(', ')}</p> : null}
      <div className={styles.locationLine}>{[item.location, item.address].filter(Boolean).join(' · ') || 'Información de ubicación pendiente'}</div>
      {item.rewardCount > 0 ? <div className={styles.rewardHint}><span>Premios Mi Olivo</span><strong>{item.rewardCount}</strong></div> : null}
      <Link className={styles.primaryLink} href={`${basePath}?slug=${encodeURIComponent(item.slug)}`}>Ver ficha y premios →</Link>
    </div>
  </article>;
}

function RewardCatalog({ slug }: { slug: string }) {
  const [items, setItems] = useState<PublicMillReward[]>([]);
  const [unlockState, setUnlockState] = useState<MillRewardUnlockState | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [redeeming, setRedeeming] = useState<string | null>(null);
  const [credential, setCredential] = useState<{ code: string; productTitle: string; expiresAt: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setUnlockState(null);
    loadMillRewards(slug)
      .then((rows) => { if (!cancelled) setItems(rows); })
      .catch(() => { if (!cancelled) setMessage('No se pudieron cargar los premios de esta almazara.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    loadMyMillRewardUnlocks(slug)
      .then((state) => { if (!cancelled) setUnlockState(state); })
      .catch(() => { if (!cancelled) setUnlockState(null); });
    return () => { cancelled = true; };
  }, [slug]);

  async function redeem(item: PublicMillReward) {
    setRedeeming(item.id);
    setMessage(null);
    try {
      const result = await redeemMillReward(item.id);
      setCredential({ code: result.redemption.token, productTitle: result.redemption.productTitle, expiresAt: result.redemption.expiresAt });
      setItems((current) => current.map((row) => row.id === item.id ? { ...row, availableStock: Math.max(0, row.availableStock - 1) } : row));
    } catch (error) {
      const text = error instanceof Error ? error.message : '';
      if (text.includes('reward_level_locked')) {
        setMessage('Este premio todavía está bloqueado para tu nivel de Mi Olivo. Sigue acumulando XP para desbloquearlo.');
      } else if (text.includes('409')) {
        setMessage('No tienes aceitunas suficientes, se agotó el premio o ya alcanzaste el límite de canjes.');
      } else {
        setMessage('No ha sido posible realizar el canje. Inicia sesión y vuelve a intentarlo.');
      }
    } finally {
      setRedeeming(null);
    }
  }

  if (loading) return <section className={styles.stateCard}><strong>Cargando premios…</strong><p>Consultando stock, nivel requerido y condiciones actuales.</p></section>;

  return <section className={styles.rewardCatalog} aria-label="Premios de la almazara">
    <div className={styles.rewardIntro}>
      <div>
        <span className={styles.eyebrow}>MI OLIVO · RECOMPENSAS REALES</span>
        <h2>Del progreso digital a una botella de Mágina</h2>
        <p>Canjea tus aceitunas por productos reales. Algunos premios requieren un nivel permanente de Mi Olivo. Al confirmar, el stock queda reservado durante 7 días y recibes un QR firmado de un solo uso para recoger el premio.</p>
      </div>
      <div className={styles.rewardLinks}>
        <Link className={styles.secondaryLink} href="/mi-olivo">Abrir Mi Olivo</Link>
        <Link className={styles.secondaryLink} href="/mi-olivo/canjes">Mis canjes</Link>
      </div>
    </div>

    {unlockState ? <div className={styles.rewardHint} aria-label="Nivel actual de Mi Olivo">
      <span>Tu progreso: Nivel {unlockState.currentLevel} · {unlockState.currentLevelName}</span>
      <strong>{unlockState.xp} XP</strong>
    </div> : <div className={styles.softState}><strong>Consulta tus desbloqueos</strong><span>Inicia sesión para ver qué premios tienes disponibles por nivel.</span></div>}

    {message ? <div className={styles.message} role="alert">{message}</div> : null}

    {credential ? <section className={styles.credential} aria-label="Premio reservado">
      <div className={styles.credentialCopy}>
        <span className={styles.statusPill}>Reserva activa</span>
        <h3>{credential.productTitle}</h3>
        <p>Presenta este QR firmado en la almazara. El código solo puede validarse una vez.</p>
        <small>Caduca: {new Date(credential.expiresAt).toLocaleString('es-ES')}</small>
        <Link className={styles.primaryLink} href="/mi-olivo/canjes">Ver todos mis canjes →</Link>
      </div>
      <div className={styles.qrPanel}>
        <RewardQr code={credential.code} />
        <code>{credential.code}</code>
      </div>
    </section> : null}

    {!items.length ? <div className={styles.softState}><strong>Esta almazara todavía no tiene premios activos.</strong><span>La ficha sigue disponible y los premios aparecerán cuando exista stock publicado.</span></div> : null}

    <div className={styles.rewardGrid}>
      {items.map((item) => {
        const rewardImage = safeMediaUrl(item.imageUrl);
        const soldOut = item.availableStock < 1;
        const personal = unlockState?.rewards.find((entry) => entry.rewardId === item.id) ?? null;
        const lockedByLevel = personal ? !personal.unlocked : false;
        return <article className={styles.rewardCard} key={item.id}>
          {rewardImage ? <img className={styles.rewardImage} src={rewardImage} alt="" /> : <div className={styles.rewardPlaceholder} aria-hidden="true"><span>AOVE</span></div>}
          <div className={styles.rewardBody}>
            <div className={styles.rewardTopline}>
              <span className={styles.cost}>{item.oliveCost} aceitunas</span>
              <span className={`${styles.stock} ${soldOut ? styles.stockEmpty : ''}`}>{soldOut ? 'Agotado' : `${item.availableStock} disponibles`}</span>
            </div>
            <h3>{item.title}</h3>
            {item.volumeMl ? <small>{item.volumeMl} ml</small> : null}
            {item.description ? <p>{item.description}</p> : null}
            <div className={styles.rewardHint}>
              <span>Nivel {item.requiredLevel} · {item.requiredLevelName}</span>
              <strong>{item.minXp} XP</strong>
            </div>
            {personal ? <p><strong>{personal.unlocked ? `Desbloqueado con tu nivel ${unlockState?.currentLevel}` : `Bloqueado: necesitas nivel ${personal.requiredLevel}`}</strong></p> : null}
            <button className={styles.primaryLink} type="button" disabled={soldOut || lockedByLevel || redeeming === item.id} onClick={() => redeem(item)}>
              {redeeming === item.id ? 'Reservando…' : soldOut ? 'Sin stock' : lockedByLevel ? `Nivel ${item.requiredLevel} requerido` : 'Canjear premio'}
            </button>
          </div>
        </article>;
      })}
    </div>
  </section>;
}

function MillDetail({ item, basePath }: { item: PublicMill; basePath: string }) {
  const image = safeMediaUrl(item.mediaUrl);
  const externalUrl = safeExternalUrl(item.externalUrl);
  const callHref = phoneHref(item.phone);
  return <>
    <Link className={styles.backLink} href={basePath}>← Cooperativas y almazaras</Link>
    <article className={styles.detail}>
      {image ? <img className={styles.heroImage} src={image} alt="" /> : <div className={styles.heroPlaceholder} aria-hidden="true"><span>AOVE</span></div>}
      <div className={styles.detailBody}>
        <div className={styles.metaRow}><span>{item.millKind === 'cooperativa' ? 'Cooperativa' : 'Almazara'}</span>{item.town ? <span>{item.town}</span> : null}</div>
        <h1>{item.title}</h1>
        {item.summary ? <p className={styles.lead}>{item.summary}</p> : null}
        {item.oliveVarieties.length ? <p><strong>Variedades:</strong> {item.oliveVarieties.join(', ')}</p> : null}
        <dl className={styles.contactGrid}>
          {item.town ? <div><dt>Pueblo</dt><dd>{item.town}</dd></div> : null}
          {item.address ? <div><dt>Dirección</dt><dd>{item.address}</dd></div> : null}
          {item.phone ? <div><dt>Teléfono</dt><dd>{item.phone}</dd></div> : null}
          <div><dt>Tienda</dt><dd>{item.hasShop ? 'Sí' : 'No indicada'}</dd></div>
          <div><dt>Visitas</dt><dd>{item.acceptsVisits ? 'Disponibles' : 'No indicadas'}</dd></div>
        </dl>
        <div className={styles.actions}>
          {callHref ? <a className={styles.primaryLink} href={callHref}>Llamar</a> : null}
          {externalUrl ? <a className={styles.secondaryLink} href={externalUrl} target="_blank" rel="noopener noreferrer">Web oficial ↗</a> : null}
        </div>
      </div>
    </article>
    <RewardCatalog slug={item.slug} />
  </>;
}

export function PublicMillsPage({ basePath = '/cooperativas' }: { basePath?: '/cooperativas' | '/almazaras' }) {
  const params = useSearchParams();
  const slug = params.get('slug')?.trim() || null;
  const [items, setItems] = useState<PublicMill[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    loadPublicMills()
      .then((rows) => { if (!cancelled) setItems(rows); })
      .catch((cause) => {
        console.error('Unable to load public mills', cause);
        if (!cancelled) { setItems([]); setError(true); }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const selected = useMemo(() => slug ? items.find((item) => item.slug === slug) ?? null : null, [items, slug]);
  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('es');
    return needle ? items.filter((item) => searchableText(item).includes(needle)) : items;
  }, [items, query]);

  if (loading) return <main className={styles.page}><section className={styles.stateCard} aria-live="polite"><strong>Cargando cooperativas y almazaras…</strong><p>Consultando el directorio público de Mágina.</p></section></main>;
  if (error) return <main className={styles.page}><header className={styles.header}><span>SIERRA MÁGINA</span><h1>Cooperativas y almazaras</h1></header><section className={styles.stateCard} role="alert"><strong>Directorio no disponible ahora</strong><p>No mostramos entidades ni premios inventados. Vuelve a intentarlo cuando el servicio esté disponible.</p><button type="button" onClick={() => window.location.reload()}>Reintentar</button></section></main>;
  if (slug && !selected) return <main className={styles.page}><header className={styles.header}><span>SIERRA MÁGINA</span><h1>Cooperativas y almazaras</h1></header><section className={styles.stateCard}><strong>No encontramos esta ficha</strong><p>Puede haber sido retirada o su enlace haber cambiado.</p><Link href={basePath}>Volver al directorio</Link></section></main>;
  if (selected) return <main className={styles.page}><MillDetail item={selected} basePath={basePath} /></main>;

  return <main className={styles.page}>
    <header className={styles.header}>
      <div>
        <span>SIERRA MÁGINA · AOVE</span>
        <h1>El aceite también forma parte de tu experiencia.</h1>
        <p>Descubre cooperativas y almazaras del territorio, conoce dónde nace el AOVE de Sierra Mágina y utiliza las aceitunas de Mi Olivo en recompensas reales cuando exista stock publicado.</p>
      </div>
      <div className={styles.headerActions}>
        <Link className={styles.primaryLink} href="/mi-olivo">Ver Mi Olivo</Link>
        <Link className={styles.secondaryLink} href="/mi-olivo/canjes">Mis canjes</Link>
      </div>
    </header>
    <section className={styles.toolbar} aria-label="Buscar en el directorio">
      <label htmlFor="mill-search">Buscar por nombre, pueblo, variedad o dirección</label>
      <div className={styles.searchRow}><input id="mill-search" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Ej. Bedmar, picual, cooperativa…" />{query ? <button type="button" onClick={() => setQuery('')}>Limpiar</button> : null}</div>
      <small>{filtered.length} {filtered.length === 1 ? 'resultado' : 'resultados'}</small>
    </section>
    {!items.length ? <section className={styles.stateCard}><strong>Todavía no hay entidades publicadas</strong><p>El directorio aparecerá aquí cuando Administración publique cooperativas o almazaras.</p></section> : null}
    {items.length > 0 && !filtered.length ? <section className={styles.stateCard}><strong>Sin coincidencias</strong><button type="button" onClick={() => setQuery('')}>Ver todas</button></section> : null}
    <section className={styles.grid} aria-label="Cooperativas y almazaras publicadas">{filtered.map((item) => <MillCard key={item.id} item={item} basePath={basePath} />)}</section>
  </main>;
}
