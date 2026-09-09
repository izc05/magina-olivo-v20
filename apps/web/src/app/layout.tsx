import type { Metadata, Viewport } from 'next';
import './globals.css';
import './premium.css';

export const metadata: Metadata = {
  title: 'Mágina Olivo V20',
  description: 'Territorio, personas y futuro. Gestión sencilla del olivar y guía de Sierra Mágina.',
  applicationName: 'Mágina Olivo',
  appleWebApp: {
    capable: true,
    title: 'Mágina Olivo',
    statusBarStyle: 'black-translucent',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  themeColor: '#31452a',
  colorScheme: 'light',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
