# Mágina Olivo · Web

Superficie web independiente dentro del repositorio `magina-olivo-v20`.

## Regla de aislamiento

La web vive en `/web` y **no modifica ni sustituye** la baseline Android RC1.2. La aplicación Android sigue su propia secuencia de Gates.

## Stack

- Next.js 16.3.3
- React 19.3
- TypeScript 6
- Tailwind CSS 4.3
- App Router

## Desarrollo local

```bash
cd web
npm install
npm run dev
```

Validación:

```bash
npm run typecheck
npm run build
```

## Estado

- [x] Fase Web 0: arquitectura aislada
- [x] Sistema visual inicial
- [x] Primera portada responsive
- [x] Fase Web 1: navegación y páginas públicas
- [ ] Fase Web 2: contenido dinámico
- [ ] Fase Web 3: integraciones de datos
- [x] Fase Web 4: SEO técnico base, accesibilidad y rendimiento inicial
- [ ] Fase Web 5: despliegue de producción

Fuente de verdad: `docs/WEB-MASTER-SPEC.md`.


## Staging con Docker

La web genera salida standalone de Next.js y dispone de una imagen Docker multi-stage.

```bash
cd web
docker compose -f docker-compose.staging.yml up -d --build
```

Healthcheck:

```bash
curl http://127.0.0.1:3000/api/health
```

Guía completa: `docs/STAGING.md`.

## Estado visual

- Home cinematográfica con scroll por secuencia.
- Fotografía aprobada integrada.
- Páginas Producto, Beneficios, Territorio y Contacto terminadas en primera versión pública.
- Responsive móvil refinado.
- Navegación por teclado y reduced-motion contemplados.
- Páginas legales y 404 incluidas.
- CI valida TypeScript, build Next y build Docker.

La publicación definitiva sigue bloqueada hasta revisión visual en navegador real y definición del dominio/canonical de producción.
