'use client';

import Link from 'next/link';
import { ArrowIcon, PlusIcon } from '@/components/icons';
import { recordTypes } from '@/lib/record-types';
import { useFieldContext, withFieldQuery } from '@/lib/use-field-context';

const harvest = {
  shortLabel: 'Cosecha',
  symbol: '🫒',
  description: 'Peso, albarán y rendimiento.',
} as const;

export function RegisterHubClient() {
  const { context, ready } = useFieldContext();

  return <>
    <header className="page-title register-hub-title">
      <span className="eyebrow dark">MI CAMPO · {ready ? context.name.toUpperCase() : 'CARGANDO FINCA'}</span>
      <h1>¿Qué quieres registrar?</h1>
      <p>Elige lo que acabas de hacer. Después solo pediremos los datos necesarios.</p>
    </header>

    <section className="card register-principle">
      <PlusIcon />
      <div><strong>Una vez, en un solo sitio</strong><small>Al guardar, Mágina reutiliza el registro en historia, costes y calendario cuando corresponde.</small></div>
    </section>

    <section className="section">
      <div className="register-choice-grid">
        <Link className="card register-choice featured" href={withFieldQuery('/mi-campo/registrar/cosecha', context.id)}>
          <span className="register-choice-symbol">{harvest.symbol}</span>
          <div><strong>{harvest.shortLabel}</strong><small>{harvest.description}</small></div>
          <ArrowIcon />
        </Link>
        {recordTypes.map((type) => (
          <Link className="card register-choice" key={type.slug} href={withFieldQuery(`/mi-campo/registrar/${type.slug}`, context.id)}>
            <span className="register-choice-symbol">{type.symbol}</span>
            <div><strong>{type.shortLabel}</strong><small>{type.description}</small></div>
            <ArrowIcon />
          </Link>
        ))}
      </div>
    </section>

    <section className="section register-tip">
      <span>💡</span>
      <p><strong>Finca activa:</strong> {context.name}. Si registras un riego de 22 €, ese coste quedará asociado a esta finca y no tendrás que escribirlo otra vez.</p>
    </section>
  </>;
}
