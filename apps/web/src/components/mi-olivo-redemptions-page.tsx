'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { cancelMyMillRedemption, loadMyMillRedemptions, type MillRedemption } from '@/lib/public-mills-source';
import { RewardQr } from './reward-qr';

const labels: Record<string, string> = {
  reserved: 'Reservado',
  redeemed: 'Recogido',
  cancelled: 'Cancelado',
  expired: 'Caducado',
};

function expiryCopy(expiresAt: string) {
  const milliseconds = new Date(expiresAt).getTime() - Date.now();
  if (!Number.isFinite(milliseconds) || milliseconds <= 0) return 'La reserva está pendiente de actualizar su caducidad.';
  const hours = Math.ceil(milliseconds / 3_600_000);
  if (hours < 1) return 'Caduca en menos de una hora.';
  if (hours < 24) return `Quedan ${hours} ${hours === 1 ? 'hora' : 'horas'} para recogerlo.`;
  const days = Math.ceil(hours / 24);
  return `Quedan ${days} ${days === 1 ? 'día' : 'días'} para recogerlo.`;
}

function RedemptionCard({ item, cancelling, onCancel }: { item: MillRedemption; cancelling: string | null; onCancel: (item: MillRedemption) => void }) {
  const reserved = item.status === 'reserved';
  return <article className="card" style={{ display: 'grid', gap: 12 }}>
    <div>
      <small>{item.businessName}</small>
      <h3 style={{ marginBottom: 6 }}>{item.productTitle}</h3>
      <p><strong>{labels[item.status] ?? item.status}</strong> · {item.olivesSpent} aceitunas</p>
      <small>Reservado el {new Date(item.createdAt).toLocaleString('es-ES')}</small>
    </div>

    {reserved ? <div className="card" style={{ display: 'grid', gap: 8, padding: 12 }}>
      <strong>📍 Recogida física en {item.businessName}</strong>
      <span>{expiryCopy(item.expiresAt)}</span>
      <small>Fecha límite: {new Date(item.expiresAt).toLocaleString('es-ES')}.</small>
      {item.businessSlug ? <Link href={`/almazaras?slug=${encodeURIComponent(item.businessSlug)}`}>Ver ficha, dirección y contacto de la almazara →</Link> : null}
    </div> : null}

    {reserved && item.code ? <>
      <RewardQr code={item.code} size={240} />
      <p><strong>Presenta este QR al recoger el premio.</strong> Está firmado y solo puede usarse una vez.</p>
      <code style={{ overflowWrap: 'anywhere' }}>{item.code}</code>
      <button type="button" disabled={cancelling === item.id} onClick={() => onCancel(item)}>
        {cancelling === item.id ? 'Cancelando…' : 'Cancelar reserva y recuperar aceitunas'}
      </button>
    </> : null}
    {reserved && !item.code ? <p role="status">El QR no está disponible temporalmente. La reserva sigue protegida y no se ha perdido.</p> : null}
    {item.status === 'redeemed' && item.redeemedAt ? <p>Recogido el {new Date(item.redeemedAt).toLocaleString('es-ES')}.</p> : null}
    {item.status === 'cancelled' || item.status === 'expired' ? <p>Las aceitunas de esta reserva se devuelven automáticamente a tu saldo.</p> : null}
    {!reserved && item.businessSlug ? <Link href={`/almazaras?slug=${encodeURIComponent(item.businessSlug)}`}>Volver a ver la almazara →</Link> : null}
  </article>;
}

export function MiOlivoRedemptionsPage() {
  const [items, setItems] = useState<MillRedemption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<string | null>(null);

  async function refresh() {
    setError(null);
    try {
      const result = await loadMyMillRedemptions();
      setItems(result.redemptions);
    } catch {
      setError('No se pudieron cargar tus canjes. Comprueba que has iniciado sesión.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void refresh(); }, []);

  const pending = useMemo(() => items
    .filter((item) => item.status === 'reserved')
    .sort((a, b) => new Date(a.expiresAt).getTime() - new Date(b.expiresAt).getTime()), [items]);
  const history = useMemo(() => items
    .filter((item) => item.status !== 'reserved')
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()), [items]);

  async function cancel(item: MillRedemption) {
    if (item.status !== 'reserved') return;
    setCancelling(item.id);
    setError(null);
    try {
      await cancelMyMillRedemption(item.id);
      await refresh();
    } catch {
      setError('No se pudo cancelar la reserva. Puede que ya haya sido recogida o haya caducado.');
    } finally {
      setCancelling(null);
    }
  }

  return <main style={{ maxWidth: 980, margin: '0 auto', padding: '24px 18px 80px' }}>
    <Link href="/mi-olivo">← Mi Olivo</Link>
    <header style={{ margin: '24px 0' }}>
      <span>MI OLIVO · RECOMPENSAS</span>
      <h1>Mis canjes</h1>
      <p>Consulta primero lo que tienes pendiente de recoger. Cada QR reservado está firmado, es de un solo uso y caduca automáticamente.</p>
      {!loading && pending.length ? <p><strong>{pending.length} {pending.length === 1 ? 'premio pendiente' : 'premios pendientes'} de recoger.</strong></p> : null}
    </header>

    {loading ? <p aria-live="polite">Cargando canjes…</p> : null}
    {error ? <p role="alert">{error}</p> : null}
    {!loading && !items.length ? <section className="card"><h2>Aún no tienes premios reservados</h2><p>Consigue aceitunas con Mi Olivo y visita el catálogo de almazaras para canjearlas.</p><Link href="/almazaras">Ver almazaras →</Link></section> : null}

    {pending.length ? <section aria-labelledby="pending-redemptions" style={{ marginTop: 24 }}>
      <h2 id="pending-redemptions">Pendientes de recoger</h2>
      <p>Las reservas que caducan antes aparecen primero.</p>
      <div style={{ display: 'grid', gap: 18 }}>
        {pending.map((item) => <RedemptionCard key={item.id} item={item} cancelling={cancelling} onCancel={(redemption) => void cancel(redemption)} />)}
      </div>
    </section> : null}

    {history.length ? <section aria-labelledby="redemption-history" style={{ marginTop: 32 }}>
      <h2 id="redemption-history">Historial</h2>
      <div style={{ display: 'grid', gap: 18 }}>
        {history.map((item) => <RedemptionCard key={item.id} item={item} cancelling={cancelling} onCancel={(redemption) => void cancel(redemption)} />)}
      </div>
    </section> : null}
  </main>;
}
