'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { cancelMyMillRedemption, loadMyMillRedemptions, type MillRedemption } from '@/lib/public-mills-source';
import { RewardQr } from './reward-qr';

const labels: Record<string, string> = {
  reserved: 'Reservado',
  redeemed: 'Recogido',
  cancelled: 'Cancelado',
  expired: 'Caducado',
};

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
      <p>Guarda aquí tus premios de almazaras. Los QR reservados son de un solo uso y caducan automáticamente.</p>
    </header>

    {loading ? <p aria-live="polite">Cargando canjes…</p> : null}
    {error ? <p role="alert">{error}</p> : null}
    {!loading && !items.length ? <section className="card"><h2>Aún no tienes premios reservados</h2><p>Consigue aceitunas con Mi Olivo y visita el catálogo de almazaras para canjearlas.</p><Link href="/almazaras">Ver almazaras →</Link></section> : null}

    <div style={{ display: 'grid', gap: 18 }}>
      {items.map((item) => <article className="card" key={item.id} style={{ display: 'grid', gap: 12 }}>
        <div>
          <small>{item.businessName}</small>
          <h2>{item.productTitle}</h2>
          <p><strong>{labels[item.status] ?? item.status}</strong> · {item.olivesSpent} aceitunas</p>
        </div>
        {item.status === 'reserved' ? <>
          <RewardQr code={item.code} size={240} />
          <code style={{ overflowWrap: 'anywhere' }}>{item.code}</code>
          <p>Válido hasta {new Date(item.expiresAt).toLocaleString('es-ES')}.</p>
          <button type="button" disabled={cancelling === item.id} onClick={() => void cancel(item)}>
            {cancelling === item.id ? 'Cancelando…' : 'Cancelar y recuperar aceitunas'}
          </button>
        </> : null}
        {item.status === 'redeemed' && item.redeemedAt ? <p>Recogido el {new Date(item.redeemedAt).toLocaleString('es-ES')}.</p> : null}
        {item.status === 'cancelled' || item.status === 'expired' ? <p>Las aceitunas de esta reserva se devuelven automáticamente a tu saldo.</p> : null}
      </article>)}
    </div>
  </main>;
}
