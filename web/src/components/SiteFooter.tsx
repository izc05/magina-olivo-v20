import Image from "next/image";
import Link from "next/link";

export function SiteFooter() {
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";

  return (
    <footer className="site-footer">
      <div className="shell footer-grid">
        <Link className="brand footer-brand footer-brand-v2" href="/" aria-label="Mágina Olivo, inicio">
          <Image
            src={`${basePath}/brand/v2-lockup.svg`}
            alt="Mágina Olivo"
            width={250}
            height={67}
          />
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
