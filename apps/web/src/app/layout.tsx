import { Suspense, type CSSProperties } from 'react';
import type { Metadata, Viewport } from 'next';
import { AuthProvider } from '../components/auth-provider';
import { ManagedSeoMetadata } from '@/components/managed-seo-metadata';
import { MiOlivoActivityTracker } from '@/components/mi-olivo-activity-tracker';
import 'maplibre-gl/dist/maplibre-gl.css';
import './globals.css';
import './premium.css';
import './pwa.css';
import './assets.css';
import './record.css';
import './new-farm.css';
import './farm-modules.css';
import './calendar.css';
import './local-prototype.css';
import './map-platform.css';
import './radar-alerts.css';
import './mobile-hardening.css';
import './managed-content.css';

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
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';
  const assetStyles = {
    '--asset-hero-huelma': `url("${basePath}/assets/hero-huelma-concept.svg")`,
    '--asset-farm-las-cenillas': `url("${basePath}/assets/farm-las-cenillas-concept.svg")`,
    '--asset-delivery-ticket': `url("${basePath}/assets/delivery-ticket-concept.svg")`,
    '--asset-olive-sprig': `url("${basePath}/assets/olive-sprig.svg")`,
  } as CSSProperties;

  return (
    <html lang="es">
      <head>
        <ManagedSeoMetadata />
      </head>
      <body style={assetStyles}>
        <AuthProvider>
          <Suspense fallback={null}>
            <MiOlivoActivityTracker />
          </Suspense>
          {children}
        </AuthProvider>
      </body>
    </html>
  );
}
