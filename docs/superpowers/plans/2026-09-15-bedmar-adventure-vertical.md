# Bedmar Mágina Aventura Vertical Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Cerrar un vertical real de senderismo gamificado en Bedmar: ruta validada → preparación → GPS/track → checkpoints/retos → finalización → progreso consumible por Mi Olivo.

**Architecture:** Reutilizar el motor actual de rutas, PostGIS, `RouteActivityRecorder`, `RouteAdventurePanel` y `RouteMap`. No crear un segundo sistema GPS. El `watchPosition` de la grabación será la fuente de telemetría en vivo para la pantalla de aventura; el servidor seguirá siendo autoridad para checkpoints, finalización y recompensas. La primera versión es PWA foreground; no se prometerá GPS continuo con pantalla apagada.

**Tech Stack:** Next.js 16, React 19, TypeScript 6, MapLibre GL 6.8, Fastify 5, Kysely, PostgreSQL/PostGIS, Zod, Playwright, Node 22, pnpm 10.15.1.

**Spec:** `docs/v20/V20_CORE_AUDIT_2026-09-15.md`, `docs/v20/V20_PRODUCT_PRIORITIES.md`, `docs/v20/V20_IMPLEMENTATION_ROADMAP.md`.

## Global Constraints

- Base técnica: `integrate/v20-beta-closure`.
- Crear una rama fresca `feat/v20-bedmar-adventure-vertical`; no desarrollar sobre `main` ni mergear ramas antiguas completas.
- Usar `feat/v20-routes-adventure-premium-mobile` y `feat/v20-routes-adventure-live-telemetry-v3` solo como fuentes de deltas útiles.
- No inventar track, distancia, desnivel, checkpoint ni coordenadas de Bedmar: usar datos reales/validados existentes o cargarlos mediante Admin antes de prueba física.
- Seguridad y track validado tienen prioridad sobre gamificación.
- Una sola fuente GPS foreground por actividad.
- Premios/XP definitivos se conceden en servidor e idempotentemente.
- Cada tarea debe dejar tests verdes antes de continuar.

---

## Task 1: Congelar el contrato E2E del vertical Bedmar

**Files:**
- Modify: `e2e/magina-adventure.spec.ts`
- Reference: `apps/web/src/app/aventura/en-curso/adventure-live-client.tsx`
- Reference: `apps/web/src/app/rutas/detalle/route-activity-recorder.tsx`
- Reference: `apps/web/src/app/rutas/detalle/route-adventure-panel.tsx`

**Step 1: Write failing tests**

Añadir escenarios de Playwright para:
- abrir una aventura real/publicable con track;
- ver HUD con GPS, distancia recorrida, precisión y siguiente checkpoint;
- ver estados `Buscando GPS`, `GPS activo`, `GPS débil/error`;
- mantener siempre visible acceso a seguridad/ficha de ruta;
- mostrar final de aventura solo cuando el backend devuelve progreso completado.

Usar rutas API interceptadas; no introducir datos ficticios en código de producción.

**Step 2: Run tests and verify failure**

Run:
`pnpm exec playwright test e2e/magina-adventure.spec.ts`

Expected: FAIL porque HUD/telemetría/siguiente checkpoint todavía no forman un contrato único.

**Step 3: Commit test contract**

`git add e2e/magina-adventure.spec.ts && git commit -m "test: define Bedmar adventure live contract"`

---

## Task 2: Convertir el watcher GPS actual en fuente única de telemetría

**Files:**
- Modify: `apps/web/src/app/rutas/detalle/route-activity-recorder.tsx`
- Create: `apps/web/src/lib/route-live-telemetry.ts`
- Modify: `e2e/magina-adventure.spec.ts`

**Step 1: Write unit-testable telemetry helpers**

En `route-live-telemetry.ts` definir tipos/funciones puras:
- `RouteLiveTelemetry`
- `gpsQuality(accuracyM)`
- `distanceToCoordinate(current, target)`
- `emitRouteLiveTelemetry(detail)`
- constantes/nombre de evento compartido.

No abrir `watchPosition` aquí.

