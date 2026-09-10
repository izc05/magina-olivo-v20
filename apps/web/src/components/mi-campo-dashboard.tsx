'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { ArrowIcon, PlusIcon, SproutIcon } from '@/components/icons';
import { useAuth } from '@/components/auth-provider';
import { getPreviewFarms, loadWorkspaceFarms, summarizeFarms, type FarmListItem } from '@/lib/farm-data-source';

const quick = [
  ['＋', 'Registrar', 'Nueva actividad', '/mi-campo/registrar'],
  ['▱', 'Mapa', 'Fincas y referencias', '/mi-campo/mapa'],
] as const;

function farmHref(farm: FarmListItem) {
  if (farm.source === 'local') return `/mi-campo/fincas/local?id=${encodeURIComponent(farm.id)}`;
  if (farm.source === 'demo' && farm.id === 'las-cenillas') return '/mi-campo/fincas/las-cenillas';
  return `/mi-campo/fincas/${encodeURIComponent(farm.id)}`;
}

export function MiCampoDashboard() {
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [farms, setFarms] = useState<FarmListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        if (apiConfigured && status === 'authenticated' && selectedWorkspaceId) {
          const remote = await loadWorkspaceFarms(selectedWorkspaceId);
          if (!cancelled) setFarms(remote);
        } else if (!apiConfigured) {
          if (!cancelled) setFarms(getPreviewFarms());
        } else if (!cancelled) {
          setFarms([]);
        }
      } catch (loadError) {
        console.error('Unable to load Mi Campo farms', loadError);
        if (!cancelled) {
          setError('No se han podido cargar tus fincas.');
          setFarms([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void load();
    const refresh = () => { if (!apiConfigured) void load(); };
    window.addEventListener('magina:prototype-data-changed', refresh);
    return () => {
      cancelled = true;
      window.removeEventListener('magina:prototype-data-changed', refresh);
    };
  }, [apiConfigured, selectedWorkspaceId, status]);

  const summary = useMemo(() => summarizeFarms(farms), [farms]);

  return <>
    <header className="page-title mi-campo-title">
      <div className="title-mark"><SproutIcon /></div>
      <div><span className="eyebrow dark">GESTIÓN DEL OLIVAR</span><h1>Mi Campo</h1><p>Tus fincas y trabajos, ordenados y fáciles de consultar.</p></div>
    </header>

    <section className="card field-summary campaign-summary">
      <div className="stats">
        <div className="stat"><b>{loading ? '…' : summary.farms}</b><span>fincas</span></div>
        <div className="stat"><b>{loading ? '…' : summary.oliveTrees}</b><span>olivas</span></div>
        <div className="stat"><b>{summary.areaHa !== undefined ? `${summary.areaHa.toLocaleString('es-ES', { maximumFractionDigits: 2 })} ha` : '—'}</b><span>superficie registrada</span></div>
      </div>
    </section>

    <section className="section">
      <div className="section-head"><h2>Mis fincas</h2><Link href="/mi-campo/fincas/nueva" className="detail-link"><PlusIcon /> Añadir finca</Link></div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {!loading && farms.length === 0 ? <section className="card"><h3>Aún no tienes fincas</h3><p>Crea la primera con su nombre habitual. Catastro y SIGPAC podrán vincularse después.</p><Link href="/mi-campo/fincas/nueva" className="primary action-link">Añadir finca</Link></section> : null}
      <div className="farm-row">
        {farms.map((farm) => <Link key={`${farm.source}:${farm.id}`} href={farmHref(farm)} className="card farm-card">
          <div className="farm-image"><span className={`farm-status ${farm.tone ?? 'neutral'}`}>{farm.statusLabel ?? 'Activa'}</span></div>
          <div className="farm-body"><div className="farm-card-head"><div><h3>{farm.name}</h3><div className="farm-meta">{farm.oliveTrees ?? '—'} olivas · {farm.municipality ?? 'Municipio pendiente'}</div></div><ArrowIcon /></div></div>
        </Link>)}
      </div>
    </section>

    <section className="section"><div className="section-head"><h2>Acciones</h2><span /></div><div className="quick-grid">
      {quick.map(([icon, title, text, href]) => <Link href={href} className="card quick premium-quick" key={title}><span className="icon">{title === 'Registrar' ? <PlusIcon /> : icon}</span><div><strong>{title}</strong><small>{text}</small></div><ArrowIcon className="quick-arrow" /></Link>)}
    </div></section>

    <section className="territory-banner compact-banner"><div><span className="eyebrow">ESTRUCTURA V20</span><h2>Finca primero. Parcela, trabajos y datos dentro.</h2></div><Link href="/mi-campo/registrar">Registrar <ArrowIcon /></Link></section>
  </>;
}
