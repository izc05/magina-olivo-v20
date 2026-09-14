'use client';

import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { apiFetch } from '@/lib/api-client';

type MyBusiness = {
  id: string;
  name: string;
  role: string;
  status: string;
  municipality_name: string | null;
};

type RewardProduct = {
  id: string;
  title: string;
  slug: string;
  description: string | null;
  image_url: string | null;
  volume_ml: number | null;
  olive_cost: number;
  stock_total: number;
  stock_reserved: number;
  stock_redeemed: number;
  max_per_user: number | null;
  status: 'draft' | 'published' | 'paused' | 'archived';
  starts_at: string | null;
  ends_at: string | null;
};

type Redemption = {
  id: string;
  status: string;
  olives_spent: number;
  expires_at: string;
  redeemed_at: string | null;
  cancelled_at: string | null;
  created_at: string;
  product_title: string;
};

type PanelStats = {
  reserved: number;
  redeemed: number;
  cancelled: number;
  expired: number;
  olives_redeemed: number;
};

type AuditEvent = {
  id: string;
  actor_user_id: string | null;
  event_type: string;
  metadata?: unknown;
  created_at: string;
};

type StockEvent = AuditEvent & {
  delta_reserved: number;
  delta_redeemed: number;
};

type BarcodeDetectorLike = {
  detect(source: HTMLVideoElement): Promise<Array<{ rawValue: string }>>;
};

type BarcodeDetectorConstructor = new (options?: { formats?: string[] }) => BarcodeDetectorLike;

type ProductDraft = { oliveCost: string; stockTotal: string; status: RewardProduct['status'] };

function extractSignedToken(value: string) {
  const trimmed = value.trim();
  const match = trimmed.match(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.[A-Za-z0-9_-]{16}/i);
  return match?.[0] ?? null;
}

function eventLabel(value: string) {
  if (value === 'created') return 'Reserva creada';
  if (value === 'redeemed') return 'Entregado';
  if (value === 'cancelled') return 'Cancelado';
  if (value === 'expired') return 'Caducado';
  if (value === 'reserved') return 'Stock reservado';
  if (value === 'released') return 'Stock liberado';
  if (value === 'manual_adjustment') return 'Ajuste manual';
  return value;
}

