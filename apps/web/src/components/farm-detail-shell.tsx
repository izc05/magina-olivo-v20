'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { ArrowIcon, PlusIcon, SproutIcon } from '@/components/icons';
import { useAuth } from '@/components/auth-provider';
import { getPreviewFarms, loadWorkspaceFarms, type FarmListItem } from '@/lib/farm-data-source';

const sections = [
  ['Resumen', 'Estado, campaña, pendientes y contexto'],
  ['Actividad', 'Trabajos, riegos, tratamientos, abonos y jornales'],
  ['Cosecha', 'Entregas, kilos y rendimiento'],
  ['Datos', 'Parcelas, mapa, Catastro, SIGPAC y características'],
  ['Documentos', 'Fotos, albaranes, facturas y análisis'],
] as const;

export function FarmDetailShell() {
  const params = useSearchParams();
  const id = params.get('id');
  const source = params.get('source');
  const { apiConfigured, status, selectedWorkspaceId } = useAuth();
  const [farm, setFarm] = useState<FarmListItem | null>(null);
  const [loading, setLoading] = useState(true);

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
          if (!cancelled) setFarm(farms.find((item) => item.id === id) ?? null);
        } else {
          const farms = getPreviewFarms();
          if (!cancelled) setFarm(farms.find((item) => item.id === id && (!source || item.source === source)) ?? null);
        }
      } catch (error) {
        console.error('Unable to load finca detail', error);
        if (!cancelled) setFarm(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
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

    <section className="section">
      <div className="section-head"><h2>Estructura de la finca</h2><Link href={registerHref} className="detail-link"><PlusIcon /> Registrar</Link></div>
      <div className="quick-grid">
        {sections.map(([title, text]) => <article className="card quick premium-quick" key={title}><div><strong>{title}</strong><small>{text}</small></div><ArrowIcon className="quick-arrow" /></article>)}
      </div>
    </section>

    <section className="territory-banner compact-banner"><div><span className="eyebrow">FINCA COMO UNIDAD PRINCIPAL</span><h2>Todo lo demás cuelga de esta ficha.</h2></div><Link href={registerHref}>Registrar <ArrowIcon /></Link></section>
  </>;
}
