import Link from "next/link";
import type { ReactNode } from "react";
import { BrandLogo } from "@/components/layouts/BrandLogo";
import { MiNavigation } from "@/components/layouts/MiNavigation";

export function MiLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <div className="mi-shell">
      <aside aria-label="Mi Mágina Olivo" className="mi-sidebar">
        <div className="mi-sidebar-heading">
          <BrandLogo
            className="mi-brand"
            href="/mi"
            label="Mágina Olivo, inicio"
          />
          <span className="demo-label">DEMO</span>
        </div>
        <MiNavigation />
      </aside>
      <div className="mi-main">
        <header className="mi-topbar">
          <label className="mi-topbar-search">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <circle cx="11" cy="11" r="6.5" />
              <path d="m16 16 4 4" />
            </svg>
            <input
              aria-label="Buscar en Mi Mágina Olivo"
              disabled
              placeholder="Buscar fincas, parcelas, actividades, informes…"
              type="search"
            />
          </label>
          <div className="mi-topbar-context">
            <label className="mi-context-select">
              <span>Finca actual</span>
              <select aria-label="Finca actual" disabled>
                <option>Todas mis fincas</option>
              </select>
            </label>
            <label className="mi-context-select">
              <span>Campaña</span>
              <select aria-label="Campaña" disabled>
                <option>2026–2027</option>
              </select>
            </label>
            <span className="mi-topbar-state">Vista visual · Demo</span>
          </div>
          <span
            aria-label="Avisos Demo sin datos conectados"
            className="mi-notifications"
            role="img"
          >
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
            </svg>
            <span aria-hidden="true" />
          </span>
          <Link className="mi-user-link" href="/mi/perfil">
            <span aria-hidden="true" className="mi-avatar">
              D
            </span>
            <span>
              <strong>Usuario Demo</strong>
              <small>Sin cuenta conectada</small>
            </span>
          </Link>
        </header>
        <main className="mi-content">
          <p className="baseline-note mi-demo-note" role="status">
            Demo · Datos ficticios para revisión visual. No hay cuenta
            conectada.
          </p>
          {children}
        </main>
      </div>
    </div>
  );
}
