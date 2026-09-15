# V20 Master Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganizar Mágina Olivo V20 para que el usuario distinga de inmediato ocho mundos principales en escritorio y una navegación móvil simple de cinco destinos, sin duplicar lógica ni tocar `main`.

**Architecture:** La navegación se centraliza en una única configuración compartida que define mundos, rutas y pertenencia de subrutas. `Topbar` y `BottomNav` consumen esa configuración; móvil añade un hub `/mas`. La nueva ruta `/comunidad` se monta como hub sobre fuentes existentes y queda prohibido crear un feed ficticio o un backend paralelo.

**Tech Stack:** Next.js App Router, React 19, TypeScript, CSS Modules, Playwright, pnpm 10, Node 22.

**Spec:** `docs/V20_MASTER_NAVIGATION_CODEX_SPEC.md`

## Global Constraints

- No tocar `main`.
- Trabajar desde el candidate/rama vigente que preserve el trabajo más reciente y sincronizar de forma controlada.
- No duplicar APIs, tablas, XP, saldos, stock, QR, comunidad, rutas ni autenticación.
- Mantener autoridad visual especializada de Mágina Aventura y Mi Olivo.
- Escritorio: `Inicio · Mágina Aventura · Mi Olivo · Pueblos · Almazaras · Comunidad · Mi Campo · Noticias`.
- Móvil: `Inicio · Aventura · Mi Olivo · Mi Campo · Más`.
- `Explorar` deja de ser tab principal pero conserva su ruta y funcionalidad.
- Touch targets principales >= 44 px; foco visible; teclado; contraste; safe areas; `prefers-reduced-motion`.
- Validar 360/390/430/768/1024/1280/1440/1920.

---

## Fase 0 — Preparación segura y fotografía del estado actual

**Objetivo:** congelar el punto de partida antes de modificar navegación.

**Archivos:**
- Read: `docs/V20_MASTER_NAVIGATION_CODEX_SPEC.md`
- Read: `docs/V20_UI_UX_ROADMAP_TO_RELEASE.md`
- Read: `apps/web/src/components/topbar.tsx`
- Read: `apps/web/src/components/bottom-nav.tsx`
- Read: `apps/web/src/app/layout.tsx`
- Read: `package.json`

- [ ] Confirmar HEAD de `integrate/v20-beta-closure`, `feat/v20-ui-ux-premium` y ramas especializadas verdes de Aventura/Mi Olivo/Almazaras.
- [ ] Crear worktree/rama local segura desde el HEAD elegido; no usar `main`.
- [ ] Ejecutar `pnpm check:runtime && pnpm check:env && pnpm typecheck`.
- [ ] Ejecutar smoke actual de navegación con `pnpm e2e:beta --grep "navigation|home|adventure|mi campo"` si existen coincidencias; si no, registrar que no hay cobertura específica y continuar con Fase 1.
- [ ] Guardar capturas de referencia en 390, 768, 1280 y 1440 para comparar después.

**Cierre de fase:** build base conocido y sin cambios de navegación todavía.

---

## Fase 1 — Fuente única de verdad para la navegación

**Objetivo:** evitar arrays duplicados en Topbar/BottomNav y resolver correctamente qué mundo está activo para subrutas.

**Files:**
- Create: `apps/web/src/lib/master-navigation.ts`
- Create: `e2e/master-navigation.spec.ts`
- Modify: `apps/web/src/components/topbar.tsx`
- Modify: `apps/web/src/components/bottom-nav.tsx`

**Interfaces:**
- Produce: `MASTER_WORLDS`, `MOBILE_PRIMARY_WORLDS`, `SECONDARY_DESTINATIONS`, `resolveActiveWorld(pathname)`.
- Consume: `Topbar`, `BottomNav`, futuro hub `/mas`.

- [ ] Crear primero tests Playwright que exijan los ocho labels de escritorio y cinco labels de móvil.
- [ ] Ejecutar `pnpm exec playwright test e2e/master-navigation.spec.ts` y comprobar que falla con la navegación actual.
- [ ] Crear `master-navigation.ts` con este orden canónico exacto:
  - `/` → Inicio
  - `/aventura` → Mágina Aventura
  - `/mi-olivo` → Mi Olivo
  - `/pueblos` → Pueblos
  - `/almazaras` → Almazaras
  - `/comunidad` → Comunidad
  - `/mi-campo` → Mi Campo
  - `/noticias` → Noticias
- [ ] Definir aliases de pertenencia: `/rutas` y `/explorar` activan Aventura; rutas de Mi Olivo activan Mi Olivo; subrutas de Mi Campo activan Mi Campo; detalles de pueblos activan Pueblos; detalles de almazaras activan Almazaras.
- [ ] Sustituir arrays locales de `topbar.tsx` y `bottom-nav.tsx` por la configuración compartida.
- [ ] Ejecutar test específico y `pnpm typecheck`.
- [ ] Commit: `feat(nav): centralize V20 master navigation`.

**Cierre de fase:** una sola configuración decide orden, etiquetas y estado activo.

---

