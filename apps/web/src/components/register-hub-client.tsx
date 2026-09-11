'use client';

import Link from 'next/link';
import { ArrowIcon, PlusIcon } from '@/components/icons';
import { recordTypes } from '@/lib/record-types';
import { useFieldContext, withFieldQuery } from '@/lib/use-field-context';

const work = {
  shortLabel: 'Trabajo',
  symbol: '🧑‍🌾',
  description: 'Labor, personas, maquinaria, coste o trabajo para un cliente.',
} as const;

const harvest = {
  shortLabel: 'Entrega de cosecha',
  symbol: '🫒',
  description: 'Apunta kilos, albarán y cooperativa o almazara.',
} as const;

const harvestSteps = [
  { href: '/mi-campo/registrar/rendimiento', symbol: '📈', label: 'Rendimiento', description: 'Añade el resultado cuando llegue.' },
  { href: '/mi-campo/registrar/liquidacion', symbol: '🧾', label: 'Liquidación', description: 'Relaciona el pago devengado con sus entregas.' },
  { href: '/mi-campo/registrar/cobro', symbol: '💶', label: 'Cobro', description: 'Registra lo realmente cobrado.' },
] as const;

export function RegisterHubClient() {
  const { context, ready, found } = useFieldContext();

  if (!ready) {
    return <section className="card"><p>Preparando la finca…</p></section>;
  }

  if (!found) {
    return <section className="card"><h1>Finca no encontrada</h1><p>No hemos podido abrir la finca en la que quieres registrar esta actividad.</p><Link href="/mi-campo" className="secondary-action action-link">Volver a Mi Campo</Link></section>;
  }

  return <>
    <header className="page-title register-hub-title">
      <span className="eyebrow dark">MI CAMPO · {context.name.toUpperCase()}</span>
      <h1>¿Qué quieres registrar?</h1>
      <p>Elige lo que acabas de hacer. Después solo pediremos los datos necesarios.</p>
    </header>

    <section className="card register-principle">
      <PlusIcon />
      <div><strong>Guárdalo una vez</strong><small>Mágina reutiliza ese registro en la actividad, los costes y la campaña cuando corresponde.</small></div>
    </section>

    <section className="section">
      <div className="section-head"><div><span className="eyebrow dark">REGISTRO RÁPIDO</span><h2>Trabajo diario</h2></div></div>
      <div className="register-choice-grid">
        <Link className="card register-choice featured" href={withFieldQuery('/mi-campo/registrar/trabajo', context.id, context.source)}>
          <span className="register-choice-symbol">{work.symbol}</span>
          <div><strong>{work.shortLabel}</strong><small>{work.description}</small></div>
          <ArrowIcon />
        </Link>
        {recordTypes.map((type) => (
          <Link className="card register-choice" key={type.slug} href={withFieldQuery(`/mi-campo/registrar/${type.slug}`, context.id, context.source)}>
            <span className="register-choice-symbol">{type.symbol}</span>
            <div><strong>{type.shortLabel}</strong><small>{type.description}</small></div>
            <ArrowIcon />
          </Link>
        ))}
      </div>
    </section>

    <section className="section">
      <div className="section-head"><div><span className="eyebrow dark">COSECHA</span><h2>De la entrega al cobro</h2></div><Link href="/mi-campo/campana" className="secondary-action action-link">Ver campaña</Link></div>
      <div className="register-choice-grid">
        <Link className="card register-choice featured" href={withFieldQuery('/mi-campo/registrar/cosecha', context.id, context.source)}>
          <span className="register-choice-symbol">{harvest.symbol}</span>
          <div><strong>{harvest.shortLabel}</strong><small>{harvest.description}</small></div>
          <ArrowIcon />
        </Link>
        {harvestSteps.map((step) => <Link className="card register-choice" key={step.href} href={withFieldQuery(step.href, context.id, context.source)}>
          <span className="register-choice-symbol">{step.symbol}</span>
          <div><strong>{step.label}</strong><small>{step.description}</small></div>
          <ArrowIcon />
        </Link>)}
      </div>
      <div className="card register-principle"><div><strong>Cada momento se guarda por separado</strong><small>Entrega, rendimiento, liquidación y cobro no son lo mismo. Puedes registrar cada paso cuando ocurra, sin inventar el siguiente.</small></div></div>
    </section>

    <section className="section register-tip">
      <span>💡</span>
      <p><strong>Estás registrando en {context.name}.</strong> Usa “Trabajo” para reunir labor, personas, maquinaria y costes. En cosecha, empieza por la entrega y completa el resto cuando recibas cada dato.</p>
    </section>
  </>;
}
