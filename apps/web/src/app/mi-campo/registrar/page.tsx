import Link from 'next/link';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ArrowIcon, PlusIcon } from '@/components/icons';
import { lasCenillas } from '@/lib/demo-data';
import { recordTypes } from '@/lib/record-types';

const harvest = {
  slug: 'cosecha',
  shortLabel: 'Cosecha',
  symbol: '🫒',
  description: 'Peso, albarán y rendimiento.',
} as const;

export default function RegisterHubPage() {
  return (
    <main className="app-shell">
      <Topbar />
      <div className="page register-hub-page">
        <header className="page-title register-hub-title">
          <span className="eyebrow dark">MI CAMPO · {lasCenillas.name.toUpperCase()}</span>
          <h1>¿Qué quieres registrar?</h1>
          <p>Elige lo que acabas de hacer. Después solo pediremos los datos necesarios.</p>
        </header>

        <section className="card register-principle">
          <PlusIcon />
          <div>
            <strong>Una vez, en un solo sitio</strong>
            <small>Al guardar, Mágina actualizará automáticamente la historia, costes, campaña y calendario cuando corresponda.</small>
          </div>
        </section>

        <section className="section">
          <div className="register-choice-grid">
            <Link className="card register-choice featured" href="/mi-campo/registrar/cosecha">
              <span className="register-choice-symbol">{harvest.symbol}</span>
              <div><strong>{harvest.shortLabel}</strong><small>{harvest.description}</small></div>
              <ArrowIcon />
            </Link>
            {recordTypes.map((type) => (
              <Link className="card register-choice" key={type.slug} href={`/mi-campo/registrar/${type.slug}`}>
                <span className="register-choice-symbol">{type.symbol}</span>
                <div><strong>{type.shortLabel}</strong><small>{type.description}</small></div>
                <ArrowIcon />
              </Link>
            ))}
          </div>
        </section>

        <section className="section register-tip">
          <span>💡</span>
          <p><strong>Ejemplo:</strong> si registras un riego de 22 €, no tendrás que volver a escribir esos 22 € en Gastos.</p>
        </section>
      </div>
      <BottomNav active="/mi-campo" />
    </main>
  );
}