## Fase 2 — Cabecera premium de escritorio con los 8 mundos

**Objetivo:** hacer visibles los ocho mundos sin que la cabecera se aplaste entre 1024 y 1920 px.

**Files:**
- Modify: `apps/web/src/components/topbar.tsx`
- Modify: `apps/web/src/components/topbar.module.css`
- Modify: `e2e/master-navigation.spec.ts`

- [ ] Añadir test a 1024, 1280, 1440 y 1920 que compruebe que los ocho enlaces son accesibles y no generan overflow horizontal.
- [ ] Convertir la cabecera desktop en composición de dos zonas: marca/acciones y franja de mundos. Mantener sticky y evitar ocultar contenido.
- [ ] Mantener notificaciones y perfil fuera de los ocho mundos.
- [ ] Marcar la sección activa con `aria-current="page"` y tratamiento visual inequívoco.
- [ ] Hacer que el foco por teclado sea visible en todos los enlaces.
- [ ] Ejecutar `pnpm exec playwright test e2e/master-navigation.spec.ts --project=chromium`.
- [ ] Commit: `feat(nav): add eight-world desktop header`.

**Cierre de fase:** en escritorio se entiende la plataforma sin abrir menús secundarios.

---

## Fase 3 — Navegación móvil de cinco destinos

**Objetivo:** reducir la carga visual en móvil conservando acceso inmediato a los cuatro mundos de uso más frecuente.

**Files:**
- Modify: `apps/web/src/components/bottom-nav.tsx`
- Modify: `apps/web/src/components/bottom-nav.module.css`
- Modify: `apps/web/src/components/icons.tsx` solo si falta un icono ya existente reutilizable.
- Modify: `e2e/master-navigation.spec.ts`

- [ ] Añadir test 360/390/430 para `Inicio · Aventura · Mi Olivo · Mi Campo · Más`.
- [ ] Cambiar grid de 4 a 5 columnas sin bajar ningún target principal de 44 px.
- [ ] Usar rutas `/`, `/aventura`, `/mi-olivo`, `/mi-campo`, `/mas`.
- [ ] Comprobar safe area inferior y teclado abierto.
- [ ] Verificar que subrutas de Aventura/Mi Olivo/Mi Campo mantienen activo el mundo correcto.
- [ ] Ejecutar Playwright específico en los tres anchos móviles.
- [ ] Commit: `feat(nav): simplify mobile navigation to five destinations`.

**Cierre de fase:** móvil queda simple y consistente, sin convertir ocho módulos en ocho botones inferiores.

---

## Fase 4 — Hub “Más” para destinos secundarios

**Objetivo:** dar un hogar ordenado a los destinos que no son tabs principales.

**Files:**
- Create: `apps/web/src/app/mas/page.tsx`
- Create: `apps/web/src/app/mas/mas.module.css`
- Create: `apps/web/src/components/more-destinations.tsx`
- Modify: `e2e/master-navigation.spec.ts`

**Destinos mínimos:** Pueblos, Almazaras, Comunidad, Noticias, Empresas, Experiencias, Mágina Pass, Radar/Tiempo, Eventos, Mercado, Servicios/Herramientas, Perfil, Ayuda; Admin solo si el shell actual ya expone autorización para ese usuario.

- [ ] Escribir test que pulse “Más”, llegue a `/mas` y vea los destinos secundarios.
- [ ] Crear grid/tarjetas de destinos usando `SECONDARY_DESTINATIONS`.
- [ ] No duplicar contenido de esos módulos: cada tarjeta navega a su ruta real.
- [ ] Mantener jerarquía visual: primero los cuatro mundos secundarios (Pueblos, Almazaras, Comunidad, Noticias), después servicios/contexto.
- [ ] Ejecutar Playwright + typecheck.
- [ ] Commit: `feat(nav): add mobile more hub`.

**Cierre de fase:** ningún módulo importante queda escondido, pero la barra móvil sigue limpia.

---

## Fase 5 — Auditoría de Comunidad antes de construir el hub

**Objetivo:** garantizar que `/comunidad` reutilice fuentes reales y no nazca como backend paralelo.

**Files:**
- Create: `docs/COMMUNITY_SOURCE_MAP.md`

- [ ] Buscar en la rama/candidate vigente los componentes, endpoints y queries que ya entregan reseñas aprobadas, fotos moderadas, actividad y avisos de rutas.
- [ ] Documentar en `COMMUNITY_SOURCE_MAP.md` para cada fuente: ruta/componente consumidor actual, endpoint/query, estado de moderación y si puede reutilizarse desde `/comunidad` sin cambiar contrato.
- [ ] Si una fuente requiere nueva lógica de agregación backend, marcarla como fuera de este plan y no simularla en frontend.
- [ ] Commit: `docs(community): map reusable community sources`.

**Cierre de fase:** existe un mapa concreto de datos reales antes de diseñar el hub.

---

## Fase 6 — Crear `/comunidad` sin datos ficticios

**Objetivo:** hacer de Comunidad un mundo visible aunque la agregación global se limite a fuentes ya reutilizables.

