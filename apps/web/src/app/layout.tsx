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
import './visual-final.css';
import './visual-mi-campo.css';
import './visual-farm-detail.css';
import './visual-today.css';
import './visual-business.css';
import './visual-business-mobile.css';
import './visual-map-radar.css';

const DEFAULT_TITLE = 'Mágina Olivo V20';
const DEFAULT_DESCRIPTION = 'Territorio, personas y futuro. Gestión sencilla del olivar y guía de Sierra Mágina.';

type ManagedSeoSetting = {
  title?: string;
  description?: string;
  og_image?: string;
  robots_index?: boolean;
};

type PublicSettingsPayload = {
  settings?: Record<string, unknown>;
};

let managedSeoBuildPromise: Promise<ManagedSeoSetting | null> | null = null;

function validPublicImageUrl(value: string | undefined) {
  const candidate = value?.trim();
  if (!candidate) return null;
  try {
    const parsed = new URL(candidate);
    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') return null;
    return parsed.toString();
  } catch {
    return null;
  }
}

async function managedSeoForBuild() {
  if (managedSeoBuildPromise) return managedSeoBuildPromise;

  managedSeoBuildPromise = (async () => {
    const apiBase = (process.env.MAGINA_BUILD_API_URL ?? process.env.NEXT_PUBLIC_API_URL ?? '').replace(/\/$/, '');
    if (!apiBase) return null;

    try {
      const response = await fetch(`${apiBase}/api/v1/public/site-settings`, {
        signal: AbortSignal.timeout(2_500),
      });
      if (!response.ok) return null;

      const payload = await response.json() as PublicSettingsPayload;
      const value = payload.settings?.['site.seo'];
      if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
      return value as ManagedSeoSetting;
    } catch {
      return null;
    }
  })();

  return managedSeoBuildPromise;
}

export async function generateMetadata(): Promise<Metadata> {
  const seo = await managedSeoForBuild();
  const title = seo?.title?.trim() || DEFAULT_TITLE;
  const description = seo?.description?.trim() || DEFAULT_DESCRIPTION;
  const robotsIndex = seo?.robots_index !== false;
  const ogImage = validPublicImageUrl(seo?.og_image);

  return {
    title,
    description,
    applicationName: 'Mágina Olivo',
    appleWebApp: {
      capable: true,
      title: 'Mágina Olivo',
      statusBarStyle: 'black-translucent',
    },
    robots: {
      index: robotsIndex,
      follow: robotsIndex,
    },
    openGraph: {
      title,
      description,
      ...(ogImage ? { images: [{ url: ogImage }] } : {}),
    },
  };
}

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
