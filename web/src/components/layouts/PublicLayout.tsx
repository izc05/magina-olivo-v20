import Link from "next/link";
import type { ReactNode } from "react";
import { BrandLogo } from "@/components/layouts/BrandLogo";
import { publicNavigation } from "@/lib/navigation";

export function PublicLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <>
      <a className="skip-link" href="#contenido">
        Saltar al contenido
      </a>
      <header className="public-header">
        <BrandLogo href="/" label="Mágina Olivo, inicio" />
        <nav className="public-nav" aria-label="Navegación principal">
          {publicNavigation.map(({ href, label }) => (
            <Link href={href} key={label}>
              {label}
            </Link>
          ))}
        </nav>
        <div className="header-actions">
          <Link href="/mi">Entrar</Link>
          <Link className="button-link" href="/descargar">
            Descargar Android
          </Link>
        </div>
      </header>
      <div id="contenido" className="public-main" tabIndex={-1}>
        {children}
      </div>
      <footer className="public-footer">
        <BrandLogo href="/" label="Mágina Olivo, inicio" />
        <nav aria-label="Información legal">
          <Link href="/privacidad">Privacidad</Link> ·{" "}
          <Link href="/terminos">Términos</Link> ·{" "}
          <Link href="/aviso-legal">Aviso legal</Link>
        </nav>
      </footer>
    </>
  );
}