**Step 2: Verify test/TypeScript failure before implementation**

Run:
`pnpm --filter @magina/web typecheck`

Expected: FAIL mientras imports/contract no estén implementados.

**Step 3: Emit telemetry from existing watcher**

Desde `RouteActivityRecorder`, en cada posición válida emitir:
- latitude/longitude;
- horizontal accuracy;
- timestamp;
- GPS state;
- live distance;
- live elevation;
- activity id/status.

Emitir también cambios `searching`, `error`, `paused`, `completed`.

No crear un segundo `watchPosition`.

**Step 4: Run verification**

`pnpm --filter @magina/web typecheck`

Expected: PASS.

**Step 5: Commit**

`git add apps/web/src/lib/route-live-telemetry.ts apps/web/src/app/rutas/detalle/route-activity-recorder.tsx e2e/magina-adventure.spec.ts && git commit -m "feat: expose shared live route telemetry"`

---

## Task 3: Hacer que el mapa de aventura muestre al senderista en tiempo real

**Files:**
- Modify: `apps/web/src/app/rutas/detalle/route-map.tsx`
- Modify: `apps/web/src/app/aventura/en-curso/adventure-live-client.tsx`
- Modify: `apps/web/src/app/aventura/en-curso/live.module.css`
- Modify: `e2e/magina-adventure.spec.ts`

**Step 1: Add failing E2E assertion**

El mapa debe exponer un elemento/estado accesible `Tu posición` y precisión GPS cuando llega telemetría simulada.

**Step 2: Implement user marker without a new geolocation watcher**

`RouteMap` escucha el evento compartido y:
- crea/actualiza un único marcador de usuario;
- actualiza círculo/etiqueta de precisión cuando sea razonable;
- no recentra compulsivamente el mapa;
- permite botón explícito `Centrarme` desde la pantalla live.

**Step 3: Run**

`pnpm exec playwright test e2e/magina-adventure.spec.ts`

Expected: PASS para posición live.

**Step 4: Commit**

`git add apps/web/src/app/rutas/detalle/route-map.tsx apps/web/src/app/aventura/en-curso/adventure-live-client.tsx apps/web/src/app/aventura/en-curso/live.module.css e2e/magina-adventure.spec.ts && git commit -m "feat: show live hiker position on adventure map"`

---

## Task 4: Calcular y mostrar el siguiente checkpoint útil

**Files:**
- Modify: `apps/web/src/app/aventura/en-curso/adventure-live-client.tsx`
- Modify: `apps/web/src/lib/route-live-telemetry.ts`
- Modify: `apps/web/src/app/rutas/detalle/route-adventure-panel.tsx`
- Modify: `e2e/magina-adventure.spec.ts`

**Step 1: Add failing tests**

Con checkpoints mockeados, verificar:
- siguiente obligatorio en modo `linear`;
- siguiente pendiente más útil en modo `free`;
- distancia aproximada desde posición actual;
- mensaje `Estás cerca` dentro del umbral visual previo al radio de desbloqueo.

**Step 2: Implement selector pure**

Añadir helper:
`selectNextAdventureCheckpoint(definition, progress, currentPosition)`.

El selector no desbloquea nada; solo decide qué presentar.

**Step 3: Live HUD**

Mostrar sobre/bajo el mapa:
- checkpoint siguiente;
- metros restantes;
- tipo/categoría;
- estado `cerca`;
- botón `Ver punto` que reutiliza `magina:route-adventure-focus`.

**Step 4: Verify**

`pnpm --filter @magina/web typecheck`
`pnpm exec playwright test e2e/magina-adventure.spec.ts`

**Step 5: Commit**

`git add apps/web/src/app/aventura/en-curso/adventure-live-client.tsx apps/web/src/lib/route-live-telemetry.ts apps/web/src/app/rutas/detalle/route-adventure-panel.tsx e2e/magina-adventure.spec.ts && git commit -m "feat: guide hikers to the next adventure checkpoint"`

---

## Task 5: Unificar la UX de Aventura en curso

