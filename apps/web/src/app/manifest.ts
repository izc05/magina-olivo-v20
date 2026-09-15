import type { MetadataRoute } from 'next';

export const dynamic = 'force-static';

export default function manifest(): MetadataRoute.Manifest {
  const basePath = process.env.GITHUB_PAGES === 'true' ? '/magina-olivo-v20' : '';
  const withBase = (path: string) => `${basePath}${path}`;

  return {
    name: 'Mágina Olivo',
    short_name: 'Mágina',
    description: 'Territorio, personas y futuro. Gestión sencilla del olivar y guía de Sierra Mágina.',
    start_url: withBase('/'),
    scope: withBase('/'),
    display: 'standalone',
    background_color: '#f6f2e8',
    theme_color: '#31452a',
    orientation: 'portrait',
    categories: ['agriculture', 'lifestyle', 'productivity'],
    icons: [
      {
        src: withBase('/assets/brand/app-icon-192.png'),
        sizes: '192x192',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: withBase('/assets/brand/app-icon-512.png'),
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable',
      },
    ],
    shortcuts: [
      {
        name: 'Registrar',
        short_name: 'Registrar',
        description: 'Añadir un registro a Mi Campo',
        url: withBase('/mi-campo/registrar/'),
      },
      {
        name: 'Mi Campo',
        short_name: 'Mi Campo',
        description: 'Abrir mis fincas',
        url: withBase('/mi-campo/'),
      },
      {
        name: 'Radar',
        short_name: 'Radar',
        description: 'Consultar radar y avisos de lluvia',
        url: withBase('/radar/'),
      },
    ],
  };
}
