'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import type { ActivityType, FieldRecord } from '@/lib/domain';
import { getLocalActivities, getLocalFields } from '@/lib/local-prototype-store';
import { LocalRecordsPanel } from '@/components/local-records-panel';
import { ArrowIcon, PlusIcon } from '@/components/icons';

type ViewKey = 'cosechas' | 'riegos' | 'tratamientos' | 'abonos' | 'poda' | 'gastos' | 'calendario' | 'historia';

const views: Record<ViewKey, { title: string; symbol: string; subtitle: string; type?: ActivityType; register?: string }> = {
  cosechas: { title: 'Cosechas', symbol: '🫒', subtitle: 'Producción registrada para esta finca', type: 'harvest' },
  riegos: { title: 'Riegos', symbol: '💧', subtitle: 'Riegos realizados y próximos avisos', type: 'irrigation', register: 'riego' },
  tratamientos: { title: 'Tratamientos', symbol: '🌿', subtitle: 'Curas y seguimientos', type: 'treatment', register: 'tratamiento' },
  abonos: { title: 'Abonos', symbol: '🧪', subtitle: 'Productos, cantidades y costes', type: 'fertilization', register: 'abono' },
  poda: { title: 'Poda', symbol: '✂', subtitle: 'Trabajos de poda registrados', type: 'pruning', register: 'poda' },
  gastos: { title: 'Gastos', symbol: '€', subtitle: 'Costes derivados de tus registros', register: 'gasto' },
  calendario: { title: 'Calendario', symbol: '📅', subtitle: 'Seguimientos y trabajos programados' },
  historia: { title: 'Historia', symbol: '◷', subtitle: 'La memoria real de esta finca' },
};

export function LocalFieldModulePage() {
  const [field, setField] = useState<FieldRecord | null | undefined>(undefined);
  const [view, setView] = useState<ViewKey>('historia');
  const [summary, setSummary] = useState({ activities: 0, costs: 0, planned: 0 });

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const fieldId = params.get('fieldId');
    const rawView = params.get('view') as ViewKey | null;
    if (rawView && rawView in views) setView(rawView);
    if (!fieldId) { setField(null); return; }
    const found = getLocalFields().find((item) => item.id === fieldId) ?? null;
    setField(found);
    if (found) {
      const activities = getLocalActivities(found.id);
      setSummary({ activities: activities.length, costs: activities.reduce((sum, item) => sum + (item.costEur ?? 0), 0), planned: activities.filter((item) => item.followUpOn).length });
    }
  }, []);

  if (field === undefined) return <section className="card local-field-state"><strong>Cargando…</strong></section>;
  if (!field) return <section className="card local-field-state"><strong>No encuentro esta finca.</strong><Link href="/mi-campo">Volver a Mi Campo</Link></section>;

  const current = views[view];
  const fieldQuery = `fieldId=${encodeURIComponent(field.id)}`;
  const mode = view === 'gastos' ? 'costs' : view === 'calendario' ? 'calendar' : view === 'historia' ? 'history' : 'type';
  const registerHref = current.register ? `/mi-campo/registrar/${current.register}?${fieldQuery}` : view === 'cosechas' ? `/mi-campo/registrar/cosecha?${fieldQuery}` : null;

  return <>
    <header className="module-page-title local-module-title"><Link href={`/mi-campo/fincas/local?id=${encodeURIComponent(field.id)}`}>‹ {field.name}</Link><span className="module-page-symbol">{current.symbol}</span><div><span className="eyebrow dark">FICHA LOCAL</span><h1>{current.title}</h1><p>{current.subtitle}</p></div></header>

    {view === 'gastos' && <section className="card module-highlight cost-highlight"><span>COSTE REGISTRADO EN ESTE DISPOSITIVO</span><strong>{summary.costs.toLocaleString('es-ES')} €</strong><p>Solo suma actividades de esta finca que tengan un importe.</p></section>}
    {view === 'calendario' && <section className="card module-highlight calendar-highlight"><span>TRABAJOS PROGRAMADOS</span><strong>{summary.planned}</strong><p>Se crean al indicar una fecha de seguimiento en un registro.</p></section>}
    {view === 'historia' && <section className="card module-highlight harvest-highlight"><span>MEMORIA DE LA FINCA</span><strong>{summary.activities}</strong><p>registros guardados localmente.</p></section>}
    {view === 'cosechas' && <section className="card module-highlight harvest-highlight"><span>COSECHAS LOCALES</span><strong>{getLocalActivities(field.id).filter((item) => item.type === 'harvest').length}</strong><p>Confirmadas desde el OCR de demostración en este dispositivo.</p></section>}

    <LocalRecordsPanel fieldId={field.id} mode={mode} activityType={current.type}/>

    {view !== 'historia' && view !== 'calendario' && view !== 'gastos' && <section className="card local-field-empty-note"><strong>Sin datos inventados</strong><p>Esta sección solo muestra lo que registres para {field.name}. Si está vacía, aún no hay trabajos de este tipo.</p></section>}

    {registerHref && <section className="sticky-register-wrap module-register"><Link href={registerHref} className="primary action-link register-cta"><PlusIcon/> Registrar en {field.name} <ArrowIcon/></Link></section>}
  </>;
}