**Files:**
- Modify: `apps/web/src/app/aventura/en-curso/adventure-live-client.tsx`
- Modify: `apps/web/src/app/aventura/en-curso/live.module.css`
- Selectively port from: `feat/v20-routes-adventure-premium-mobile`
- Modify: `e2e/magina-adventure.spec.ts`

**Step 1: Add visual-contract assertions**

En 390×844 verificar orden:
1. estado/safety compacto;
2. mapa protagonista;
3. HUD de actividad;
4. siguiente checkpoint;
5. controles grandes `Pausar/Reanudar/Finalizar`;
6. retos/descubrimientos contextualizados.

**Step 2: Recompose, do not duplicate**

Reutilizar `RouteMap`, `RouteActivityRecorder` y `RouteAdventurePanel`, pero permitir variantes compactas/live si hace falta mediante props explícitas.

No copiar archivos enteros del PR #142 si pisan lógica nueva del candidate.

**Step 3: Accessibility**

- targets >=44 px;
- contrastes;
- `aria-live` para GPS/checkpoint;
- reduced motion;
- sin scroll horizontal.

**Step 4: Verify**

`pnpm exec playwright test e2e/magina-adventure.spec.ts`
`pnpm --filter @magina/web typecheck`

**Step 5: Commit**

`git commit -am "feat: consolidate premium live hiking experience"`

---

## Task 6: Añadir final de aventura real y resistente a reintentos

**Files:**
- Modify: `apps/api/src/routes/route-adventure.ts`
- Add or extend API tests beside route adventure route tests, following existing `tsx --test` pattern.
- Create/Modify: `apps/web/src/app/aventura/adventure-finish.tsx`
- Create/Modify: `apps/web/src/app/aventura/adventure-finish.module.css`
- Modify: `apps/web/src/app/aventura/en-curso/adventure-live-client.tsx`
- Modify: `e2e/magina-adventure.spec.ts`

**Step 1: Write failing API tests**

Verify:
- no completion without required checkpoints;
- first completion succeeds;
- repeated request does not duplicate completion/rewards;
- completed payload returns score, checkpoints, badges/collections needed by UI.

**Step 2: Minimal server fix**

Hacer completion idempotente. Si el esquema actual ya lo garantiza, documentar y probar esa garantía en vez de crear otra tabla.

**Step 3: UI final**

Mostrar únicamente datos devueltos por servidor:
- km/tiempo cuando existan en actividad asociada;
- checkpoints;
- XP de aventura;
- colecciones;
- insignias;
- CTA `Ver Mi Olivo`.

No inventar aceitunas hasta que el contrato Mi Olivo esté conectado.

**Step 4: Verify**

`pnpm --filter @magina/api typecheck`
`pnpm --filter @magina/web typecheck`
`pnpm exec playwright test e2e/magina-adventure.spec.ts`

**Step 5: Commit**

`git commit -am "feat: close adventure completion flow idempotently"`

---

## Task 7: Preparar el contrato Aventura → Mi Olivo

**Files:**
- Inspect first: `apps/api/src/routes/route-adventure.ts`
- Inspect first: Mi Olivo API route/service currently serving `/api/v1/mi-olivo`
- Modify only after test proves missing behavior.
- Modify: `e2e/mi-olivo.spec.ts`
- Modify: `e2e/magina-adventure.spec.ts`

**Step 1: Write integration test before code**

Test contract:
- completar una aventura concede una única entrada de progreso reconocible por Mi Olivo;
- reintentar completion no duplica XP/aceitunas;
- gastar aceitunas no reduce XP ni nivel.

**Step 2: Reuse existing ledger/reward path**

No crear una economía paralela en `route_adventure_runs`. Adventure score puede seguir existiendo como score local, pero la concesión global debe entrar por el ledger/servicio canónico de Mi Olivo.

Usar una clave idempotente derivada de `adventure_run_id` + tipo de recompensa.

**Step 3: Verify**

API/integration tests + `pnpm check:fast`.

**Step 4: Commit**

`git commit -am "feat: connect completed adventures to Mi Olivo ledger"`

---

## Task 8: Seleccionar y preparar una ruta real piloto de Bedmar

