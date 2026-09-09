import type { Metadata } from 'next';
import './globals.css';
import './premium.css';

export const metadata: Metadata = {
  title: 'Mágina Olivo V20',
  description: 'Territorio, personas y futuro. Gestión sencilla del olivar y guía de Sierra Mágina.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
