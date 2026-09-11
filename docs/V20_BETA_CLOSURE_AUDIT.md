# V20 — Cierre de Beta

Estado: en ejecución sobre `feat/v20-visual-prototype`.

Objetivo: dejar de ampliar módulos grandes y cerrar una beta coherente, API-first, móvil y verificable.

## Criterio de salida Beta

La Beta no se considerará cerrada hasta cumplir simultáneamente:

1. `V20 full candidate check` en verde sobre el HEAD candidato.
2. `V20 beta browser E2E` en verde sobre el mismo HEAD.
3. Ninguna pantalla privada principal depende de datos demo/local cuando `NEXT_PUBLIC_API_URL` está configurado.
4. Los recorridos críticos funcionan de extremo a extremo con API real.
5. No se presentan como funcionales flujos GIS/localización que todavía sean solo UI.
6. Revisión móvil de Inicio, Mi Campo, Finca, Registrar, Hoy, Campaña, Profesional, Cliente, Presupuesto/Factura y documento público.
7. Auditoría mínima de seguridad/performance y despliegue staging antes de merge.

## Cambios ya aplicados durante el cierre

- ✅ Wizard de nueva finca: no falsea vínculo Catastro/SIGPAC/mapa; guarda finca real y deja geometría pendiente.
- ✅ `NEXT_PUBLIC_PREVIEW_MODE` separa preview de runtime real; GitHub Pages lo activa expresamente.
- ✅ Inicio, Mi Campo, Ficha, Nueva finca, Registrar, Mapa, Planificar, Hoy y Campaña son API-first en runtime real.
- ✅ Rutas `fincas/local` bloqueadas fuera de preview explícita.
- ✅ Registrar no escribe localmente fuera de preview.
- ✅ Campaña etiqueta `cobrado − costes registrados` como comparación informativa, no flujo de caja real.
- ✅ Documentos de finca ya exigen contexto API/workspace real.
- ✅ Revisión OCR legacy/parcial usa `extraction + confirmed_fields + corrections`, sin perder campos de la extracción base.
- ✅ Revisión OCR sin workspace muestra estado de sesión, no una carga infinita.
- ✅ Perfil deja de mostrar municipio demo y acciones decorativas como si fueran funcionales.
- ✅ Full candidate ejecuta ahora también `map-context-smoke` y `document-smoke`.
- ✅ API incorpora CORS explícito con credenciales y allowlist de orígenes; en producción no se permite ningún origen si `CORS_ALLOWED_ORIGINS` no está configurado.
- ✅ `.env.example` documenta `CORS_ALLOWED_ORIGINS` y `NEXT_PUBLIC_PREVIEW_MODE=false` para staging/producción.
- ✅ Se ha creado el workflow `V20 beta browser E2E` con PostgreSQL/PostGIS, API real, Next real y Playwright Chromium.
- ✅ El primer recorrido browser cubre: sesión de desarrollo con usuario/membership real → nueva finca → localización pospuesta honestamente → registrar trabajo → ficha de finca → campaña.
- ✅ Los fallos Playwright conservan trace y screenshot como artifact para diagnóstico.

## Clasificación

- `REAL`: consume API/datos persistidos y su flujo principal existe.
- `HYBRID CONTROLADO`: API real en Beta + preview explícita solo para demo.
- `PREVIEW`: contenido estático/demo o interacción no conectada.
- `P0`: bloquea Beta.
- `P1`: debe cerrarse antes de candidate final.
- `P2`: puede completarse después de Beta sin falsear funcionalidad.

## Inventario vivo

| Área | Estado | Prioridad | Hallazgo / acción |
|---|---|---:|---|
| Inicio privado | HYBRID CONTROLADO | P1 | API real en Beta; preview solo con flag explícito. Pendiente auditoría móvil y componentes secundarios. |
| Inicio público / actualidad | PREVIEW | P1 beta pública | Noticias, eventos y patrocinado siguen estáticos. |
| Mi Campo | HYBRID CONTROLADO | P1 | Fincas API reales; preview solo explícita. |
| Ficha de finca | HYBRID CONTROLADO | P1 | API-first; ya entra en browser E2E. |
| Nueva finca — datos básicos | REAL / E2E | — | Alta `/api/v1/fields` y catálogo territorial reales, incluidos en Playwright. |
| Nueva finca — localización | HONESTA / PENDIENTE | P1 | No falsea vínculo; selector real sigue pendiente. |
| Rutas `fincas/local` | PREVIEW AISLADA | P1 | Bloqueadas fuera de preview. |
| Registrar | HYBRID CONTROLADO / E2E | P1 | API-first; primer registro de trabajo cubierto por Playwright. Falta ampliar a cosecha y otros tipos. |
| Planificar | REAL | P1 | Contexto API-first. |
| Hoy / agenda | REAL | P1 | Falta móvil/E2E. |
| Campaña | HYBRID CONTROLADO / E2E BÁSICO | P1 | La pantalla y campaña activa entran en Playwright; falta recorrido visual con cosecha/rendimiento. |
| Profesional | REAL | P1 | Muy avanzado; falta consolidación UX/eventos de decisión. |
| Documentos/OCR | REAL / HARDENED | P1 | Merge legacy corregido; full candidate ejecuta document smoke. Falta navegador después de estabilizar E2E base. |
| Perfil | REAL / HONESTO | P1 | Sin demo implícita ni CTAs ficticios; edición/exportación quedan explícitamente pendientes. |
| GIS Catastro/SIGPAC backend | REAL | P1 | Falta selector de alta/edición. |
| Mapa Mi Campo | HYBRID CONTROLADO | P1 | API real; demo solo en preview. |
| Tiempo AEMET | REAL | P1 | Revisar error/stale/empty en móvil. |
| Radar | REAL | P1 | Falta overlay final y auditoría móvil. |
| Explorar | PREVIEW | P2 beta privada / P1 beta pública | CMS/directorio incompleto. |
| Admin/CMS | INCOMPLETO | P2 beta privada / P0 lanzamiento público | Falta contenido, negocios, publicidad, imágenes y moderación. |
| Mi Olivo | BLUEPRINT | P2 | No bloquea Beta núcleo. |

