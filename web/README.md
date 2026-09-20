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
- [ ] Fase Web 1: navegación y páginas públicas
- [ ] Fase Web 2: contenido dinámico
- [ ] Fase Web 3: integraciones de datos
- [ ] Fase Web 4: SEO, analítica y rendimiento
- [ ] Fase Web 5: despliegue de producción

Fuente de verdad: `docs/WEB-MASTER-SPEC.md`.
