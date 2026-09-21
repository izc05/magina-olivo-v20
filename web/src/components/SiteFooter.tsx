import Link from "next/link";

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="shell footer-grid">
        <Link className="brand footer-brand" href="/" aria-label="Mágina Olivo, inicio">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-copy">
            <strong>Mágina</strong>
            <strong>Olivo</strong>
          </span>
        </Link>

        <nav className="footer-nav" aria-label="Navegación del pie">
          <Link href="/">Inicio</Link>
          <Link href="/producto">Producto</Link>
          <Link href="/beneficios">Beneficios</Link>
                     <Link href="/contacto">Contacto</Link>
        </nav>

        <p>Tecnología sencilla para quien vive del olivar.</p>
      </div>

      <div className="shell footer-legal">
        <span>© 2026 Mágina Olivo</span>
        <nav aria-label="Información legal">
          <Link href="/privacidad">Privacidad</Link>
          <Link href="/terminos">Términos</Link>
          <Link href="/aviso-legal">Aviso legal</Link>
        </nav>
      </div>
    </footer>
  );
}
