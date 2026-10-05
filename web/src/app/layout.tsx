import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

const siteUrl = process.env.SITE_URL;
const metadataBase = siteUrl
  ? new URL(siteUrl)
  : process.env.GITHUB_PAGES === "true"
    ? new URL("https://izc05.github.io/magina-olivo-v20/")
    : process.env.NODE_ENV === "development"
      ? new URL("http://localhost:3100")
      : undefined;
const indexable =
  process.env.ALLOW_INDEXING === "true" &&
  process.env.GITHUB_PAGES !== "true" &&
  Boolean(siteUrl);

export const metadata: Metadata = {
  metadataBase,
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
  openGraph: {
    type: "website",
    locale: "es_ES",
    siteName: "Mágina Olivo",
    images: ["images/v3/home-hero.webp"],
  },
  twitter: {
    card: "summary_large_image",
    images: ["images/v3/home-hero.webp"],
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
