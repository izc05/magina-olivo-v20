import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "Mágina Olivo",
    template: "%s · Mágina Olivo",
  },
  description:
    "Tu olivar, tus campañas y la información que importa, reunidos en una experiencia clara y cercana.",
  metadataBase: new URL("https://maginaolivo.es"),
  openGraph: {
    title: "Mágina Olivo",
    description:
      "Gestión del olivar con una experiencia sencilla, visual y conectada al territorio.",
    type: "website",
    locale: "es_ES",
    siteName: "Mágina Olivo",
  },
  twitter: {
    card: "summary_large_image",
    title: "Mágina Olivo",
    description:
      "Una forma clara y cercana de organizar tu olivar y tus campañas.",
  },
};

const websiteJsonLd = {
  "@context": "https://schema.org",
  "@type": "WebSite",
  name: "Mágina Olivo",
  url: "https://maginaolivo.es",
  inLanguage: "es",
  description:
    "Proyecto digital orientado a la gestión del olivar, con aplicación Android en desarrollo.",
};

const softwareJsonLd = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "Mágina Olivo",
  applicationCategory: "BusinessApplication",
  operatingSystem: "Android",
  url: "https://maginaolivo.es",
  description:
    "Aplicación Android en desarrollo para organizar fincas, parcelas, campañas y actividad del olivar.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="es">
      <body>
        {children}
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(websiteJsonLd) }}
        />
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(softwareJsonLd) }}
        />
      </body>
    </html>
  );
}
