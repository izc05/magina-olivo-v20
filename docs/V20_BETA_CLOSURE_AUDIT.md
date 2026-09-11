# V20 — Cierre de Beta

Estado: en ejecución sobre `feat/v20-visual-prototype`.

Objetivo: dejar de ampliar módulos grandes y cerrar una beta coherente, API-first, móvil y verificable.

## Criterio de salida Beta

La Beta no se considerará cerrada hasta cumplir simultáneamente:

1. `V20 full candidate check` en verde sobre el HEAD candidato.
2. Ninguna pantalla privada principal depende de datos demo/local cuando `NEXT_PUBLIC_API_URL` está configurado.
3. Los recorridos críticos funcionan de extremo a extremo con API real.
4. No se presentan como funcionales flujos GIS/localización que todavía sean solo UI.
5. Revisión móvil de Inicio, Mi Campo, Finca, Registrar, Hoy, Campaña, Profesional, Cliente, Presupuesto/Factura y documento público.
6. Auditoría mínima de seguridad/performance y despliegue staging antes de merge.

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
| Ficha de finca | HYBRID CONTROLADO | P1 | API-first; pendiente móvil/E2E. |
| Nueva finca — datos básicos | REAL | — | Alta `/api/v1/fields` y catálogo territorial reales. |
| Nueva finca — localización | HONESTA / PENDIENTE | P1 | No falsea vínculo; selector real sigue pendiente. |
| Rutas `fincas/local` | PREVIEW AISLADA | P1 | Bloqueadas fuera de preview. |
| Registrar | HYBRID CONTROLADO | P1 | API-first; falta E2E de tipos de registro. |
| Planificar | REAL | P1 | Contexto API-first. |
| Hoy / agenda | REAL | P1 | Falta móvil/E2E. |
| Campaña | HYBRID CONTROLADO | P1 | Semántica UI corregida; falta revisar agregado agrícola/profesional backend. |
| Profesional | REAL | P1 | Muy avanzado; falta consolidación UX/eventos de decisión. |
| Documentos/OCR | REAL / HARDENED | P1 | Merge legacy corregido; faltan parser fixtures, paginación/búsqueda y límite de subida. |
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

## P1 — Preview/local

Regla de candidate:

- Con API configurada, nunca caer automáticamente a `demo-data` o `local-prototype-store` tras un error.
- Sin API, solo hay demo cuando `NEXT_PUBLIC_PREVIEW_MODE=true`.
- Un error real produce estado vacío/error; nunca datos inventados.
- Las rutas local-only no forman parte del flujo Beta.

Estado actual: núcleo privado principal corregido, incluyendo documentos y Perfil.

## P1 — Recorridos E2E

### Agricultor

1. Login → workspace.
2. Crear finca.
3. Posponer localización sin engaño.
4. Abrir finca.
5. Registrar trabajo.
6. Ver actividad y costes.
7. Registrar entrega.
8. Añadir rendimiento posterior.
9. Ver campaña.
10. Documento → OCR → revisión → guardado explícito.
11. Tiempo/radar/alertas.

El full candidate ya cubre mediante smokes reales: finca, riego/idempotencia, proyecciones, cosecha, rendimiento, map-context, documento/OCR metadata, economía y profesional. Falta convertirlo en recorrido visual/E2E de navegador.

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

Revisar scroll horizontal, botones, densidad, sticky bars, targets táctiles, teclado, tablas, mapas, loading/error/empty, contraste/foco/labels.

## P1 — Hardening conocido

- CI candidate completo.
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
4. Ejecutar E2E de navegador y reparar.
5. Auditoría móvil.
6. Seguridad/performance.
7. Staging real.
8. Actualizar PR y decidir candidate final.

Este documento es el checklist vivo de Cierre Beta. No se añade una función grande nueva salvo que cierre un P0/P1.
