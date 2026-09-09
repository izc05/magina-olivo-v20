'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import type { FieldRecord } from '@/lib/domain';
import { getLocalActivities, getLocalFields } from '@/lib/local-prototype-store';
import { ArrowIcon, MapPinIcon, PlusIcon, SproutIcon } from '@/components/icons';

const modules = [
  ['🫒','Cosecha','Producción e histórico','cosechas'],
  ['💧','Riegos','Próximo e histórico','riegos'],
  ['🌿','Tratamientos','Productos y aplicaciones','tratamientos'],
  ['🧪','Abonos','Productos y cantidades','abonos'],
  ['✂','Poda','Trabajos e histórico','poda'],
  ['€','Gastos','Costes de la finca','gastos'],
  ['📅','Calendario','Próximos trabajos','calendario'],
  ['◷','Historia','Todo lo realizado','historia'],
] as const;

export function LocalFieldPage() {
  const [field, setField] = useState<FieldRecord | null | undefined>(undefined);
  const [activityCount, setActivityCount] = useState(0);
  const [cost, setCost] = useState(0);
  const [nextDate, setNextDate] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');
    if (!id) {
      setField(null);
      return;
    }
    const found = getLocalFields().find((item) => item.id === id) ?? null;
    setField(found);
    if (found) {
      const activities = getLocalActivities(found.id);
      setActivityCount(activities.length);
      setCost(activities.reduce((sum, item) => sum + (item.costEur ?? 0), 0));
      const next = activities
        .filter((item) => item.followUpOn)
        .sort((a, b) => String(a.followUpOn).localeCompare(String(b.followUpOn)))[0];
      setNextDate(next?.followUpOn ?? null);
    }
  }, []);

  const fieldQuery = useMemo(() => field ? `fieldId=${encodeURIComponent(field.id)}` : '', [field]);

  if (field === undefined) {
    return <section className="card local-field-state"><strong>Cargando finca…</strong></section>;
  }

  if (!field) {
    return <section className="card local-field-state"><strong>No encuentro esta finca en este dispositivo.</strong><p>Puede haberse borrado el almacenamiento local del navegador.</p><Link href="/mi-campo">Volver a Mi Campo</Link></section>;
  }

  return <>
    <section className="local-field-hero">
      <div className="local-field-hero-copy">
        <span className="eyebrow">FINCA LOCAL · PROTOTIPO</span>
        <h1>{field.name}</h1>
        <p><MapPinIcon/> {field.municipality ?? 'Municipio pendiente'}</p>
      </div>
    </section>

    <section className="card farm-identity-card local-field-identity">
      <div className="farm-avatar"><SproutIcon/></div>
      <div className="farm-identity-copy"><h2>{field.name}</h2><p>Ficha creada en este dispositivo.</p></div>
      <div className="farm-identity-facts"><span><b>{field.oliveTrees ?? '—'}</b> olivas</span><span><b>{field.waterRegime ?? '—'}</b> régimen</span></div>
    </section>

    <div className="kpi-grid living-kpis local-field-kpis">
      <div className="card kpi kpi-green"><b>{activityCount}</b><span>registros locales</span><small>Historia propia</small></div>
      <div className="card kpi kpi-gold"><b>{cost.toLocaleString('es-ES')} €</b><span>costes registrados</span><small>Sin duplicar</small></div>
      <div className="card kpi kpi-blue"><b>{nextDate ?? '—'}</b><span>próximo aviso</span><small>Calendario local</small></div>
    </div>

    <section className="section">
      <div className="module-grid living-modules">
        {modules.map(([icon,label,summary,view]) => <Link href={`/mi-campo/fincas/local/modulo?${fieldQuery}&view=${view}`} className="card module living-module" key={view}><span className="module-symbol">{icon}</span><div><strong>{label}</strong><small>{summary}</small></div><ArrowIcon/></Link>)}
      </div>
    </section>

    <section className="card local-field-empty-note"><strong>Esta finca empieza vacía</strong><p>A diferencia de Las Cenillas, aquí no inventamos históricos. Lo que aparezca será lo que registres en este navegador.</p></section>

    <section className="sticky-register-wrap"><Link href={`/mi-campo/registrar?${fieldQuery}`} className="primary action-link register-cta"><PlusIcon/> Registrar en {field.name} <ArrowIcon/></Link></section>
  </>;
}