**Files:**
- Create: `apps/web/src/app/comunidad/page.tsx`
- Create: `apps/web/src/app/comunidad/comunidad.module.css`
- Create: `apps/web/src/components/community-hub.tsx`
- Modify: `e2e/master-navigation.spec.ts`

- [ ] Leer `docs/COMMUNITY_SOURCE_MAP.md` y usar únicamente fuentes marcadas como reutilizables sin contrato nuevo.
- [ ] Mostrar estados reales de loading/empty/error; nunca inventar actividad para rellenar tarjetas.
- [ ] Para bloques cuya agregación no sea reutilizable aún, mostrar acceso a la superficie real existente (por ejemplo comunidad dentro de Rutas) en vez de datos falsos.
- [ ] Añadir smoke E2E de `/comunidad` y de estado vacío.
- [ ] Ejecutar Playwright + typecheck.
- [ ] Commit: `feat(community): add real-data community hub`.

**Cierre de fase:** Comunidad ya existe como mundo principal sin crear otra red social dentro de V20.

---

## Fase 7 — Integrar los mundos y rutas legacy con el estado activo correcto

**Objetivo:** que el usuario siempre sepa en qué mundo está aunque navegue a una subruta técnica.

**Files:**
- Modify: `apps/web/src/lib/master-navigation.ts`
- Modify: páginas/shells afectados únicamente si todavía no usan `Topbar`/`BottomNav` comunes.
- Modify: `e2e/master-navigation.spec.ts`

- [ ] Probar al menos: `/`, `/aventura`, `/rutas`, `/explorar`, `/mi-olivo`, `/pueblos`, `/almazaras`, `/comunidad`, `/mi-campo`, `/noticias`, `/empresas`, `/experiencias`, `/magina-pass`, `/radar`, `/perfil`.
- [ ] Verificar que `/explorar` y `/rutas` no reaparecen como mundos principales.
- [ ] Verificar que rutas legacy siguen respondiendo y no se borran por reorganizar navegación.
- [ ] Corregir únicamente shells inconsistentes; no rediseñar módulos especializados en esta fase.
- [ ] Commit: `fix(nav): align legacy routes with master worlds`.

**Cierre de fase:** navegación coherente de extremo a extremo.

---

## Fase 8 — Portada Inicio como mapa visual de los mundos

**Objetivo:** reforzar la misma arquitectura también dentro de Inicio.

**Files:**
- Modify: `apps/web/src/app/page.tsx` o el componente de Home que realmente componga la portada en el HEAD vigente.
- Create/Modify: componente de accesos de Home siguiendo patrones existentes.
- Modify: `e2e/home-hoy.spec.ts`

- [ ] Añadir una sección visual que permita reconocer y entrar a los ocho mundos sin duplicar su contenido interno.
- [ ] Mantener Tiempo/Alertas/actualidad existentes y no convertir Inicio en un mega-dashboard saturado.
- [ ] Verificar lectura en 390 y composición propia en 1280/1440.
- [ ] Ejecutar `pnpm exec playwright test e2e/home-hoy.spec.ts e2e/master-navigation.spec.ts`.
- [ ] Commit: `feat(home): expose V20 worlds from the home page`.

**Cierre de fase:** cabecera e Inicio cuentan la misma arquitectura de producto.

---

## Fase 9 — QA completo, comparación visual y sincronización

**Objetivo:** cerrar navegación solo cuando funcione y se vea bien en todos los tamaños.

**Files:**
- Modify: `e2e/accessibility-smoke.spec.ts` si necesita registrar las nuevas superficies.
- Modify: `e2e/master-navigation.spec.ts` con la matriz final.
- Update: `docs/V20_MASTER_NAVIGATION_CODEX_SPEC.md` solo para registrar estado final, sin cambiar la decisión de producto.

- [ ] `pnpm check:runtime`
- [ ] `pnpm check:env`
- [ ] `pnpm check:staging`
- [ ] `pnpm typecheck`
- [ ] `pnpm build`
- [ ] `pnpm exec playwright test e2e/master-navigation.spec.ts e2e/accessibility-smoke.spec.ts`
- [ ] `pnpm e2e:beta` para la matriz completa si el entorno local dispone de sus dependencias.
- [ ] QA visual 360/390/430/768/1024/1280/1440/1920.
- [ ] Comparar capturas con Fase 0: overflow, solapes, sticky header, safe areas y jerarquía.
- [ ] Resincronizar de forma controlada con `integrate/v20-beta-closure` vigente; no force-push destructivo y no tocar `main`.
- [ ] Confirmar CI verde sobre el mismo HEAD: Full Candidate, Browser E2E, Responsive/visual QA, Environment, Staging y workflows específicos afectados.
- [ ] Commit final de documentación/QA si procede.

**Definition of Done:** al abrir V20, un usuario distingue de inmediato los ocho mundos en escritorio; en móvil navega con cinco destinos claros y encuentra el resto en Más; las rutas legacy siguen funcionando; Comunidad no usa datos ficticios; no existe overflow horizontal; y el mismo HEAD pasa build, typecheck y la matriz E2E aplicable.
