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
  shortLabel: 'Cosecha',
  symbol: '🫒',
  description: 'Peso, albarán y rendimiento.',
} as const;

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
      <div><strong>Guárdalo una vez</strong><small>Mágina reutiliza ese registro en la actividad, los costes y el calendario cuando corresponde.</small></div>
    </section>

    <section className="section">
      <div className="register-choice-grid">
        <Link className="card register-choice featured" href={withFieldQuery('/mi-campo/registrar/trabajo', context.id, context.source)}>
          <span className="register-choice-symbol">{work.symbol}</span>
          <div><strong>{work.shortLabel}</strong><small>{work.description}</small></div>
          <ArrowIcon />
        </Link>
        <Link className="card register-choice featured" href={withFieldQuery('/mi-campo/registrar/cosecha', context.id, context.source)}>
          <span className="register-choice-symbol">{harvest.symbol}</span>
          <div><strong>{harvest.shortLabel}</strong><small>{harvest.description}</small></div>
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

    <section className="section register-tip">
      <span>💡</span>
      <p><strong>Estás registrando en {context.name}.</strong> Usa “Trabajo” para reunir labor, personas, maquinaria y costes. Si solo quieres apuntar un jornal o una máquina, puedes usar su acceso rápido.</p>
    </section>
  </>;
}
