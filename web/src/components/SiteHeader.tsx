"use client";

import { useState } from "react";

export function SiteHeader() {
  const [open, setOpen] = useState(false);

  return (
    <header className="site-header">
      <div className="shell header-inner">
        <a className="brand" href="#inicio" aria-label="Mágina Olivo, inicio">
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
          <a href="#historia" onClick={() => setOpen(false)}>La historia</a>
          <a href="#funciones" onClick={() => setOpen(false)}>La app</a>
          <a href="#beneficios" onClick={() => setOpen(false)}>Beneficios</a>
          <a href="#territorio" onClick={() => setOpen(false)}>Nuestra tierra</a>
          <a href="#contacto" onClick={() => setOpen(false)}>Contacto</a>
          <a className="nav-cta" href="#descarga" onClick={() => setOpen(false)}>
            Descubrir la app <span aria-hidden="true">→</span>
          </a>
        </nav>
      </div>
    </header>
  );
}
