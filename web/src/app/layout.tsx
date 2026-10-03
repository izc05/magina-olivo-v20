import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

const siteUrl = process.env.SITE_URL;
const indexable =
  process.env.ALLOW_INDEXING === "true" &&
  process.env.GITHUB_PAGES !== "true" &&
  Boolean(siteUrl);

export const metadata: Metadata = {
  metadataBase: siteUrl ? new URL(siteUrl) : undefined,
  title: {
    default: "Mágina Olivo",
    template: "%s | Mágina Olivo",
  },
  description:
    "Gestión clara del olivar: fincas, campañas, cuaderno, tiempo y mercado.",
  robots: {
    index: indexable,
    follow: indexable,
    googleBot: { index: indexable, follow: indexable },
  },
};

export default function RootLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
