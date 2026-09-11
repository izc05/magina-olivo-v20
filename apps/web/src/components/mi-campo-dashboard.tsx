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

type FarmStatusFilter = 'active' | 'all' | 'archived';

function farmHref(farm: FarmListItem) {
  const params = new URLSearchParams({ id: farm.id, source: farm.source });
  return `/mi-campo/fincas/ver?${params.toString()}`;
}

function fieldActionHref(path: string, farm: FarmListItem) {
  const params = new URLSearchParams({ fieldId: farm.id, source: farm.source });
  return `${path}?${params.toString()}`;
}

function farmMeta(farm: FarmListItem) {
  const parts = [
    farm.oliveTrees !== undefined ? `${farm.oliveTrees.toLocaleString('es-ES')} olivas` : undefined,
    farm.areaHa !== undefined ? `${farm.areaHa.toLocaleString('es-ES', { maximumFractionDigits: 2 })} ha` : undefined,
    farm.waterRegime,
    farm.municipality ?? 'Municipio pendiente',
  ].filter(Boolean);
  return parts.join(' · ');
}

export function MiCampoDashboard() {
  const { apiConfigured, previewEnabled, status, selectedWorkspaceId } = useAuth();
  const [farms, setFarms] = useState<FarmListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<FarmStatusFilter>('active');
  const [reloadKey, setReloadKey] = useState(0);

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
  }, [apiConfigured, previewEnabled, reloadKey, selectedWorkspaceId, status]);

  const activeFarms = useMemo(() => farms.filter((farm) => farm.status !== 'archived'), [farms]);
  const archivedCount = farms.length - activeFarms.length;
  const summary = useMemo(() => summarizeFarms(activeFarms), [activeFarms]);
  const visibleFarms = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('es-ES');
    return farms
      .filter((farm) => statusFilter === 'all' || (statusFilter === 'archived' ? farm.status === 'archived' : farm.status !== 'archived'))
      .filter((farm) => {
        if (!normalizedQuery) return true;
        return [farm.name, farm.municipality, farm.province]
          .filter(Boolean)
          .some((value) => value!.toLocaleLowerCase('es-ES').includes(normalizedQuery));
      })
      .sort((left, right) => {
        if (left.status !== right.status) return left.status === 'archived' ? 1 : -1;
        return left.name.localeCompare(right.name, 'es');
      });
  }, [farms, query, statusFilter]);
  const singleActiveFarm = activeFarms.length === 1 ? activeFarms[0] : undefined;
  const canCreateFarm = apiConfigured || previewEnabled;

  return <>
    <header className="page-title mi-campo-title">
      <div className="title-mark"><SproutIcon /></div>
      <div><span className="eyebrow dark">GESTIÓN DEL OLIVAR</span><h1>Mi Campo</h1><p>Tus fincas y trabajos, ordenados y fáciles de consultar.</p></div>
    </header>

    <section className="card field-summary campaign-summary">
      <div className="stats">
        <div className="stat"><b>{loading ? '…' : summary.farms}</b><span>fincas activas</span></div>
        <div className="stat"><b>{loading ? '…' : summary.oliveTrees.toLocaleString('es-ES')}</b><span>olivas</span></div>
        <div className="stat"><b>{summary.areaHa !== undefined ? `${summary.areaHa.toLocaleString('es-ES', { maximumFractionDigits: 2 })} ha` : '—'}</b><span>superficie registrada</span></div>
      </div>
      {!loading && archivedCount > 0 ? <p className="subtle">{archivedCount} {archivedCount === 1 ? 'finca archivada' : 'fincas archivadas'} fuera del resumen activo.</p> : null}
    </section>

    <section className="section" id="mis-fincas">
      <div className="section-head"><h2>Mis fincas</h2>{canCreateFarm ? <Link href="/mi-campo/fincas/nueva" className="detail-link"><PlusIcon /> Añadir finca</Link> : <span />}</div>
      {error ? <section className="card"><p className="form-error" role="alert">{error}</p><button type="button" className="secondary-action" onClick={() => setReloadKey((value) => value + 1)}>Reintentar</button></section> : null}
      {!error && loading ? <section className="card"><p>Cargando tus fincas…</p></section> : null}
      {!error && !loading && farms.length === 0 ? <section className="card"><h3>Aún no tienes fincas</h3><p>Crea la primera con su nombre habitual. Catastro y SIGPAC podrán vincularse después.</p>{canCreateFarm ? <Link href="/mi-campo/fincas/nueva" className="primary action-link">Añadir finca</Link> : null}</section> : null}

      {!error && !loading && farms.length > 0 ? <div className="card record-fields">
        <label className="record-field"><span>Buscar finca</span><input className="record-control" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nombre, municipio o provincia" /></label>
        <label className="record-field"><span>Estado</span><select className="record-control" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as FarmStatusFilter)}><option value="active">Activas</option><option value="all">Todas</option><option value="archived">Archivadas</option></select></label>
      </div> : null}

      {!error && !loading && farms.length > 0 && visibleFarms.length === 0 ? <section className="card"><h3>No hay fincas con estos filtros</h3><p>Prueba otro nombre o cambia el estado seleccionado.</p><button type="button" className="secondary-action" onClick={() => { setQuery(''); setStatusFilter('active'); }}>Limpiar filtros</button></section> : null}

      <div className="farm-row">
        {visibleFarms.map((farm) => <article key={`${farm.source}:${farm.id}`} className="card farm-card">
          <div className="farm-image"><span className={`farm-status ${farm.tone ?? 'neutral'}`}>{farm.statusLabel ?? (farm.status === 'archived' ? 'Archivada' : 'Activa')}</span></div>
          <div className="farm-body">
            <Link href={farmHref(farm)} className="farm-card-head"><div><h3>{farm.name}</h3><div className="farm-meta">{farmMeta(farm)}</div></div><ArrowIcon /></Link>
            <div className="record-actions">
              <Link href={farmHref(farm)} className="secondary-action action-link">Abrir finca</Link>
              {farm.status !== 'archived' ? <Link href={fieldActionHref('/mi-campo/registrar', farm)} className="primary action-link">Registrar</Link> : null}
            </div>
          </div>
        </article>)}
      </div>
    </section>

    <section className="section"><div className="section-head"><h2>Qué quieres hacer</h2><span /></div><div className="quick-grid">
      {singleActiveFarm ? <Link href={fieldActionHref('/mi-campo/registrar', singleActiveFarm)} className="card quick premium-quick"><span className="icon"><PlusIcon /></span><div><strong>Registrar</strong><small>Nueva actividad · {singleActiveFarm.name}</small></div><ArrowIcon className="quick-arrow" /></Link> : activeFarms.length > 1 ? <a href="#mis-fincas" className="card quick premium-quick"><span className="icon"><PlusIcon /></span><div><strong>Registrar</strong><small>Elige primero la finca</small></div><ArrowIcon className="quick-arrow" /></a> : null}
      {quick.map(([icon, title, text, href]) => <Link href={href} className="card quick premium-quick" key={title}><span className="icon">{icon}</span><div><strong>{title}</strong><small>{text}</small></div><ArrowIcon className="quick-arrow" /></Link>)}
    </div></section>

    <section className="territory-banner compact-banner"><div><span className="eyebrow">TU CAMPO, EN UN SOLO LUGAR</span><h2>Consulta tus fincas, registra trabajos y sigue la campaña sin perder el hilo.</h2></div>{singleActiveFarm ? <Link href={fieldActionHref('/mi-campo/registrar', singleActiveFarm)}>Registrar <ArrowIcon /></Link> : activeFarms.length > 1 ? <a href="#mis-fincas">Elegir finca <ArrowIcon /></a> : canCreateFarm ? <Link href="/mi-campo/fincas/nueva">Añadir finca <ArrowIcon /></Link> : <span />}</section>
  </>;
}
