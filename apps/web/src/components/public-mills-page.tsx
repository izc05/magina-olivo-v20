'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import {
  loadMillRewards,
  loadPublicMills,
  redeemMillReward,
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
    {image ? <img className={styles.cardImage} src={image} alt="" loading="lazy" /> : <div className={styles.cardPlaceholder} aria-hidden="true">🫒</div>}
    <div className={styles.cardBody}>
      <div className={styles.metaRow}>
        <span>{item.millKind === 'cooperativa' ? 'Cooperativa' : 'Almazara'}</span>
        {item.town ? <span>{item.town}</span> : null}
      </div>
      <h2>{item.title}</h2>
      {item.summary ? <p>{item.summary}</p> : null}
      {item.oliveVarieties.length ? <p><strong>Variedades:</strong> {item.oliveVarieties.join(', ')}</p> : null}
      <div className={styles.locationLine}>{[item.location, item.address].filter(Boolean).join(' · ') || 'Información de ubicación pendiente'}</div>
      {item.rewardCount > 0 ? <div className={styles.metaRow}><span>🎁 {item.rewardCount} {item.rewardCount === 1 ? 'premio disponible' : 'premios disponibles'}</span></div> : null}
      <Link className={styles.primaryLink} href={`${basePath}?slug=${encodeURIComponent(item.slug)}`}>Ver ficha y premios →</Link>
    </div>
  </article>;
}

function RewardCatalog({ slug }: { slug: string }) {
  const [items, setItems] = useState<PublicMillReward[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [redeeming, setRedeeming] = useState<string | null>(null);
  const [credential, setCredential] = useState<{ code: string; productTitle: string; expiresAt: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    loadMillRewards(slug)
      .then((rows) => { if (!cancelled) setItems(rows); })
      .catch(() => { if (!cancelled) setMessage('No se pudieron cargar los premios de esta almazara.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
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
      setMessage(text.includes('409') ? 'No tienes aceitunas suficientes, se agotó el premio o ya alcanzaste el límite de canjes.' : 'No ha sido posible realizar el canje. Inicia sesión y vuelve a intentarlo.');
    } finally {
      setRedeeming(null);
    }
  }

  if (loading) return <section className={styles.stateCard}><strong>Cargando premios…</strong></section>;

  return <section className={styles.stateCard} aria-label="Premios de la almazara">
    <h2>Premios con Mi Olivo</h2>
    <p>Canjea tus aceitunas por productos reales. Al confirmar se reservan durante 7 días y recibirás un QR único para recogerlos en la almazara.</p>
    {message ? <p role="alert">{message}</p> : null}
    {credential ? <div className={styles.cardBody}>
      <strong>✅ Premio reservado: {credential.productTitle}</strong>
      <p>Presenta este QR firmado en la almazara. Solo puede utilizarse una vez.</p>
      <RewardQr code={credential.code} />
      <code style={{ overflowWrap: 'anywhere', fontSize: '0.8rem' }}>{credential.code}</code>
      <small>Caduca: {new Date(credential.expiresAt).toLocaleString('es-ES')}</small>
      <Link className={styles.secondaryLink} href="/mi-olivo/canjes">Ver todos mis canjes →</Link>
    </div> : null}
    {!items.length ? <p>Esta almazara todavía no tiene premios activos.</p> : null}
    <div className={styles.grid}>
      {items.map((item) => <article className={styles.card} key={item.id}>
        {safeMediaUrl(item.imageUrl) ? <img className={styles.cardImage} src={safeMediaUrl(item.imageUrl) ?? ''} alt="" /> : <div className={styles.cardPlaceholder}>🫒</div>}
        <div className={styles.cardBody}>
          <h3>{item.title}</h3>
          {item.volumeMl ? <small>{item.volumeMl} ml</small> : null}
          {item.description ? <p>{item.description}</p> : null}
          <p><strong>{item.oliveCost} aceitunas</strong> · {item.availableStock > 0 ? `${item.availableStock} disponibles` : 'Agotado'}</p>
          <button className={styles.primaryLink} type="button" disabled={item.availableStock < 1 || redeeming === item.id} onClick={() => redeem(item)}>
            {redeeming === item.id ? 'Reservando…' : 'Canjear premio'}
          </button>
        </div>
      </article>)}
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
      {image ? <img className={styles.heroImage} src={image} alt="" /> : <div className={styles.heroPlaceholder} aria-hidden="true">🫒</div>}
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
  if (error) return <main className={styles.page}><header className={styles.header}><span>SIERRA MÁGINA</span><h1>Cooperativas y almazaras</h1></header><section className={styles.stateCard} role="alert"><strong>Directorio no disponible ahora</strong><p>Vuelve a intentarlo cuando el servicio esté disponible.</p><button type="button" onClick={() => window.location.reload()}>Reintentar</button></section></main>;
  if (slug && !selected) return <main className={styles.page}><header className={styles.header}><span>SIERRA MÁGINA</span><h1>Cooperativas y almazaras</h1></header><section className={styles.stateCard}><strong>No encontramos esta ficha</strong><p>Puede haber sido retirada o su enlace haber cambiado.</p><Link href={basePath}>Volver al directorio</Link></section></main>;
  if (selected) return <main className={styles.page}><MillDetail item={selected} basePath={basePath} /></main>;

  return <main className={styles.page}>
    <header className={styles.header}>
      <span>SIERRA MÁGINA · AOVE</span>
      <h1>Cooperativas y almazaras</h1>
      <p>Descubre dónde nace el AOVE de Sierra Mágina y convierte las aceitunas de Mi Olivo en recompensas reales del territorio.</p>
    </header>
    <section className={styles.toolbar} aria-label="Buscar en el directorio">
      <label htmlFor="mill-search">Buscar por nombre, pueblo, variedad o dirección</label>
      <div className={styles.searchRow}><input id="mill-search" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Ej. Bedmar, picual, cooperativa…" />{query ? <button type="button" onClick={() => setQuery('')}>Limpiar</button> : null}</div>
      <small>{filtered.length} {filtered.length === 1 ? 'resultado' : 'resultados'}</small>
    </section>
    {!items.length ? <section className={styles.stateCard}><strong>Todavía no hay entidades publicadas</strong><p>El directorio aparecerá aquí cuando Administración publique cooperativas o almazaras.</p></section> : null}
    {items.length && !filtered.length ? <section className={styles.stateCard}><strong>Sin coincidencias</strong><button type="button" onClick={() => setQuery('')}>Ver todas</button></section> : null}
    <section className={styles.grid} aria-label="Cooperativas y almazaras publicadas">{filtered.map((item) => <MillCard key={item.id} item={item} basePath={basePath} />)}</section>
  </main>;
}