**Files:**
- Do not hardcode invented route values.
- Use Admin/routes data surfaces already present.
- If seed/import documentation is needed: create `docs/v20/BEDMAR_ADVENTURE_PILOT.md`.

**Step 1: Identify authoritative route**

Seleccionar una ruta de Bedmar cuyo track ya esté validado en la base/staging. Si no existe, cargar track desde fuente autorizada y completar validación técnica antes de habilitar aventura.

**Step 2: Field design**

Definir inicialmente 4–8 checkpoints reales, mezclando:
- patrimonio;
- flora/fauna cuando sea verificable;
- olivar/tradición;
- paisaje;
- al menos un reto opcional;
- checkpoints obligatorios solo donde sean seguros y razonables.

**Step 3: Validate physically before public publication**

Registrar para cada checkpoint:
- coordenada comprobada;
- radio de desbloqueo razonable según precisión observada;
- seguridad del punto;
- accesibilidad;
- cobertura aproximada observada;
- foto/referencia administrativa.

**Step 4: Commit only documentation/configuration that belongs in repo**

Nunca guardar secretos ni datos personales de pruebas físicas.

---

## Task 9: Recuperación y conectividad — V20.0 web

**Files:**
- Modify: `apps/web/src/app/rutas/detalle/route-activity-recorder.tsx`
- Create if needed: `apps/web/src/lib/route-activity-outbox.ts`
- Do NOT modify `apps/web/public/sw.js` for tile caching until map-provider/cache policy is explicitly approved.
- Modify: E2E tests.

**Step 1: Tests first**

Simular fallo temporal de envío de puntos y comprobar que la UI:
- no pierde silenciosamente el estado;
- informa `Pendiente de sincronizar`;
- reintenta en orden cuando vuelve conexión.

**Step 2: Implement small outbox**

Usar IndexedDB nativo para lotes de puntos pendientes si la necesidad se confirma. Mantener límites y limpieza al completar/sincronizar.

**Step 3: Explicit offline scope**

V20.0 puede conservar actividad temporalmente sin red, pero **no declarar mapas offline completos**. El service worker actual es de Web Push y el caching cartográfico se diseña como fase separada.

**Step 4: Verify**

E2E de reconexión + typecheck.

**Step 5: Commit**

`git commit -am "feat: make route recording resilient to temporary connectivity loss"`

---

## Task 10: QA técnico + prueba física de campo

**Files:**
- Create: `docs/v20/BEDMAR_FIELD_QA_CHECKLIST.md`
- Update: `docs/v20/V20_CORE_AUDIT_2026-09-15.md` only with verified results.

**Automated gates**

Run in this order:
1. `pnpm check:fast`
2. `pnpm build`
3. `pnpm exec playwright test e2e/magina-adventure.spec.ts`
4. relevant Mi Olivo E2E after Task 7
5. full `pnpm e2e:beta`

No declarar verde sin observar cada exit code 0.

**Physical mobile QA**

On the actual Bedmar route verify:
- GPS permission flow;
- first fix time;
- route line and user marker;
- accuracy behavior;
- checkpoint radius;
- wrong/right challenge answer;
- pause/resume;
- temporary connectivity loss;
- app foreground/background behavior and warning;
- route deviation messaging if implemented;
- finish;
- Mi Olivo progression;
- battery consumption notes;
- sunlight/readability;
- no unsafe prompt encourages leaving the official path.

**Definition of Done**

The Bedmar Aventura vertical is done only when the real mobile walk succeeds end-to-end and CI is green on the exact tested HEAD.

---

## Follow-up plans after this one

Do not mix these into the first branch:

1. `Mi Olivo Premium V2` — 2.5D living tree, level-up scene, premium collections/rewards UI.
2. `AOVE redemption pilot` — one almazara, stock, reservation, QR validation and double-redemption tests.
3. `Mi Campo core` — farms, quick register, campaign, weather/alerts, field-ready UX.
4. `V20 Home three-pillars` — rebuild Inicio around Aventura, Mi Olivo and contextual Mi Campo.
5. `Adventure offline maps/native evolution` — only after evaluating tile policy and whether Capacitor/native GPS is required.
