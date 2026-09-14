'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../lib/api-client';

type Sponsorship = {
  id: string;
  route_id: string | null;
  route_name: string | null;
  sponsor_name: string;
  sponsor_logo_url: string | null;
  sponsor_url: string | null;
  headline: string | null;
  description: string | null;
  cta_label: string | null;
  cta_url: string | null;
  promo_code: string | null;
  placement: string;
  billing_model: string;
  price_cents: number | null;
  currency: string;
  priority: number;
  status: string;
  starts_at: string | null;
  ends_at: string | null;
  disclosure: string;
  impressions: number;
  clicks: number;
  actions: number;
};

type RouteOption = { id: string; name: string };

const blank = {
  route_id: '', sponsor_name: '', sponsor_logo_url: '', sponsor_url: '', headline: '', description: '',
  cta_label: 'Ver oferta', cta_url: '', promo_code: '', placement: 'route_sidebar', billing_model: 'flat',
  price_eur: '', priority: '0', status: 'draft', starts_at: '', ends_at: '', disclosure: 'Patrocinado',
};

export function RoutesSponsorshipAdmin() {
  const [items, setItems] = useState<Sponsorship[]>([]);
  const [routes, setRoutes] = useState<RouteOption[]>([]);
  const [form, setForm] = useState(blank);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [sponsorData, routeData] = await Promise.all([
        apiFetch<{ sponsorships: Sponsorship[] }>('/api/v1/admin/routes/sponsorships'),
        apiFetch<{ routes: Array<RouteOption & Record<string, unknown>> }>('/api/v1/admin/routes?limit=100'),
      ]);
      setItems(sponsorData.sponsorships);
      setRoutes(routeData.routes.map((route) => ({ id: route.id, name: route.name })));
      setMessage(null);
    } catch { setMessage('No se han podido cargar los patrocinios.'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const totals = useMemo(() => items.reduce((acc, item) => ({
    impressions: acc.impressions + Number(item.impressions || 0),
    clicks: acc.clicks + Number(item.clicks || 0),
    actions: acc.actions + Number(item.actions || 0),
  }), { impressions: 0, clicks: 0, actions: 0 }), [items]);

  function edit(item: Sponsorship) {
    setEditingId(item.id);
    setForm({
      route_id: item.route_id ?? '', sponsor_name: item.sponsor_name, sponsor_logo_url: item.sponsor_logo_url ?? '',
      sponsor_url: item.sponsor_url ?? '', headline: item.headline ?? '', description: item.description ?? '',
      cta_label: item.cta_label ?? 'Ver oferta', cta_url: item.cta_url ?? '', promo_code: item.promo_code ?? '',
      placement: item.placement, billing_model: item.billing_model,
      price_eur: item.price_cents == null ? '' : (item.price_cents / 100).toFixed(2), priority: String(item.priority),
      status: item.status, starts_at: item.starts_at?.slice(0,16) ?? '', ends_at: item.ends_at?.slice(0,16) ?? '',
      disclosure: item.disclosure || 'Patrocinado',
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function save() {
    if (!form.sponsor_name.trim()) { setMessage('El nombre del patrocinador es obligatorio.'); return; }
    const payload = {
      route_id: form.route_id || null,
      sponsor_name: form.sponsor_name.trim(),
      sponsor_logo_url: form.sponsor_logo_url || null,
      sponsor_url: form.sponsor_url || null,
      headline: form.headline || null,
      description: form.description || null,
      cta_label: form.cta_label || null,
      cta_url: form.cta_url || null,
      promo_code: form.promo_code || null,
      placement: form.placement,
      billing_model: form.billing_model,
      price_cents: form.price_eur === '' ? null : Math.round(Number(form.price_eur) * 100),
      currency: 'EUR',
      priority: Number(form.priority || 0),
      status: form.status,
      starts_at: form.starts_at ? new Date(form.starts_at).toISOString() : null,
      ends_at: form.ends_at ? new Date(form.ends_at).toISOString() : null,
      disclosure: form.disclosure || 'Patrocinado',
    };
    try {
      if (editingId) await apiFetch(`/api/v1/admin/routes/sponsorships/${editingId}`, { method: 'PUT', body: JSON.stringify(payload) });
      else await apiFetch('/api/v1/admin/routes/sponsorships', { method: 'POST', body: JSON.stringify(payload) });
      setForm(blank); setEditingId(null); setMessage('Patrocinio guardado.'); await refresh();
    } catch { setMessage('No se ha podido guardar el patrocinio. Revisa URLs, fechas y permisos.'); }
  }

  return <main className="admin-main" style={{maxWidth:1500,margin:'0 auto'}}>
    <header className="admin-main-header">
      <div><p className="admin-eyebrow">Rutas · Monetización</p><h2>Patrocinios</h2></div>
      <div className="admin-actions"><a className="admin-button secondary" href="/admin/rutas">Volver a rutas</a><button className="admin-button" onClick={() => void refresh()}>Actualizar</button></div>
    </header>

    <section className="admin-metrics">
      <article><span>Campañas</span><strong>{loading ? '…' : items.length}</strong></article>
      <article><span>Activas</span><strong>{items.filter((item) => item.status === 'active').length}</strong></article>
      <article><span>Impresiones</span><strong>{totals.impressions}</strong></article>
      <article><span>Clics</span><strong>{totals.clicks}</strong></article>
      <article><span>Acciones</span><strong>{totals.actions}</strong></article>
    </section>

    {message ? <p className="admin-notice success" style={{marginTop:16}}>{message}</p> : null}

    <section className="admin-grid-two" style={{marginTop:18}}>
      <article className="admin-card admin-form-card">
        <div className="admin-card-title"><div><h3>{editingId ? 'Editar patrocinio' : 'Nuevo patrocinio'}</h3><p>El pago da visibilidad comercial, nunca prioridad en seguridad o datos técnicos.</p></div></div>
        <div className="admin-field-grid">
          <label>Patrocinador<input value={form.sponsor_name} onChange={(e) => setForm({...form,sponsor_name:e.target.value})} /></label>
          <label>Ruta<select value={form.route_id} onChange={(e) => setForm({...form,route_id:e.target.value})}><option value="">Todas / global</option>{routes.map((route) => <option value={route.id} key={route.id}>{route.name}</option>)}</select></label>
          <label>Titular<input value={form.headline} onChange={(e) => setForm({...form,headline:e.target.value})} /></label>
          <label>Código promo<input value={form.promo_code} onChange={(e) => setForm({...form,promo_code:e.target.value})} /></label>
          <label>URL patrocinador<input value={form.sponsor_url} onChange={(e) => setForm({...form,sponsor_url:e.target.value})} placeholder="https://..." /></label>
          <label>Logo URL<input value={form.sponsor_logo_url} onChange={(e) => setForm({...form,sponsor_logo_url:e.target.value})} placeholder="https://..." /></label>
          <label>Texto CTA<input value={form.cta_label} onChange={(e) => setForm({...form,cta_label:e.target.value})} /></label>
          <label>URL CTA<input value={form.cta_url} onChange={(e) => setForm({...form,cta_url:e.target.value})} placeholder="https://..." /></label>
          <label>Ubicación<select value={form.placement} onChange={(e) => setForm({...form,placement:e.target.value})}><option value="route_hero">Hero</option><option value="route_sidebar">Lateral</option><option value="after_map">Después del mapa</option><option value="nearby_services">Servicios cercanos</option><option value="route_download">Descarga GPX</option><option value="collection">Colección</option></select></label>
          <label>Modelo<select value={form.billing_model} onChange={(e) => setForm({...form,billing_model:e.target.value})}><option value="flat">Cuota fija</option><option value="cpm">CPM</option><option value="cpc">CPC</option><option value="affiliate">Afiliación</option></select></label>
          <label>Precio €<input type="number" min="0" step="0.01" value={form.price_eur} onChange={(e) => setForm({...form,price_eur:e.target.value})} /></label>
          <label>Prioridad<input type="number" value={form.priority} onChange={(e) => setForm({...form,priority:e.target.value})} /></label>
          <label>Inicio<input type="datetime-local" value={form.starts_at} onChange={(e) => setForm({...form,starts_at:e.target.value})} /></label>
          <label>Fin<input type="datetime-local" value={form.ends_at} onChange={(e) => setForm({...form,ends_at:e.target.value})} /></label>
          <label>Estado<select value={form.status} onChange={(e) => setForm({...form,status:e.target.value})}><option value="draft">Borrador</option><option value="scheduled">Programado</option><option value="active">Activo</option><option value="paused">Pausado</option><option value="ended">Finalizado</option></select></label>
          <label>Etiqueta<input value={form.disclosure} onChange={(e) => setForm({...form,disclosure:e.target.value})} /></label>
        </div>
        <label>Descripción<textarea rows={4} value={form.description} onChange={(e) => setForm({...form,description:e.target.value})} /></label>
        <div className="admin-actions"><button className="admin-button" onClick={() => void save()}>{editingId ? 'Guardar cambios' : 'Crear patrocinio'}</button>{editingId ? <button className="admin-button secondary" onClick={() => {setEditingId(null);setForm(blank);}}>Cancelar</button> : null}</div>
      </article>

      <article className="admin-card">
        <div className="admin-card-title"><div><h3>Campañas</h3><p>Rendimiento comercial por ruta y colocación.</p></div></div>
        <div className="admin-content-list">
          {items.length ? items.map((item) => <button className="admin-content-row" key={item.id} onClick={() => edit(item)}>
            <span><strong>{item.sponsor_name}</strong><small>{item.route_name || 'Global'} · {item.placement} · {item.billing_model}</small><small>{item.impressions} imp. · {item.clicks} clics · {item.actions} acciones</small></span>
            <span className={`admin-status ${item.status === 'active' ? 'published' : item.status === 'ended' ? 'archived' : 'draft'}`}>{item.status}</span>
          </button>) : <p>No hay patrocinios creados.</p>}
        </div>
      </article>
    </section>
  </main>;
}