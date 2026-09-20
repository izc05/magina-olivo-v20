import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";

export default function NotFound() {
  return (
    <>
      <a className="skip-link" href="#contenido-principal">Saltar al contenido</a>
      <SiteHeader />
      <main id="contenido-principal">
      <section className="not-found">
        <div className="shell not-found-inner">
          <p className="eyebrow">404 · Fuera de la parcela</p>
          <h1>Este camino no lleva a ningún olivar.</h1>
          <p>
            La página que buscas no existe o ha cambiado de lugar.
          </p>
          <a className="button button-primary" href="/">Volver al inicio →</a>
        </div>
      </section>
      </main>
      <SiteFooter />
    </>
  );
}
