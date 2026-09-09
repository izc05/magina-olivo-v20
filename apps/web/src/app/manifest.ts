import type { MetadataRoute } from 'next';

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
        src: withBase('/assets/app-icon.svg'),
        sizes: 'any',
        type: 'image/svg+xml',
        purpose: 'any',
      },
      {
        src: withBase('/assets/app-icon.svg'),
        sizes: 'any',
        type: 'image/svg+xml',
        purpose: 'maskable',
      },
    ],
    shortcuts: [
      {
        name: 'Registrar',
        short_name: 'Registrar',
        description: 'Añadir un registro a Mi Campo',
        url: withBase('/mi-campo/registrar/cosecha/'),
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
