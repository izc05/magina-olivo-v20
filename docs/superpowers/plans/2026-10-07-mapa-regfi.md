# MAPA REGFI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar, después del gate explícito, ingestión oficial versionada y lectura de catálogo conservando la última versión válida y los históricos.

**Architecture:** Descarga JSON oficial → candidato validado → versiones inmutables → publicación atómica → endpoint de lectura. Ingestión separada de la petición del cliente; medir recursos antes de seleccionar Edge o worker. Android/IUWS quedan fuera de esta PR futura.

**Tech Stack:** TypeScript, runtime compatible con fetch/Web Crypto; handlers con dependencias inyectadas y tests Node siguiendo `supabase/functions/oil-market`. Persistencia mediante interfaz versionada; Supabase Storage para artefactos y operación atómica del puntero mediante repositorio backend (recurso y política en migración futura separada si se necesita).

**Spec:** `docs/integrations/mapa-regfi/CONTRACT.md`; aceptación `docs/integrations/mapa-regfi/QA.md`.

## Global Constraints

- #681 es PREP hasta nuevo gate: este plan NO se ejecuta como parte de la entrega de preparación.
- NO modificar `app/**`, Room/migrations ni #676/#679/#680.
- NO IUWS/REAFA/certificados, secretos reales ni activación automática de producción.
- Una sola implementación productiva activa. Rama futura nueva desde main, PR separada.
- Fuente MAPA oficial JSON; no scraping UI HTML.
- Históricos no se reescriben, catálogo inválido no se publica, un nº registro no se duplica.

## Review Focus

- El export nominalmente autorizado contiene cancelados: no asumir CURRENT.
- Duplicados reales con límites de venta contradictorios: conservar variantes sin elegir por IdProducto.
- Payload de 86 MB exterior/50 MB interior: medir memoria, tiempo y límites antes de Edge.
- Race de refresh y lectura multipágina: puntero CAS y versión fijada en cada página.
- Producto ausente no implica retirada: snapshot inmutable y versión anterior conservada.

## Task 0: liberar gate y elegir ejecución de ingestión

- [ ] Confirmar orden explícita de ejecución y que no compite con otra línea productiva; actualizar main y abrir rama aislada.
- [ ] Comparar descripción oficial PDF y contrato observado; registrar cambios sin inventar campos.
- [ ] Ejecutar benchmark con tamaño equivalente al real y guardar RSS máximo, tiempos y bytes normalizados. Si supera recursos con margen, elegir worker de ingestión y mantener Edge solo para lectura.
- [ ] Antes de desplegar: aprobar recurso durable y permisos de lectura/refresh. No guardar LKG solo en memoria del proceso.

## Task 1: parser/normalización y contrato

**Files (futuros, no se crean en PREP):**
- Create: `supabase/functions/phytosanitary-catalog/contract.ts`
- Create: `supabase/functions/phytosanitary-catalog/mapa.ts`
- Test: `supabase/functions/phytosanitary-catalog/mapa.test.ts`
- Fixtures: copiar selectivamente desde `docs/integrations/mapa-regfi/fixtures/`.

**Interfaces:**
- `decodeMapaTransport(text: string): { fecha: string; contenido: string; productos: unknown[] }`
- `normalizeProducts(rows: unknown[]): Product[]` (tipos definidos por CONTRACT.md).
- `makeCatalog(decoded: ReturnType<typeof decodeMapaTransport>, fetchedAt: string): Promise<Catalog>`; hash UTF-8 de Contenido, no del JSON reserializado.
- `searchProducts(catalog: Catalog, query: string): Product[]`; índice por registro/nombre/alias/sustancia, no asesoramiento.

- [ ] Escribir primero tests T01–T06, T10–T13 y T20. Assertions: `searchProducts(v1,'cobre').length===2`; registro conflictivo cuenta 1 producto/2 variantes; `resolution==='REVIEW_REQUIRED'`; fecha contradictoria del mismo provider id lanza `conflicting_provider_id`; NP sigue siendo texto; desconocido queda UNKNOWN; mismo uso con distintas condiciones sigue siendo dos usos.
- [ ] Ejecutar `node --experimental-strip-types --test supabase/functions/phytosanitary-catalog/mapa.test.ts`; debe fallar antes de implementar.
- [ ] Implementar parser acotado, validación completa y normalización según Spec. Mantener variantes ordenadas, identidad string y unidades originales.
- [ ] Repetir el comando: todos los tests pasan. Guardar prueba y commit de esta unidad.

## Task 2: refresh durable y publicación atómica

