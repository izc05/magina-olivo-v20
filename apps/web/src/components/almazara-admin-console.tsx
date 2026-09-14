'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api-client';

type Product = {
  id: string;
  business_id: string;
  business_name: string;
  title: string;
  olive_cost: number;
  stock_total: number;
  stock_reserved: number;
  stock_redeemed: number;
  available_stock: number;
  status: string;
};

type Redemption = {
  id: string;
  business_name: string;
  product_title: string;
  olives_spent: number;
  status: string;
  expires_at: string;
  redeemed_at: string | null;
  created_at: string;
};

type StockAudit = {
  id: string;
  product_id: string;
  redemption_id: string | null;
  event_type: string;
  delta_reserved: number;
  delta_redeemed: number;
  created_at: string;
};

type AuditEvent = {
  id: string;
  actor_user_id: string | null;
  event_type: string;
  metadata: unknown;
  created_at: string;
};

type HistoryPayload = {
  redemption: Redemption & { user_id: string; product_id: string; business_id: string; cancelled_at: string | null };
  events: AuditEvent[];
  stock_events: Array<AuditEvent & { delta_reserved: number; delta_redeemed: number }>;
};

type Payload = {
  products: Product[];
  redemptions: Redemption[];
  stock_audit: StockAudit[];
  stats: { reserved: number; redeemed: number; cancelled: number; expired: number; olives_spent: number };
  expired_in_sweep: number;
};

function auditLabel(value: string) {
  if (value === 'created') return 'Reserva creada';
  if (value === 'reserved') return 'Stock reservado';
  if (value === 'redeemed') return 'Entregado';
  if (value === 'cancelled') return 'Cancelado';
  if (value === 'expired') return 'Caducado';
  if (value === 'released') return 'Stock liberado';
  if (value === 'manual_adjustment') return 'Ajuste manual';
  return value;
}

