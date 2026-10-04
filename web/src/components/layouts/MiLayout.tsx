import type { ReactNode } from "react";
import { BrandLogo } from "@/components/layouts/BrandLogo";
import { MiNavigation } from "@/components/layouts/MiNavigation";

export function MiLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <div className="mi-shell">
      <aside className="mi-sidebar">
        <BrandLogo
          className="mi-brand"
          href="/mi"
          label="Mágina Olivo, inicio"
        />
        <span className="demo-label">DEMO</span>
        <MiNavigation />
      </aside>
      <div className="mi-main">
        <header className="mi-topbar">
          <span>Mi Mágina Olivo</span>
          <span>Vista visual · sin cuenta conectada</span>
        </header>
        <main className="mi-content">
          <p className="baseline-note" role="status">
            Demo · interfaz de preparación
          </p>
          {children}
        </main>
      </div>
    </div>
  );
}