**Files futuros:**
- Create: `supabase/functions/phytosanitary-catalog/catalog-store.ts`
- Create: `supabase/functions/phytosanitary-catalog/refresh.ts`
- Test: `supabase/functions/phytosanitary-catalog/refresh.test.ts`
- Adaptador durable y configuración de worker: solo después de Task 0, en recurso elegido y revisado; ninguna tabla existente Android se reutiliza como catálogo mutable.

**Interfaces:**
- `CatalogStore.readCurrent(): Promise<{ revision: string; catalog: Catalog } | null>`
- `CatalogStore.putImmutable(catalog: Catalog, raw: string): Promise<void>`
- `CatalogStore.compareAndSwap(expectedRevision: string | null, sourceVersion: string): Promise<boolean>`
- `CatalogStore.recordAttempt(at: string, error: string | null): Promise<void>`
- `refreshCatalog(deps: { fetch: typeof fetch; now: () => Date; store: CatalogStore }): Promise<{ outcome: 'PUBLISHED'|'UNCHANGED'|'RETAINED'|'QUARANTINED'; sourceVersion: string | null }>`

- [ ] Escribir tests T07–T09, T14–T17 con fetch/store fakes. Assertions: error upstream no llama CAS, fetchedAt anterior no cambia; sin LKG retorna RETAINED/null; `Fecha + checksum` idénticos dan UNCHANGED; mismo hash con nueva Fecha crea una nueva versión de metadatos sin duplicar necesariamente los bytes raw; pérdida >20% QUARANTINED; segunda CAS obsoleta falla; snapshot v1 serializado antes/después es idéntico.
- [ ] Ejecutar `node --experimental-strip-types --test supabase/functions/phytosanitary-catalog/refresh.test.ts`; confirmar fallo inicial.
- [ ] Implementar POST form a endpoint configurado, límites, staging, métricas, retry acotado y publicación. No crear catálogo vacío por catch.
- [ ] Conectar repositorio durable solo en entorno local de pruebas; verificar fallo antes/después del CAS y recuperación tras reinicio. Retener objetos versionados con referencias históricas.
- [ ] Repetir tests; registrar recursos/permiso/race y commit. Si el backend requiere migración nueva, generar y revisar esa migración en la PR de implementación, nunca en PREP.

## Task 3: endpoint de lectura y evidencia

**Files futuros:**
- Create: `supabase/functions/phytosanitary-catalog/handler.ts`
- Create: `supabase/functions/phytosanitary-catalog/index.ts`
- Test: `supabase/functions/phytosanitary-catalog/catalog.test.ts`
- Modify: `supabase/functions/README.md` en PR futura, no en PREP.

**Interfaces:**
- `handleCatalog(body: unknown, deps: { store: CatalogStore; now: () => Date }): Promise<{ status: number; body: unknown }>`
- Petición lectura: `{operation:'catalog',sourceVersion?:string,cursor?:string,pageSize?:number}`. Primera página resuelve current; respuesta fija sourceVersion/checksum, cursor opaco de esa versión y máximo 100 productos/página. Peticiones posteriores requieren la versión devuelta. Snapshot completo reconstruido mantiene metadatos globales y productos únicos.
- Refresh es operación interna privilegiada separada; ninguna URL upstream ni secreto entra por body del cliente.

- [ ] Escribir tests T08/T18/T19: sin catálogo 503; página 2 conserva versión 1 aunque current cambie; refresh ordinario denegado; URL aportada por usuario no ejecuta fetch.
- [ ] Ejecutar `node --experimental-strip-types --test supabase/functions/phytosanitary-catalog/catalog.test.ts`; fallo inicial comprobado.
- [ ] Implementar lectura versionada y freshness según Spec. El handler no consulta MAPA ni depende de ingestión síncrona. Edge entry usa patrón de entry existente, con autorización verificada contra docs Supabase vigentes.
- [ ] Ejecutar suite de esta función y funciones actuales: `node --experimental-strip-types --test supabase/functions/*/*.test.ts`; registrar resultados.
- [ ] Revisar diff, límites, autorización y no cambios Android; abrir Draft PR con evidencia. Deploy solo tras aceptación explícita del gate correspondiente. Este plan no contiene credenciales, instrucciones de despliegue productivo ni auto-merge.

## Autorrevisión del plan

Cobertura: T01–T20 distribuidos entre parser, refresh y endpoint; históricas/invariantes en Task 2; recursos en Task 0. Firmas Catalog/Product coinciden con Spec. Riesgos que requieren entorno real: límites/runtime, operación CAS durable y acceso upstream desde región de ejecución. Esta preparación no acredita esas pruebas ni libera gate.
