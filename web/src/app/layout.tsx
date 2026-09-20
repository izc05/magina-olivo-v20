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
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