## P0

### P0-1 — Wizard de finca simulaba vínculo GIS

**RESUELTO para Beta.** La finca se crea de forma real y la geometría queda explícitamente pendiente.

### P0-2 — Web y API en orígenes distintos sin política CORS explícita

**RESUELTO para Beta.** La API usa `@fastify/cors`, credenciales, headers limitados y allowlist mediante `CORS_ALLOWED_ORIGINS`. En producción, una configuración ausente no abre CORS por defecto.

## P1 — Preview/local

Regla de candidate:

- Con API configurada, nunca caer automáticamente a `demo-data` o `local-prototype-store` tras un error.
- Sin API, solo hay demo cuando `NEXT_PUBLIC_PREVIEW_MODE=true`.
- Un error real produce estado vacío/error; nunca datos inventados.
- Las rutas local-only no forman parte del flujo Beta.

Estado actual: núcleo privado principal corregido, incluyendo documentos y Perfil.

## P1 — Recorridos E2E

### Agricultor

1. Login → workspace. **Browser E2E preparado mediante identidad dev + membership real.**
2. Crear finca. **Browser E2E.**
3. Posponer localización sin engaño. **Browser E2E.**
4. Abrir finca. **Browser E2E.**
5. Registrar trabajo. **Browser E2E.**
6. Ver actividad y costes. **Browser E2E básico en ficha.**
7. Registrar entrega. **Pendiente de ampliar Playwright; cubierto por smoke API.**
8. Añadir rendimiento posterior. **Pendiente de ampliar Playwright; cubierto por smoke API.**
9. Ver campaña. **Browser E2E básico; agregados cubiertos por smoke API.**
10. Documento → OCR → revisión → guardado explícito. **Smoke API; navegador pendiente.**
11. Tiempo/radar/alertas. **Smokes API; navegador/móvil pendiente.**

El full candidate cubre por smokes reales finca, riego/idempotencia, proyecciones, cosecha, rendimiento, map-context, documento/OCR metadata, economía y profesional. Playwright añade validación real del navegador y del límite web/API.

### Profesional

1. Crear cliente/sitio.
2. Presupuesto.
3. PDF.
4. Compartir enlace.
5. Confirmar envío.
6. Cliente acepta/rechaza.
7. Convertir a trabajo.
8. Costes.
9. Factura.
10. Cobros parciales/totales.
11. Pendiente y margen.

## P1 — Auditoría móvil

Objetivos mínimos: 360 px, 390–430 px, tablet y escritorio.

El primer Playwright usa viewport 390×844 como base móvil. Cuando el recorrido base esté verde se añadirá matriz 360 / 430 / desktop para auditoría de layout, no antes.

Revisar scroll horizontal, botones, densidad, sticky bars, targets táctiles, teclado, tablas, mapas, loading/error/empty, contraste/foco/labels.

## P1 — Hardening conocido

- CI candidate completo.
- Browser E2E base en verde y luego ampliación cosecha/rendimiento/documentos.
- Semántica campaña agrícola/profesional consistente en backend/agregado.
- Parser-specific OCR fixtures.
- Races/idempotencia pendientes de liquidaciones/cobros.
- Validación de workspace/filtros pendiente en rutas concretas.
- Financial attention: prioridad/orden y counts totales.
- Catálogo documental: paginación/búsqueda/límite de subida.
- Observabilidad workers/notificaciones.
- Seguridad/performance.

## P2 — Después de Beta núcleo

- Admin/CMS completo.
- Noticias/eventos/pueblos/almazaras dinámicos.
- Cerca de ti/publicidad/promociones.
- Mi Olivo.
- Monetización/planes.
- Nowcast solo con metodología validada.

## Orden de ejecución actual

1. ~~Resolver P0 del wizard de finca.~~ ✅
2. ~~Convertir preview implícita del núcleo privado en modo explícito.~~ ✅
3. ~~Auditar documentos/OCR y Perfil.~~ ✅
4. Ejecutar E2E de navegador y reparar. **En curso.**
5. Ampliar E2E a cosecha/rendimiento/documentos.
6. Auditoría móvil multi-viewport.
7. Seguridad/performance.
8. Staging real.
9. Actualizar PR y decidir candidate final.

Este documento es el checklist vivo de Cierre Beta. No se añade una función grande nueva salvo que cierre un P0/P1.
