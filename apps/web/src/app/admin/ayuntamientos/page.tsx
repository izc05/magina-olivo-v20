'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/components/auth-provider';
import { GoogleSignInButton } from '@/components/google-sign-in-button';
import { apiFetch } from '@/lib/api-client';
import '../admin.css';

type Directory = {
  official_website: string;
  electronic_office_url: string | null;
  transparency_url: string | null;
  tourism_url: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  postal_code: string | null;
  source_url: string;
  verified_at: string;
  public_enabled: boolean;
};

type Municipality = {
  id: string;
  ine_code: string;
  name: string;
  slug: string;
  directory: Directory | null;
};

type FormState = Directory & { id: string; name: string };

export default function AdminMunicipalitiesPage() {
  const auth = useAuth();
  const [items, setItems] = useState<Municipality[]>([]);
  const [selected, setSelected] = useState<FormState | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const payload = await apiFetch<{ municipalities: Municipality[] }>('/api/v1/admin/territory/catalog');
    setItems(payload.municipalities);
    setSelected((current) => {
      const requestedSlug = typeof window !== 'undefined' ? window.location.hash.replace(/^#/, '') : '';
      const municipality = payload.municipalities.find((item) => requestedSlug && item.slug === requestedSlug)
        ?? payload.municipalities.find((item) => item.id === current?.id)
        ?? payload.municipalities[0];
      if (!municipality?.directory) return null;
      return { id: municipality.id, name: municipality.name, ...municipality.directory };
    });
  }, []);

  useEffect(() => { if (auth.status === 'authenticated') void load().catch(() => setError('No se ha podido cargar el directorio.')); }, [auth.status, load]);

  function choose(item: Municipality) {
    if (!item.directory) return;
    setSelected({ id: item.id, name: item.name, ...item.directory });
    if (typeof window !== 'undefined') window.history.replaceState(null, '', `#${item.slug}`);
    setMessage(null); setError(null);
  }

  async function save() {
    if (!selected) return;
    setBusy(true); setMessage(null); setError(null);
    try {
      const { id, name: _name, ...payload } = selected;
      await apiFetch(`/api/v1/admin/territory/municipalities/${id}/directory`, { method: 'PATCH', body: JSON.stringify(payload) });
      await load();
      setMessage('Ficha institucional actualizada y auditada.');
    } catch {
      setError('No se ha podido guardar. Revisa URLs, email y fecha de verificación.');
    } finally { setBusy(false); }
  }

  if (auth.status === 'loading') return <main className="admin-login"><div>Comprobando acceso…</div></main>;
  if (auth.status === 'anonymous') return <main className="admin-login"><div><h1>Ayuntamientos</h1><p>Acceso corporativo requerido.</p><GoogleSignInButton /></div></main>;

  return <main className="admin-shell">
    <header className="admin-topbar"><div><a href="/admin/territorio">← Territorio</a><h1>Ayuntamientos de Sierra Mágina</h1><p>Directorio institucional verificado de los 16 municipios.</p></div><div style={{display:'flex',gap:'.75rem',flexWrap:'wrap'}}><a href="/admin/ayuntamientos/cobertura">Ver cobertura municipal</a><a href="/admin/ayuntamientos/patrimonio">Patrimonio y turismo</a><a href="/admin/ayuntamientos/actualidad">Vincular noticias y eventos</a><a href="/ayuntamientos" target="_blank">Ver directorio público ↗</a></div></header>
    {message ? <div className="admin-notice success">{message}</div> : null}{error ? <div className="admin-notice error">{error}</div> : null}
    <section style={{display:'grid',gridTemplateColumns:'minmax(240px,.8fr) minmax(0,1.8fr)',gap:'1rem',alignItems:'start'}}>
      <article className="admin-card"><h2>Municipios ({items.length})</h2><div style={{display:'grid',gap:'.45rem'}}>{items.map((item) => <button id={item.slug} key={item.id} type="button" onClick={() => choose(item)} style={{textAlign:'left',padding:'.75rem',borderRadius:'.75rem',border:'1px solid #d8ded8',background:selected?.id===item.id?'#e9f2e9':'#fff'}}><strong>{item.name}</strong><br/><small>INE {item.ine_code} · {item.directory?.public_enabled ? 'Público' : 'Oculto'}</small></button>)}</div></article>
      <article className="admin-card">{selected ? <><h2>{selected.name}</h2><p>Los cambios quedan registrados en la auditoría de plataforma.</p><div style={{display:'grid',gridTemplateColumns:'repeat(2,minmax(0,1fr))',gap:'.8rem'}}>
        <label>Web oficial<input value={selected.official_website} onChange={(e)=>setSelected({...selected,official_website:e.target.value})}/></label>
        <label>Sede electrónica<input value={selected.electronic_office_url??''} onChange={(e)=>setSelected({...selected,electronic_office_url:e.target.value||null})}/></label>
        <label>Portal transparencia<input value={selected.transparency_url??''} onChange={(e)=>setSelected({...selected,transparency_url:e.target.value||null})}/></label>
        <label>Turismo<input value={selected.tourism_url??''} onChange={(e)=>setSelected({...selected,tourism_url:e.target.value||null})}/></label>
        <label>Teléfono<input value={selected.phone??''} onChange={(e)=>setSelected({...selected,phone:e.target.value||null})}/></label>
        <label>Email<input type="email" value={selected.email??''} onChange={(e)=>setSelected({...selected,email:e.target.value||null})}/></label>
        <label>Dirección<input value={selected.address??''} onChange={(e)=>setSelected({...selected,address:e.target.value||null})}/></label>
        <label>Código postal<input value={selected.postal_code??''} onChange={(e)=>setSelected({...selected,postal_code:e.target.value||null})}/></label>
        <label>Fuente de verificación<input value={selected.source_url} onChange={(e)=>setSelected({...selected,source_url:e.target.value})}/></label>
        <label>Fecha verificación<input type="date" value={selected.verified_at.slice(0,10)} onChange={(e)=>setSelected({...selected,verified_at:e.target.value})}/></label>
      </div><label style={{display:'flex',gap:'.5rem',marginTop:'1rem'}}><input type="checkbox" checked={selected.public_enabled} onChange={(e)=>setSelected({...selected,public_enabled:e.target.checked})}/> Visible públicamente</label><button type="button" disabled={busy} onClick={()=>void save()} style={{marginTop:'1rem'}}>{busy?'Guardando…':'Guardar ficha institucional'}</button></> : <p>No hay ficha seleccionada.</p>}</article>
    </section>
  </main>;
}
