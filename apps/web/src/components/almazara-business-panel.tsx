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
  olive_cost: number;
  stock_total: number;
  stock_reserved: number;
  stock_redeemed: number;
  status: string;
};

type Redemption = {
  id: string;
  status: string;
  olives_spent: number;
  expires_at: string;
  redeemed_at: string | null;
  created_at: string;
  product_title: string;
};

type BarcodeDetectorLike = {
  detect(source: HTMLVideoElement): Promise<Array<{ rawValue: string }>>;
};

type BarcodeDetectorConstructor = new (options?: { formats?: string[] }) => BarcodeDetectorLike;

function extractCode(value: string) {
  const trimmed = value.trim();
  const match = trimmed.match(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i);
  return match?.[0] ?? null;
}

export function AlmazaraBusinessPanel() {
  const [businesses, setBusinesses] = useState<MyBusiness[]>([]);
  const [businessId, setBusinessId] = useState('');
  const [products, setProducts] = useState<RewardProduct[]>([]);
  const [redemptions, setRedemptions] = useState<Redemption[]>([]);
  const [role, setRole] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [scanCode, setScanCode] = useState('');
  const [scanning, setScanning] = useState(false);
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
      const result = await apiFetch<{ role: string; products: RewardProduct[]; redemptions: Redemption[] }>(`/api/v1/my/businesses/${selectedId}/almazara-rewards`);
      setRole(result.role);
      setProducts(result.products);
      setRedemptions(result.redemptions);
    } catch {
      setMessage('No se pudo cargar el panel de recompensas de esta entidad.');
    }
  }

  useEffect(() => { void loadBusinesses(); }, []);
  useEffect(() => { if (businessId) void loadRewards(businessId); }, [businessId]);
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
        body: JSON.stringify({
          title,
          slug,
          oliveCost,
          stockTotal,
          volumeMl: Number.isFinite(volumeMlValue) && volumeMlValue > 0 ? volumeMlValue : null,
          maxPerUser: 1,
          status: 'published',
        }),
      });
      event.currentTarget.reset();
      setMessage('Premio publicado correctamente.');
      await loadRewards();
    } catch {
      setMessage('No se pudo crear el premio. Revisa slug, stock y coste en aceitunas.');
    }
  }

  async function validateCode(raw = scanCode) {
    if (!businessId) return;
    const code = extractCode(raw);
    if (!code) {
      setMessage('El QR o código no contiene un identificador de canje válido.');
      return;
    }
    setMessage(null);
    try {
      const result = await apiFetch<{ redemption: { id: string; productTitle: string; status: string } }>(`/api/v1/my/businesses/${businessId}/almazara-redemptions/${code}/redeem`, { method: 'POST' });
      setMessage(`✅ Entrega validada: ${result.redemption.productTitle}. El QR queda inutilizado.`);
      setScanCode('');
      await loadRewards();
    } catch (error) {
      const detail = error instanceof Error ? error.message : '';
      setMessage(detail.includes('expired') ? 'Este QR ha caducado; el stock y las aceitunas ya se han liberado.' : 'No se pudo validar el QR. Puede estar usado, cancelado, caducado o pertenecer a otra almazara.');
    }
  }

  async function startScanner() {
    const Detector = (window as unknown as { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector;
    if (!Detector || !navigator.mediaDevices?.getUserMedia) {
      setMessage('Este navegador no permite escanear QR directamente. Puedes introducir el código manualmente.');
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
          // Keep scanning; transient camera frames can fail detection.
        }
        requestAnimationFrame(() => { void loop(); });
      };
      void loop();
    } catch {
      setMessage('No se pudo abrir la cámara. Revisa el permiso de cámara o usa el código manual.');
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
    <header>
      <span>ALMAZARAS · PANEL OPERATIVO</span>
      <h1>Premios y canjes</h1>
      <p>Gestiona las botellas disponibles y valida los QR de Mi Olivo en el momento de la entrega.</p>
    </header>

    {message ? <p role="status" className="card">{message}</p> : null}

    <section className="card" style={{ marginTop: 20 }}>
      <label htmlFor="mill-business">Entidad</label>
      <select id="mill-business" value={businessId} onChange={(event) => setBusinessId(event.target.value)}>
        <option value="">Selecciona una entidad</option>
        {businesses.map((business) => <option key={business.id} value={business.id}>{business.name}{business.municipality_name ? ` · ${business.municipality_name}` : ''}</option>)}
      </select>
      {role ? <p>Permiso actual: <strong>{role}</strong></p> : null}
    </section>

    {businessId && canEdit ? <section className="card" style={{ marginTop: 20 }}>
      <h2>Crear premio</h2>
      <form onSubmit={createReward} style={{ display: 'grid', gap: 12, maxWidth: 640 }}>
        <label>Nombre de producto<input name="title" required minLength={2} placeholder="Botella AOVE 500 ml" /></label>
        <label>Slug<input name="slug" required pattern="[a-z0-9]+(?:-[a-z0-9]+)*" placeholder="aove-500-ml" /></label>
        <label>Coste en aceitunas<input name="oliveCost" required type="number" min="1" step="1" /></label>
        <label>Stock disponible<input name="stockTotal" required type="number" min="0" step="1" /></label>
        <label>Volumen ml<input name="volumeMl" type="number" min="1" step="1" /></label>
        <button type="submit">Publicar premio</button>
      </form>
    </section> : null}

    {businessId && canEdit ? <section className="card" style={{ marginTop: 20 }}>
      <h2>Validar entrega</h2>
      <p>Escanea el QR del usuario o pega el código que aparece debajo del QR.</p>
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <input aria-label="Código de canje" value={scanCode} onChange={(event) => setScanCode(event.target.value)} placeholder="UUID del canje" style={{ flex: '1 1 320px' }} />
        <button type="button" onClick={() => void validateCode()}>Validar</button>
        {!scanning ? <button type="button" onClick={() => void startScanner()}>Escanear con cámara</button> : <button type="button" onClick={stopScanner}>Cerrar cámara</button>}
      </div>
      <video ref={videoRef} muted playsInline style={{ display: scanning ? 'block' : 'none', width: '100%', maxWidth: 520, marginTop: 16, borderRadius: 12 }} />
    </section> : null}

    <section className="card" style={{ marginTop: 20 }}>
      <h2>Productos</h2>
      {!products.length ? <p>No hay premios creados.</p> : <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Premio</th><th>Coste</th><th>Stock</th><th>Reservado</th><th>Entregado</th><th>Estado</th></tr></thead><tbody>{products.map((product) => <tr key={product.id}><td>{product.title}</td><td>{product.olive_cost}</td><td>{product.stock_total}</td><td>{product.stock_reserved}</td><td>{product.stock_redeemed}</td><td>{product.status}</td></tr>)}</tbody></table></div>}
    </section>

    <section className="card" style={{ marginTop: 20 }}>
      <h2>Últimos canjes</h2>
      {!redemptions.length ? <p>No hay canjes todavía.</p> : <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Premio</th><th>Aceitunas</th><th>Estado</th><th>Creado</th></tr></thead><tbody>{redemptions.map((item) => <tr key={item.id}><td>{item.product_title}</td><td>{item.olives_spent}</td><td>{item.status}</td><td>{new Date(item.created_at).toLocaleString('es-ES')}</td></tr>)}</tbody></table></div>}
    </section>
  </main>;
}