export function AlmazaraAdminConsole() {
  const [data, setData] = useState<Payload | null>(null);
  const [status, setStatus] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [history, setHistory] = useState<HistoryPayload | null>(null);
  const [sweeping, setSweeping] = useState(false);

  async function load(nextStatus = status) {
    setMessage(null);
    try {
      const query = nextStatus ? `?status=${encodeURIComponent(nextStatus)}` : '';
      const payload = await apiFetch<Payload>(`/api/v1/admin/almazara-rewards${query}`);
      setData(payload);
      if (payload.expired_in_sweep > 0) setMessage(`${payload.expired_in_sweep} reservas caducadas se han liberado automáticamente.`);
    } catch {
      setMessage('No se pudo cargar la supervisión de almazaras.');
    }
  }

  useEffect(() => { void load(''); }, []);

  async function pause(product: Product) {
    try {
      await apiFetch(`/api/v1/admin/almazara-rewards/${product.id}/pause`, { method: 'POST' });
      setMessage(`Premio pausado: ${product.title}`);
      await load();
    } catch {
      setMessage('No se pudo pausar el premio.');
    }
  }

  async function expireStale() {
    setSweeping(true);
    try {
      const result = await apiFetch<{ expired: number }>('/api/v1/admin/almazara-rewards/expire-stale', {
        method: 'POST',
        body: JSON.stringify({ limit: 5000 }),
      });
      setMessage(result.expired ? `${result.expired} reservas caducadas liberadas y reembolsadas.` : 'No había reservas caducadas pendientes.');
      await load();
    } catch {
      setMessage('No se pudo ejecutar el barrido de caducidades.');
    } finally {
      setSweeping(false);
    }
  }

  async function showHistory(redemptionId: string) {
    try {
      setHistory(await apiFetch<HistoryPayload>(`/api/v1/admin/almazara-rewards/redemptions/${redemptionId}/history`));
    } catch {
      setMessage('No se pudo cargar la trazabilidad de este canje.');
    }
  }

  return <main className="admin-page">
    <header className="admin-page__header"><div><span className="admin-kicker">EMPRESAS · ALMAZARAS</span><h1>Recompensas y canjes</h1><p>Control global de productos AOVE, stock, reservas, QR y entregas físicas.</p></div></header>
    {message ? <section className="admin-card" role="status"><p>{message}</p></section> : null}

    {data ? <>
      <section className="admin-grid admin-grid--stats">
        <article className="admin-card"><small>Reservados</small><strong>{data.stats.reserved}</strong></article>
        <article className="admin-card"><small>Entregados</small><strong>{data.stats.redeemed}</strong></article>
        <article className="admin-card"><small>Cancelados</small><strong>{data.stats.cancelled}</strong></article>
        <article className="admin-card"><small>Caducados</small><strong>{data.stats.expired}</strong></article>
        <article className="admin-card"><small>Aceitunas consumidas</small><strong>{data.stats.olives_spent}</strong></article>
      </section>

      <section className="admin-card"><div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'end', justifyContent: 'space-between' }}>
        <label>Filtrar canjes<select value={status} onChange={(event) => { setStatus(event.target.value); void load(event.target.value); }}><option value="">Todos</option><option value="reserved">Reservados</option><option value="redeemed">Entregados</option><option value="cancelled">Cancelados</option><option value="expired">Caducados</option></select></label>
        <button type="button" disabled={sweeping} onClick={() => void expireStale()}>{sweeping ? 'Liberando…' : 'Liberar caducados ahora'}</button>
      </div></section>

      <section className="admin-card"><h2>Premios publicados</h2><div style={{ overflowX: 'auto' }}><table><thead><tr><th>Almazara</th><th>Premio</th><th>Coste</th><th>Stock</th><th>Reservado</th><th>Entregado</th><th>Disponible</th><th>Estado</th><th></th></tr></thead><tbody>{data.products.map((product) => <tr key={product.id}><td>{product.business_name}</td><td>{product.title}</td><td>{product.olive_cost}</td><td>{product.stock_total}</td><td>{product.stock_reserved}</td><td>{product.stock_redeemed}</td><td>{product.available_stock}</td><td>{product.status}</td><td>{product.status === 'published' ? <button type="button" onClick={() => void pause(product)}>Pausar</button> : null}</td></tr>)}</tbody></table></div></section>

      <section className="admin-card"><h2>Canjes</h2>{!data.redemptions.length ? <p>No hay canjes para este filtro.</p> : <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Almazara</th><th>Premio</th><th>Aceitunas</th><th>Estado</th><th>Creado</th><th>Caduca</th><th>Historial</th></tr></thead><tbody>{data.redemptions.map((item) => <tr key={item.id}><td>{item.business_name}</td><td>{item.product_title}</td><td>{item.olives_spent}</td><td>{item.status}</td><td>{new Date(item.created_at).toLocaleString('es-ES')}</td><td>{new Date(item.expires_at).toLocaleString('es-ES')}</td><td><button type="button" onClick={() => void showHistory(item.id)}>Abrir</button></td></tr>)}</tbody></table></div>}</section>

      <section className="admin-card"><h2>Auditoría de stock</h2>{!data.stock_audit.length ? <p>Sin movimientos auditados todavía.</p> : <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Evento</th><th>Δ reservado</th><th>Δ entregado</th><th>Canje</th><th>Fecha</th></tr></thead><tbody>{data.stock_audit.map((item) => <tr key={item.id}><td>{auditLabel(item.event_type)}</td><td>{item.delta_reserved}</td><td>{item.delta_redeemed}</td><td>{item.redemption_id ?? '—'}</td><td>{new Date(item.created_at).toLocaleString('es-ES')}</td></tr>)}</tbody></table></div>}</section>

      {history ? <section className="admin-card"><h2>Trazabilidad completa</h2><p><strong>{history.redemption.business_name}</strong> · {history.redemption.product_title}</p><p>Canje <code>{history.redemption.id}</code> · usuario <code>{history.redemption.user_id}</code></p><div style={{ display: 'grid', gap: 8 }}>
        {[...history.events.map((event) => ({ ...event, source: 'canje' })), ...history.stock_events.map((event) => ({ ...event, source: 'stock' }))]
          .sort((a, b) => new Date(a.created_at).getTime() - new Date(b.created_at).getTime())
          .map((event) => <div key={`${event.source}-${event.id}`} style={{ borderBottom: '1px solid rgba(0,0,0,.08)', paddingBottom: 8 }}><strong>{auditLabel(event.event_type)}</strong><span style={{ marginLeft: 8 }}>{new Date(event.created_at).toLocaleString('es-ES')}</span>{event.source === 'stock' && 'delta_reserved' in event ? <small style={{ display: 'block' }}>Δ reservado {event.delta_reserved} · Δ entregado {event.delta_redeemed}</small> : null}</div>)}
      </div></section> : null}
    </> : <section className="admin-card"><p>Cargando recompensas…</p></section>}
  </main>;
}
