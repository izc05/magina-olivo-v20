'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { PlusIcon, SproutIcon } from '@/components/icons';
import { useAuth } from '@/components/auth-provider';
import { getPreviewFarms, loadWorkspaceFarms, type FarmListItem } from '@/lib/farm-data-source';
import { getLocalFarmDerivedView, type FarmDerivedView } from '@/lib/farm-local-view-data';

const sections = ['Resumen', 'Actividad', 'Cosecha', 'Datos', 'Documentos'] as const;
type Section = (typeof sections)[number];

function formatMoney(value: number) {
  return `${value.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} €`;
}

function emptyDerived(): FarmDerivedView {
  return {
    workCount: 0,
    deliveryCount: 0,
    deliveredKg: 0,
    totalCostEur: 0,
    totalIncomeEur: 0,
    marginEur: 0,
    recentActivity: [],
  };
}

export function FarmDetailShell() {
  const params = useSearchParams();
  const id = params.get('id');
  const source = params.get('source');
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [farm, setFarm] = useState<FarmListItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeSection, setActiveSection] = useState<Section>('Resumen');
  const [derived, setDerived] = useState<FarmDerivedView>(emptyDerived());

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        if (!id) {
          if (!cancelled) setFarm(null);
          return;
        }
        if (source === 'api' && apiConfigured && status === 'authenticated' && selectedWorkspaceId) {
          const farms = await loadWorkspaceFarms(selectedWorkspaceId);
          if (!cancelled) {
            setFarm(farms.find((item) => item.id === id) ?? null);
            setDerived(emptyDerived());
          }
        } else {
          const farms = getPreviewFarms();
          if (!cancelled) {
            setFarm(farms.find((item) => item.id === id && (!source || item.source === source)) ?? null);
            setDerived(getLocalFarmDerivedView(id));
          }
        }
      } catch (error) {
        console.error('Unable to load finca detail', error);
        if (!cancelled) setFarm(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    const refresh = () => { if (source !== 'api') void load(); };
    window.addEventListener('magina:prototype-data-changed', refresh);
    return () => {
      cancelled = true;
      window.removeEventListener('magina:prototype-data-changed', refresh);
    };
  }, [apiConfigured, id, selectedWorkspaceId, source, status]);

  const registerHref = useMemo(() => id ? `/mi-campo/registrar?fieldId=${encodeURIComponent(id)}` : '/mi-campo/registrar', [id]);

  if (loading) return <section className="card"><p>Cargando finca…</p></section>;
  if (!farm) return <section className="card"><h1>Finca no encontrada</h1><p>La finca no está disponible en esta fuente de datos.</p><Link href="/mi-campo" className="secondary-action action-link">Volver a Mi Campo</Link></section>;

  return <>
    <header className="page-title mi-campo-title">
      <div className="title-mark"><SproutIcon /></div>
      <div><span className="eyebrow dark">MI CAMPO · FINCA</span><h1>{farm.name}</h1><p>{farm.municipality ?? 'Municipio pendiente'}{farm.oliveTrees ? ` · ${farm.oliveTrees} olivas` : ''}{farm.areaHa ? ` · ${farm.areaHa.toLocaleString('es-ES', { maximumFractionDigits: 2 })} ha` : ''}</p></div>
    </header>

    <section className="card field-summary campaign-summary"><div className="stats">
      <div className="stat"><b>{farm.oliveTrees ?? '—'}</b><span>olivas</span></div>
      <div className="stat"><b>{farm.areaHa ?? '—'}</b><span>hectáreas</span></div>
      <div className="stat"><b>{farm.waterRegime ?? '—'}</b><span>régimen</span></div>
    </div></section>

    <nav className="section farm-detail-tabs" aria-label="Secciones de la finca">
      <div className="quick-grid">
        {sections.map((section) => <button type="button" key={section} onClick={() => setActiveSection(section)} className={`card quick premium-quick${activeSection === section ? ' active' : ''}`} aria-pressed={activeSection === section}><strong>{section}</strong></button>)}
      </div>
    </nav>

    {activeSection === 'Resumen' ? <section className="section">
      <div className="section-head"><h2>Resumen</h2><Link href={registerHref} className="detail-link"><PlusIcon /> Registrar</Link></div>
      <div className="quick-grid">
        <article className="card quick premium-quick"><div><strong>{derived.workCount}</strong><small>trabajos registrados</small></div></article>
        <article className="card quick premium-quick"><div><strong>{Math.round(derived.deliveredKg).toLocaleString('es-ES')} kg</strong><small>{derived.deliveryCount} entregas de cosecha</small></div></article>
        <article className="card quick premium-quick"><div><strong>{derived.weightedYieldPercent !== undefined ? `${derived.weightedYieldPercent.toLocaleString('es-ES', { maximumFractionDigits: 2 })} %` : '—'}</strong><small>rendimiento ponderado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(derived.totalCostEur)}</strong><small>coste registrado</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(derived.totalIncomeEur)}</strong><small>ingresos registrados</small></div></article>
        <article className="card quick premium-quick"><div><strong>{formatMoney(derived.marginEur)}</strong><small>margen provisional</small></div></article>
      </div>
      {source === 'api' ? <p className="subtle">La finca ya procede de la API real. Las proyecciones agregadas de actividad, cosecha y economía se conectarán a endpoints de lectura específicos sin inventar datos en cliente.</p> : null}
    </section> : null}

    {activeSection === 'Actividad' ? <section className="section">
      <div className="section-head"><h2>Actividad</h2><Link href={registerHref} className="detail-link"><PlusIcon /> Registrar trabajo</Link></div>
      {derived.recentActivity.length ? <div className="card feed today-list">
        {derived.recentActivity.map((item) => <div className="feed-row" key={item.id}><div className="feed-copy"><strong>{item.title}</strong><small>{item.date}{item.summary ? ` · ${item.summary}` : ''}</small></div>{item.amountEur !== undefined ? <span className="pending-pill">{formatMoney(item.amountEur)}</span> : null}</div>)}
      </div> : <section className="card"><h3>Sin actividad todavía</h3><p>Los trabajos, cosechas y movimientos económicos aparecerán aquí ordenados por fecha.</p></section>}
    </section> : null}

    {activeSection === 'Cosecha' ? <section className="section"><div className="section-head"><h2>Cosecha</h2><Link href={registerHref} className="detail-link"><PlusIcon /> Registrar</Link></div><div className="card"><p>{derived.deliveryCount ? `${derived.deliveryCount} entregas · ${Math.round(derived.deliveredKg).toLocaleString('es-ES')} kg registrados.` : 'Aquí vivirán entregas, reparto entre fincas, tickets y resultados posteriores.'}</p>{derived.estimatedOilKg !== undefined ? <p><strong>Aceite estimado:</strong> {Math.round(derived.estimatedOilKg).toLocaleString('es-ES')} kg</p> : null}</div></section> : null}

    {activeSection === 'Datos' ? <section className="section"><div className="section-head"><h2>Datos</h2><span /></div><div className="card"><p><strong>Municipio:</strong> {farm.municipality ?? 'Pendiente'}</p><p><strong>Olivos:</strong> {farm.oliveTrees ?? 'Pendiente'}</p><p><strong>Superficie:</strong> {farm.areaHa !== undefined ? `${farm.areaHa} ha` : 'Pendiente'}</p><p><strong>Régimen:</strong> {farm.waterRegime ?? 'Pendiente'}</p><p>Parcelas, geometría propia, Catastro y SIGPAC se integrarán aquí como datos subordinados a la finca.</p></div></section> : null}

    {activeSection === 'Documentos' ? <section className="section"><div className="section-head"><h2>Documentos</h2><span /></div><div className="card"><p>Fotos, albaranes, facturas, fitosanitarios, resultados y archivos relacionados con esta finca compartirán esta superficie.</p></div></section> : null}

    <section className="territory-banner compact-banner"><div><span className="eyebrow">FINCA COMO UNIDAD PRINCIPAL</span><h2>Todo lo demás cuelga de esta ficha.</h2></div><Link href={registerHref}>Registrar</Link></section>
  </>;
}
