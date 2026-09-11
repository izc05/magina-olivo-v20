'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { ArrowIcon, PlusIcon, SproutIcon } from '@/components/icons';
import { useAuth } from '@/components/auth-provider';
import { getPreviewFarms, loadWorkspaceFarms, summarizeFarms, type FarmListItem } from '@/lib/farm-data-source';

const quick = [
  ['☀', 'Hoy', 'Pendientes y próximos trabajos', '/mi-campo/hoy'],
  ['🫒', 'Campaña', 'Producción, costes y cobros', '/mi-campo/campana'],
  ['▱', 'Mapa', 'Fincas y referencias', '/mi-campo/mapa'],
  ['€', 'Profesional', 'Clientes, trabajos y cobros', '/mi-campo/profesional'],
] as const;

function farmHref(farm: FarmListItem) {
  const params = new URLSearchParams({ id: farm.id, source: farm.source });
  return `/mi-campo/fincas/ver?${params.toString()}`;
}

function fieldActionHref(path: string, farm: FarmListItem) {
  const params = new URLSearchParams({ fieldId: farm.id, source: farm.source });
  return `${path}?${params.toString()}`;
}

export function MiCampoDashboard() {
  const { apiConfigured, previewEnabled, status, selectedWorkspaceId } = useAuth();
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
        } else if (!apiConfigured && previewEnabled) {
          if (!cancelled) setFarms(getPreviewFarms());
        } else if (!cancelled) {
          setFarms([]);
          if (!apiConfigured) setError('Mi Campo necesita conexión con la API. El modo demo no está activo en esta instalación.');
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
    const refresh = () => { if (!apiConfigured && previewEnabled) void load(); };
    window.addEventListener('magina:prototype-data-changed', refresh);
    return () => {
      cancelled = true;
      window.removeEventListener('magina:prototype-data-changed', refresh);
    };
  }, [apiConfigured, previewEnabled, selectedWorkspaceId, status]);

  const summary = useMemo(() => summarizeFarms(farms), [farms]);
  const defaultFarm = farms[0];
  const canCreateFarm = apiConfigured || previewEnabled;

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
      <div className="section-head"><h2>Mis fincas</h2>{canCreateFarm ? <Link href="/mi-campo/fincas/nueva" className="detail-link"><PlusIcon /> Añadir finca</Link> : <span />}</div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {!error && !loading && farms.length === 0 ? <section className="card"><h3>Aún no tienes fincas</h3><p>Crea la primera con su nombre habitual. Catastro y SIGPAC podrán vincularse después.</p>{canCreateFarm ? <Link href="/mi-campo/fincas/nueva" className="primary action-link">Añadir finca</Link> : null}</section> : null}
      <div className="farm-row">
        {farms.map((farm) => <Link key={`${farm.source}:${farm.id}`} href={farmHref(farm)} className="card farm-card">
          <div className="farm-image"><span className={`farm-status ${farm.tone ?? 'neutral'}`}>{farm.statusLabel ?? 'Activa'}</span></div>
          <div className="farm-body"><div className="farm-card-head"><div><h3>{farm.name}</h3><div className="farm-meta">{farm.oliveTrees ?? '—'} olivas · {farm.municipality ?? 'Municipio pendiente'}</div></div><ArrowIcon /></div></div>
        </Link>)}
      </div>
    </section>

    <section className="section"><div className="section-head"><h2>Acciones</h2><span /></div><div className="quick-grid">
      {defaultFarm ? <Link href={fieldActionHref('/mi-campo/registrar', defaultFarm)} className="card quick premium-quick"><span className="icon"><PlusIcon /></span><div><strong>Registrar</strong><small>Nueva actividad · {defaultFarm.name}</small></div><ArrowIcon className="quick-arrow" /></Link> : null}
      {quick.map(([icon, title, text, href]) => <Link href={href} className="card quick premium-quick" key={title}><span className="icon">{icon}</span><div><strong>{title}</strong><small>{text}</small></div><ArrowIcon className="quick-arrow" /></Link>)}
    </div></section>

    <section className="territory-banner compact-banner"><div><span className="eyebrow">ESTRUCTURA V20</span><h2>Finca primero. Campaña y agenda agregan sin duplicar.</h2></div>{defaultFarm ? <Link href={fieldActionHref('/mi-campo/registrar', defaultFarm)}>Registrar <ArrowIcon /></Link> : canCreateFarm ? <Link href="/mi-campo/fincas/nueva">Añadir finca <ArrowIcon /></Link> : <span />}</section>
  </>;
}
