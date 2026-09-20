"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";

const links = [
  ["/", "Inicio"],
  ["/producto", "Producto"],
  ["/beneficios", "Beneficios"],
  ["/territorio", "Nuestra tierra"],
  ["/contacto", "Contacto"],
] as const;

export function SiteHeader() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () =>
      setScrolled(window.scrollY > Math.min(420, window.innerHeight * 0.52));

    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);

    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, []);

  useEffect(() => setOpen(false), [pathname]);

  const solid = pathname !== "/" || scrolled || open;

  return (
    <header className={`site-header ${solid ? "header-solid" : "header-overlay"}`}>
      <div className="shell header-inner">
        <a className="brand" href="/" aria-label="Mágina Olivo, inicio">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-copy">
            <strong>Mágina</strong>
            <strong>Olivo</strong>
          </span>
        </a>

        <button
          className="menu-toggle"
          type="button"
          aria-expanded={open}
          aria-controls="main-navigation"
          aria-label={open ? "Cerrar menú" : "Abrir menú"}
          onClick={() => setOpen((value) => !value)}
        >
          <span />
          <span />
        </button>

        <nav
          id="main-navigation"
          className={`nav ${open ? "nav-open" : ""}`}
          aria-label="Navegación principal"
        >
          {links.map(([href, label]) => (
            <a
              key={href}
              href={href}
              aria-current={pathname === href ? "page" : undefined}
              onClick={() => setOpen(false)}
            >
              {label}
            </a>
          ))}
          <a className="nav-cta" href="/#descarga" onClick={() => setOpen(false)}>
            Descubrir la app <span aria-hidden="true">→</span>
          </a>
        </nav>
      </div>
    </header>
  );
}
