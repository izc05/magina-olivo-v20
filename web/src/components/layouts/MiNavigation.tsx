"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { miNavigationGroups } from "@/lib/navigation";

type IconName =
  | "home"
  | "farm"
  | "parcel"
  | "map"
  | "notebook"
  | "campaign"
  | "document"
  | "report"
  | "weather"
  | "radar"
  | "market"
  | "cooperative"
  | "news"
  | "alerts"
  | "business"
  | "profile"
  | "account";

const iconPaths: Record<IconName, string> = {
  home: "M3 10.5 12 3l9 7.5M5.5 9v11h13V9M9 20v-6h6v6",
  farm: "M3 20V9l9-6 9 6v11M7 20v-6h10v6M8 10h.01M12 10h.01M16 10h.01",
  parcel: "M12 3 21 8v9l-9 4-9-4V8l9-5ZM3.5 8.5 12 13l8.5-4.5M12 13v8",
  map: "M9 18 3 21V6l6-3 6 3 6-3v15l-6 3-6-3Zm0 0V3m6 18V6",
  notebook: "M6 3h12v18H6zM9 7h6M9 11h6M9 15h4M3 7h3M3 12h3M3 17h3",
  campaign: "M4 5h16v16H4zM8 3v4m8-4v4M4 10h16m-11 4h3m-3 3h6",
  document: "M6 3h8l5 5v13H6zM14 3v5h5M9 13h7m-7 4h7",
  report: "M4 20h16M6 17V9m6 8V4m6 13v-6M4 7l5-3 5 2 6-3",
  weather:
    "M7 18h10a4 4 0 0 0 .2-8A6 6 0 0 0 6 8a5 5 0 0 0 1 10Zm2 3-1 2m6-2-1 2m6-2-1 2",
  radar: "M12 3v9l6.4 6.4M12 12l7.8-4.5M21 12a9 9 0 1 1-9-9",
  market: "M4 18 9 13l4 3 7-8m0 0h-5m5 0v5M4 21h16",
  cooperative: "M3 20h18M5 20V9l7-5 7 5v11M9 20v-6h6v6M9 10h.01M15 10h.01",
  news: "M4 5h16v15H4zM8 9h8M8 13h8M8 17h5",
  alerts: "M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9m-8 12h4",
  business: "M3 20h18M5 20V8h14v12M9 8V4h6v4M8 12h2m4 0h2m-8 4h2m4 0h2",
  profile: "M20 21a8 8 0 0 0-16 0m8-10a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z",
  account: "M4 5h16v14H4zM8 9h8m-8 4h5m-5 3h8",
};

function MiIcon({ name }: { name: IconName }) {
  return (
    <svg
      aria-hidden="true"
      className="mi-nav-icon"
      fill="none"
      viewBox="0 0 24 24"
    >
      <path
        d={iconPaths[name]}
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.7"
      />
    </svg>
  );
}

function NavigationContents() {
  const pathname = usePathname();

  return (
    <div className="mi-navigation-groups">
      {miNavigationGroups.map((group) => (
        <section className="mi-navigation-group" key={group.label ?? "home"}>
          {group.label ? <h2>{group.label}</h2> : null}
          <div className="mi-nav">
            {group.items.map(({ href, icon, label }) => {
              const active =
                href !== null &&
                (pathname === href ||
                  (href !== "/mi" && pathname.startsWith(`${href}/`)));
              return href ? (
                <Link
                  aria-current={active ? "page" : undefined}
                  className="mi-nav-link"
                  href={href}
                  key={label}
                >
                  <MiIcon name={icon} />
                  <span>{label}</span>
                </Link>
              ) : (
                <span className="mi-nav-link nav-pending" key={label}>
                  <MiIcon name={icon} />
                  <span>{label}</span>
                  <small>Sin datos</small>
                </span>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
}

export function MiNavigation() {
  return (
    <>
      <nav
        aria-label="Navegación Mi Mágina Olivo"
        className="mi-desktop-navigation"
      >
        <NavigationContents />
      </nav>
      <details className="mobile-nav">
        <summary>Menú · Mi Mágina Olivo</summary>
        <nav aria-label="Navegación Mi Mágina Olivo">
          <NavigationContents />
        </nav>
      </details>
    </>
  );
}
