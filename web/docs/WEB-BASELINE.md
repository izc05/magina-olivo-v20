# WEB-0B · Baseline

## Estructura

- `src/app/(public)`: rutas públicas y `PublicLayout`.
- `src/app/(mi)/mi`: rutas visuales `/mi` y layout `MiLayout`.
- `src/components/layouts`: navegación pública y shell privado.
- `src/features/scaffold`: páginas mínimas compartidas para las rutas aún sin UI.
- `src/data/mock`: reservado para fixtures locales explícitos; vacío de datos en WEB-0B.
- `src/lib`: navegación y contratos pequeños de la baseline.
- `src/styles`: tokens crema/olivo y CSS común.
- `tests`: smoke Playwright responsive.

## Modos de build

- Normal: `output: standalone`, usado por Docker.
- GitHub Pages: `GITHUB_PAGES=true`, `output: export`, `basePath` del repositorio y robots noindex.
- Las rutas dinámicas `/mi/fincas/[id]`, `/mi/parcelas/[id]` y `/mi/campanas/[id]` exportan solo `/demo`.
- La ruta GET `/api/health` es estática para el export de Pages y responde en el runtime standalone.

El indexado está desactivado por defecto. Ninguna fase debe activarlo hasta que se definan
dominio canónico y contenido público final.

## Alcance aplazado

- #389 implementa la Home conforme a #394, con fotografía premium aprobada y hero cinematográfico.
- #390 completa páginas y contenido públicos.
- #391 implementa el dashboard/shell y superficies privadas con fixtures claramente marcados Demo.
- Auth, datos productivos, RLS y Sync quedan fuera de WEB-0.
