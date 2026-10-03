import Link from "next/link";
import type { ReactNode } from "react";
import { MiNavigation } from "@/components/layouts/MiNavigation";

export function MiLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <div className="mi-shell">
      <aside className="mi-sidebar">
        <Link className="brand mi-brand" href="/mi">
          Mágina Olivo
        </Link>
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
