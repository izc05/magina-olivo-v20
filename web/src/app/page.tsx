import { CinematicHome } from "@/components/CinematicHome";
import { SiteHeader } from "@/components/SiteHeader";

export default function HomePage() {
  return (
    <main>
      <SiteHeader />
      <CinematicHome />

      <footer className="site-footer">
        <div className="shell footer-grid">
          <a className="brand footer-brand" href="#inicio" aria-label="Mágina Olivo, inicio">
            <span className="brand-mark" aria-hidden="true" />
            <span className="brand-copy">
              <strong>Mágina</strong>
              <strong>Olivo</strong>
            </span>
          </a>

          <nav className="footer-nav" aria-label="Navegación del pie">
            <a href="#historia">La historia</a>
            <a href="#funciones">La app</a>
            <a href="#beneficios">Beneficios</a>
            <a href="#territorio">Nuestra tierra</a>
            <a href="#contacto">Contacto</a>
          </nav>

          <p>Más que olivos, nuestra tierra.</p>
        </div>

        <div className="shell footer-legal">
          <span>© 2026 Mágina Olivo</span>
          <span>Proyecto en desarrollo · Jaén</span>
        </div>
      </footer>
    </main>
  );
}
