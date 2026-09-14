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

type Payload = {
  products: Product[];
  redemptions: Redemption[];
  stats: { reserved: number; redeemed: number; cancelled: number; expired: number; olives_spent: number };
};

export function AlmazaraAdminConsole() {
  const [data, setData] = useState<Payload | null>(null);
  const [status, setStatus] = useState('');
  const [message, setMessage] = useState<string | null>(null);

  async function load(nextStatus = status) {
    setMessage(null);
    try {
      const query = nextStatus ? `?status=${encodeURIComponent(nextStatus)}` : '';
      setData(await apiFetch<Payload>(`/api/v1/admin/almazara-rewards${query}`));
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

  return <main className="admin-page">
    <header className="admin-page__header">
      <div>
        <span className="admin-kicker">EMPRESAS · ALMAZARAS</span>
        <h1>Recompensas y canjes</h1>
        <p>Control global de productos AOVE canjeables con Mi Olivo, stock y entregas físicas.</p>
      </div>
    </header>

    {message ? <section className="admin-card" role="status"><p>{message}</p></section> : null}

    {data ? <>
      <section className="admin-grid admin-grid--stats">
        <article className="admin-card"><small>Reservados</small><strong>{data.stats.reserved}</strong></article>
        <article className="admin-card"><small>Entregados</small><strong>{data.stats.redeemed}</strong></article>
        <article className="admin-card"><small>Cancelados</small><strong>{data.stats.cancelled}</strong></article>
        <article className="admin-card"><small>Caducados</small><strong>{data.stats.expired}</strong></article>
        <article className="admin-card"><small>Aceitunas consumidas</small><strong>{data.stats.olives_spent}</strong></article>
      </section>

      <section className="admin-card">
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'end' }}>
          <label>Filtrar canjes<select value={status} onChange={(event) => { setStatus(event.target.value); void load(event.target.value); }}><option value="">Todos</option><option value="reserved">Reservados</option><option value="redeemed">Entregados</option><option value="cancelled">Cancelados</option><option value="expired">Caducados</option></select></label>
        </div>
      </section>

      <section className="admin-card">
        <h2>Premios publicados</h2>
        <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Almazara</th><th>Premio</th><th>Coste</th><th>Stock</th><th>Reservado</th><th>Entregado</th><th>Disponible</th><th>Estado</th><th></th></tr></thead><tbody>{data.products.map((product) => <tr key={product.id}><td>{product.business_name}</td><td>{product.title}</td><td>{product.olive_cost}</td><td>{product.stock_total}</td><td>{product.stock_reserved}</td><td>{product.stock_redeemed}</td><td>{product.available_stock}</td><td>{product.status}</td><td>{product.status === 'published' ? <button type="button" onClick={() => void pause(product)}>Pausar</button> : null}</td></tr>)}</tbody></table></div>
      </section>

      <section className="admin-card">
        <h2>Canjes</h2>
        {!data.redemptions.length ? <p>No hay canjes para este filtro.</p> : <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Almazara</th><th>Premio</th><th>Aceitunas</th><th>Estado</th><th>Creado</th><th>Caduca</th></tr></thead><tbody>{data.redemptions.map((item) => <tr key={item.id}><td>{item.business_name}</td><td>{item.product_title}</td><td>{item.olives_spent}</td><td>{item.status}</td><td>{new Date(item.created_at).toLocaleString('es-ES')}</td><td>{new Date(item.expires_at).toLocaleString('es-ES')}</td></tr>)}</tbody></table></div>}
      </section>
    </> : <section className="admin-card"><p>Cargando recompensas…</p></section>}
  </main>;
}
