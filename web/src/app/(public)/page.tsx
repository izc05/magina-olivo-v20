import Link from "next/link";

export default function HomePage() {
  return (
    <main className="page-wrap home-hero">
      <p className="page-eyebrow">Mágina Olivo · Web V3</p>
      <h1 className="page-title">Tu olivar, claro y al día.</h1>
      <p className="page-description">
        Gestión de fincas, campañas, pesadas, jornales, gastos, tiempo y
        mercado.
      </p>
      <div className="home-actions">
        <Link className="button-link" href="/descargar">
          Descargar Android
        </Link>
        <Link className="button-secondary" href="/funciones">
          Conocer la app
        </Link>
      </div>
      <p className="baseline-note">
        Base técnica V3 · experiencia visual en preparación.
      </p>
      <span id="territorio" aria-hidden="true" />
    </main>
  );
}
