# Web Manus Support

## Estado

Intervención realizada sobre `feat/web-manus-support`, creada desde `feat/web-magína-olivo` en `99c9410f`. `main` y la rama del flujo principal no se han modificado.

## Encontrado

La promoción vive en `web/` como una aplicación Next.js 16 con App Router. La portada ya implementa una narrativa cinematográfica campo → agricultor → móvil → producto mediante `CinematicHome` y `FieldToPhoneSequence`, con especificaciones visuales y de motion en `web/docs/` y assets fotográficos en `web/public/media/home/`.

## Implementado

- Se evita registrar listeners de `scroll` y `resize` y ejecutar frames de la secuencia cinematográfica cuando el dispositivo declara `prefers-reduced-motion: reduce`. La degradación visual existente en CSS sigue controlando la presentación accesible.
- Se añadió `web/.gitignore` para no introducir `node_modules`, `.next`, `out`, coberturas ni artefactos de TypeScript en commits futuros.

## Validación

- `npm run typecheck` — correcto.
- `npm run build` — correcto; 15 rutas generadas.
- Comprobación HTTP local — `200` en `/`, `/producto`, `/beneficios`, `/territorio`, `/contacto`, `/api/health`, `/sitemap.xml` y `/robots.txt`.
- Cabeceras `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy` presentes.
- `git diff --check` — correcto.

## Commits

- `b3ac6964 perf(web): skip cinematic scroll work for reduced motion`
- `df057c53 chore(web): ignore local Next.js artifacts`

## Pendiente

La secuencia de fotogramas dedicada `sequence-field.webp` todavía no existe en los assets; la implementación conserva el fallback visual aprobado configurado en `visualAssets.ts`. No se sustituyeron imágenes ni se alteró la composición canónica.
