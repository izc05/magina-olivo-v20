import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Mágina Olivo',
    short_name: 'Mágina',
    description: 'Territorio, personas y futuro. Gestión sencilla del olivar y guía de Sierra Mágina.',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    background_color: '#f6f2e8',
    theme_color: '#31452a',
    orientation: 'portrait',
    categories: ['agriculture', 'lifestyle', 'productivity'],
    icons: [
      {
        src: '/assets/app-icon.svg',
        sizes: 'any',
        type: 'image/svg+xml',
        purpose: 'any',
      },
      {
        src: '/assets/app-icon.svg',
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
        url: '/mi-campo/registrar/cosecha',
      },
      {
        name: 'Mi Campo',
        short_name: 'Mi Campo',
        description: 'Abrir mis fincas',
        url: '/mi-campo',
      },
      {
        name: 'Radar',
        short_name: 'Radar',
        description: 'Consultar radar y avisos de lluvia',
        url: '/radar',
      },
    ],
  };
}
