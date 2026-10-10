# Mágina Olivo · Web V3 baseline

Baseline independiente para la web pública y la zona visual de preparación `/mi`.
La Home visual completa corresponde a #389; las pantallas `/mi` completas, a #391.
La dirección visual aprobada y vinculante está en el issue #394.

## Requisitos

- Node.js 22 (`.nvmrc`)
- npm con `npm ci`

## Desarrollo y verificación

```bash
npm ci
npm run dev
npm run lint
npm run typecheck
npm run build
npx playwright install chromium
npm run test:e2e
```

## Salidas

- `npm run build`: servidor Next.js `standalone` para Docker.
- `npm run build:pages`: export estático con base path `/magina-olivo-v20` para GitHub Pages.
- Preview: noindex por defecto; la indexación requiere configuración explícita posterior.
- `GET /api/health`: responde únicamente `{ "status": "ok" }`.

Las rutas dinámicas de detalle solo prerenderizan el identificador `demo`. Todas las
páginas `/mi` indican que son una interfaz de preparación y no muestran datos reales.
No se conecta backend, Auth, Sync ni una API agrícola.

## Docker

```bash
docker build -t magina-olivo-web-v3 .
docker run --rm -p 3000:3000 magina-olivo-web-v3
```

La imagen final sirve Next.js en modo standalone y tiene healthcheck en `/api/health`.