export function AlmazaraBusinessPanel() {
  const [businesses, setBusinesses] = useState<MyBusiness[]>([]);
  const [businessId, setBusinessId] = useState('');
  const [products, setProducts] = useState<RewardProduct[]>([]);
  const [redemptions, setRedemptions] = useState<Redemption[]>([]);
  const [stats, setStats] = useState<PanelStats | null>(null);
  const [drafts, setDrafts] = useState<Record<string, ProductDraft>>({});
  const [role, setRole] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [scanCode, setScanCode] = useState('');
  const [scanning, setScanning] = useState(false);
  const [savingProduct, setSavingProduct] = useState<string | null>(null);
  const [history, setHistory] = useState<{ redemptionId: string; events: AuditEvent[]; stockEvents: StockEvent[] } | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  async function loadBusinesses() {
    try {
      const result = await apiFetch<{ businesses: MyBusiness[] }>('/api/v1/my/businesses');
      setBusinesses(result.businesses);
      if (!businessId && result.businesses[0]) setBusinessId(result.businesses[0].id);
    } catch {
      setMessage('No se pudieron cargar tus empresas. Comprueba que has iniciado sesión y que la almazara está vinculada a tu cuenta.');
    }
  }

  async function loadRewards(selectedId = businessId) {
    if (!selectedId) return;
    try {
      const result = await apiFetch<{ role: string; products: RewardProduct[]; redemptions: Redemption[]; stats: PanelStats | null }>(`/api/v1/my/businesses/${selectedId}/almazara-rewards`);
      setRole(result.role);
      setProducts(result.products);
      setRedemptions(result.redemptions);
      setStats(result.stats);
      setDrafts(Object.fromEntries(result.products.map((product) => [product.id, {
        oliveCost: String(product.olive_cost),
        stockTotal: String(product.stock_total),
        status: product.status,
      }])));
    } catch {
      setMessage('No se pudo cargar el panel de recompensas de esta entidad.');
    }
  }

  useEffect(() => { void loadBusinesses(); }, []);
  useEffect(() => { if (businessId) { setHistory(null); void loadRewards(businessId); } }, [businessId]);
  useEffect(() => () => streamRef.current?.getTracks().forEach((track) => track.stop()), []);

  async function createReward(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!businessId) return;
    setMessage(null);
    const form = new FormData(event.currentTarget);
    const title = String(form.get('title') ?? '').trim();
    const slug = String(form.get('slug') ?? '').trim().toLowerCase();
    const oliveCost = Number(form.get('oliveCost'));
    const stockTotal = Number(form.get('stockTotal'));
    const volumeMlValue = Number(form.get('volumeMl'));
    try {
      await apiFetch(`/api/v1/my/businesses/${businessId}/almazara-rewards`, {
        method: 'POST',
        body: JSON.stringify({ title, slug, oliveCost, stockTotal,
          volumeMl: Number.isFinite(volumeMlValue) && volumeMlValue > 0 ? volumeMlValue : null,
          maxPerUser: 1, status: 'published' }),
      });
      event.currentTarget.reset();
      setMessage('Premio publicado correctamente.');
      await loadRewards();
    } catch {
      setMessage('No se pudo crear el premio. Revisa slug, stock y coste en aceitunas.');
    }
  }

  async function updateReward(product: RewardProduct) {
    if (!businessId) return;
    const draft = drafts[product.id];
    if (!draft) return;
    const oliveCost = Number(draft.oliveCost);
    const stockTotal = Number(draft.stockTotal);
    if (!Number.isInteger(oliveCost) || oliveCost < 1 || !Number.isInteger(stockTotal) || stockTotal < 0) {
      setMessage('Coste y stock deben ser números enteros válidos.');
      return;
    }
    setSavingProduct(product.id);
    setMessage(null);
    try {
      await apiFetch(`/api/v1/my/businesses/${businessId}/almazara-rewards/${product.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          slug: product.slug,
          title: product.title,
          description: product.description,
          imageUrl: product.image_url,
          volumeMl: product.volume_ml,
          oliveCost,
          stockTotal,
          maxPerUser: product.max_per_user,
          status: draft.status,
          startsAt: product.starts_at,
          endsAt: product.ends_at,
        }),
      });
      setMessage('Premio actualizado. El stock comprometido sigue protegido.');
      await loadRewards();
    } catch (error) {
      const detail = error instanceof Error ? error.message : '';
      setMessage(detail.includes('reward_stock_below_committed') ? 'No puedes bajar el stock por debajo de las unidades reservadas y ya entregadas.' : 'No se pudo actualizar el premio.');
    } finally {
      setSavingProduct(null);
    }
  }

  async function validateCode(raw = scanCode) {
    if (!businessId) return;
    const token = extractSignedToken(raw);
    if (!token) {
      setMessage('El QR no contiene una credencial firmada válida.');
      return;
    }
    setMessage(null);
    try {
      const result = await apiFetch<{ redemption: { id: string; productTitle: string; status: string } }>(`/api/v1/my/businesses/${businessId}/almazara-redemptions/${encodeURIComponent(token)}/redeem`, { method: 'POST' });
      setMessage(`✅ Entrega validada: ${result.redemption.productTitle}. El QR queda inutilizado.`);
      setScanCode('');
      await loadRewards();
    } catch (error) {
      const detail = error instanceof Error ? error.message : '';
      setMessage(detail.includes('expired') ? 'Este QR ha caducado; el stock y las aceitunas ya se han liberado.' : 'No se pudo validar el QR. Puede estar manipulado, usado, cancelado, caducado o pertenecer a otra almazara.');
    }
  }

  async function loadHistory(redemptionId: string) {
    if (!businessId) return;
    try {
      const result = await apiFetch<{ events: AuditEvent[]; stockEvents: StockEvent[] }>(`/api/v1/my/businesses/${businessId}/almazara-redemptions/${redemptionId}/history`);
      setHistory({ redemptionId, events: result.events, stockEvents: result.stockEvents });
    } catch {
      setMessage('No se pudo cargar la trazabilidad del canje.');
    }
  }

  async function startScanner() {
    const Detector = (window as unknown as { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector;
    if (!Detector || !navigator.mediaDevices?.getUserMedia) {
      setMessage('Este navegador no permite escanear QR directamente. Puedes introducir la credencial manualmente.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
      streamRef.current = stream;
      const video = videoRef.current;
      if (!video) return;
      video.srcObject = stream;
      await video.play();
      setScanning(true);
      const detector = new Detector({ formats: ['qr_code'] });
      const loop = async () => {
        if (!streamRef.current || !videoRef.current) return;
        try {
          const results = await detector.detect(videoRef.current);
          const value = results[0]?.rawValue;
          if (value) {
            stopScanner();
            setScanCode(value);
            await validateCode(value);
            return;
          }
        } catch {
          // A camera frame can fail detection without invalidating the scanner session.
        }
        requestAnimationFrame(() => { void loop(); });
      };
      void loop();
    } catch {
      setMessage('No se pudo abrir la cámara. Revisa el permiso de cámara o usa la credencial manual.');
    }
  }

  function stopScanner() {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setScanning(false);
  }

  const canEdit = role === 'owner' || role === 'manager' || role === 'editor';

  return <main style={{ maxWidth: 1080, margin: '0 auto', padding: '24px 18px 80px' }}>
    <header><span>ALMAZARAS · PANEL OPERATIVO</span><h1>Premios y canjes</h1><p>Gestiona productos, stock y recogidas QR de Mi Olivo con trazabilidad completa.</p></header>
    {message ? <p role="status" className="card">{message}</p> : null}

    <section className="card" style={{ marginTop: 20 }}>
      <label htmlFor="mill-business">Entidad</label>
      <select id="mill-business" value={businessId} onChange={(event) => setBusinessId(event.target.value)}>
        <option value="">Selecciona una entidad</option>{businesses.map((business) => <option key={business.id} value={business.id}>{business.name}{business.municipality_name ? ` · ${business.municipality_name}` : ''}</option>)}
      </select>{role ? <p>Permiso actual: <strong>{role}</strong></p> : null}
    </section>

    {stats ? <section className="card" style={{ marginTop: 20, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(130px,1fr))', gap: 12 }}>
      <div><small>Reservas</small><strong style={{ display: 'block', fontSize: '1.5rem' }}>{stats.reserved}</strong></div>
      <div><small>Entregados</small><strong style={{ display: 'block', fontSize: '1.5rem' }}>{stats.redeemed}</strong></div>
      <div><small>Cancelados</small><strong style={{ display: 'block', fontSize: '1.5rem' }}>{stats.cancelled}</strong></div>
      <div><small>Caducados</small><strong style={{ display: 'block', fontSize: '1.5rem' }}>{stats.expired}</strong></div>
      <div><small>Aceitunas canjeadas</small><strong style={{ display: 'block', fontSize: '1.5rem' }}>{stats.olives_redeemed}</strong></div>
    </section> : null}

    {businessId && canEdit ? <section className="card" style={{ marginTop: 20 }}><h2>Crear premio</h2><form onSubmit={createReward} style={{ display: 'grid', gap: 12, maxWidth: 640 }}>
      <label>Nombre de producto<input name="title" required minLength={2} placeholder="Botella AOVE 500 ml" /></label>
      <label>Slug<input name="slug" required pattern="[a-z0-9]+(?:-[a-z0-9]+)*" placeholder="aove-500-ml" /></label>
      <label>Coste en aceitunas<input name="oliveCost" required type="number" min="1" step="1" /></label>
      <label>Stock disponible<input name="stockTotal" required type="number" min="0" step="1" /></label>
      <label>Volumen ml<input name="volumeMl" type="number" min="1" step="1" /></label>
      <button type="submit">Publicar premio</button>
    </form></section> : null}

    {businessId && canEdit ? <section className="card" style={{ marginTop: 20 }}><h2>Validar entrega</h2><p>Escanea el QR firmado del usuario o pega la credencial completa que aparece debajo.</p><div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
      <input aria-label="Credencial firmada de canje" value={scanCode} onChange={(event) => setScanCode(event.target.value)} placeholder="UUID.firma" style={{ flex: '1 1 360px' }} />
      <button type="button" onClick={() => void validateCode()}>Validar</button>
      {!scanning ? <button type="button" onClick={() => void startScanner()}>Escanear con cámara</button> : <button type="button" onClick={stopScanner}>Cerrar cámara</button>}
    </div><video ref={videoRef} muted playsInline style={{ display: scanning ? 'block' : 'none', width: '100%', maxWidth: 520, marginTop: 16, borderRadius: 12 }} /></section> : null}

    <section className="card" style={{ marginTop: 20 }}><h2>Productos</h2>{!products.length ? <p>No hay premios creados.</p> : <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Premio</th><th>Coste</th><th>Stock total</th><th>Reservado</th><th>Entregado</th><th>Estado</th><th>Acción</th></tr></thead><tbody>{products.map((product) => {
      const draft = drafts[product.id];
      return <tr key={product.id}><td>{product.title}</td><td><input aria-label={`Coste ${product.title}`} type="number" min="1" step="1" value={draft?.oliveCost ?? product.olive_cost} onChange={(event) => setDrafts((current) => ({ ...current, [product.id]: { ...(current[product.id] ?? { stockTotal: String(product.stock_total), status: product.status }), oliveCost: event.target.value } }))} disabled={!canEdit} style={{ width: 90 }} /></td><td><input aria-label={`Stock ${product.title}`} type="number" min={product.stock_reserved + product.stock_redeemed} step="1" value={draft?.stockTotal ?? product.stock_total} onChange={(event) => setDrafts((current) => ({ ...current, [product.id]: { ...(current[product.id] ?? { oliveCost: String(product.olive_cost), status: product.status }), stockTotal: event.target.value } }))} disabled={!canEdit} style={{ width: 90 }} /></td><td>{product.stock_reserved}</td><td>{product.stock_redeemed}</td><td><select aria-label={`Estado ${product.title}`} value={draft?.status ?? product.status} onChange={(event) => setDrafts((current) => ({ ...current, [product.id]: { ...(current[product.id] ?? { oliveCost: String(product.olive_cost), stockTotal: String(product.stock_total) }), status: event.target.value as RewardProduct['status'] } }))} disabled={!canEdit}><option value="draft">Borrador</option><option value="published">Publicado</option><option value="paused">Pausado</option><option value="archived">Archivado</option></select></td><td>{canEdit ? <button type="button" disabled={savingProduct === product.id} onClick={() => void updateReward(product)}>{savingProduct === product.id ? 'Guardando…' : 'Guardar'}</button> : 'Solo lectura'}</td></tr>;
    })}</tbody></table></div>}</section>

    <section className="card" style={{ marginTop: 20 }}><h2>Últimos canjes</h2>{!redemptions.length ? <p>No hay canjes todavía.</p> : <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Premio</th><th>Aceitunas</th><th>Estado</th><th>Creado</th><th>Trazabilidad</th></tr></thead><tbody>{redemptions.map((item) => <tr key={item.id}><td>{item.product_title}</td><td>{item.olives_spent}</td><td>{item.status}</td><td>{new Date(item.created_at).toLocaleString('es-ES')}</td><td><button type="button" onClick={() => void loadHistory(item.id)}>Ver historial</button></td></tr>)}</tbody></table></div>}</section>

    {history ? <section className="card" style={{ marginTop: 20 }}><h2>Trazabilidad del canje</h2><p><code>{history.redemptionId}</code></p><div style={{ display: 'grid', gap: 8 }}>
      {[...history.events.map((event) => ({ ...event, source: 'canje' })), ...history.stockEvents.map((event) => ({ ...event, source: 'stock' }))]
        .sort((a, b) => new Date(a.created_at).getTime() - new Date(b.created_at).getTime())
        .map((event) => <div key={`${event.source}-${event.id}`} style={{ borderBottom: '1px solid rgba(0,0,0,.08)', paddingBottom: 8 }}><strong>{eventLabel(event.event_type)}</strong><span style={{ marginLeft: 8 }}>{new Date(event.created_at).toLocaleString('es-ES')}</span>{event.source === 'stock' && 'delta_reserved' in event ? <small style={{ display: 'block' }}>Δ reservado {event.delta_reserved} · Δ entregado {event.delta_redeemed}</small> : null}</div>)}
    </div></section> : null}
  </main>;
}
