"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { miNavigation } from "@/lib/navigation";

function NavigationItems() {
  const pathname = usePathname();

  return (
    <div className="mi-nav">
      {miNavigation.map(({ href, label }) => {
        const active =
          href !== null &&
          (pathname === href ||
            (href !== "/mi" && pathname.startsWith(`${href}/`)));
        return href ? (
          <Link
            aria-current={active ? "page" : undefined}
            href={href}
            key={label}
          >
            {label}
          </Link>
        ) : (
          <span aria-disabled="true" className="nav-pending" key={label}>
            {label}
          </span>
        );
      })}
    </div>
  );
}

export function MiNavigation() {
  return (
    <>
      <nav className="mi-nav" aria-label="Navegación Mi Mágina Olivo">
        <NavigationItems />
      </nav>
      <details className="mobile-nav">
        <summary>Menú · Mi Mágina Olivo</summary>
        <nav aria-label="Navegación Mi Mágina Olivo">
          <NavigationItems />
        </nav>
      </details>
    </>
  );
}
