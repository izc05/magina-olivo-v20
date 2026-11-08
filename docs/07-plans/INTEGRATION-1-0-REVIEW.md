# Integración Android 1.0 — candidato de revisión

Rama `feat/integration-1-0-review`. Base: `e8e3acbca` + #868 (`8d4d209bb`). Candidato para revisión: no se fusiona ni se publica.

## 1. Contenido comprobado

Comprobación por contenido, no solo por ascendencia de commits.

| PR | Estado en el candidato | Evidencia |
|---|---|---|
| #847 Tiempo | Incluida vía #853 | Mismos commits reaplicados: `577cc3ebf`, `ef8dd86c7` |
| #848 selector de municipios | Incluida vía #853 | `1f02189b0`; después simplificado a propósito por `a44e8cf20` (selector INE sin conexión compartido) |
| #851 Play y privacidad | Incluida vía #853 | `65dd0a7cc` |
| #852 sincronización manual | Incluida vía #853 | Sus dos últimos commits declarados en #853 |
| #853 integración DEV | Ascendiente | `1c2a4574e` |
| #857, #859 | Ascendientes | Cadena de P1 y P3 |
| #862, #868 | Fusionadas | `4a25207c7`, `8d4d209bb` |
| #863, #865 | Fusionadas | `5a83451d5`, `ed323acf5` |
| #866 | Fusionada | `683601c38`, `e8e3acbca` |

Fuera del candidato, por decisión: Riego avanzado (B.1–B.5), P5 Economía, Gemini y Mistral. Room permanece en 36.

## 2. Discrepancia de versiones SQL entre DEV y el repositorio

No se ha alterado ningún historial ni se ha desplegado SQL.

- DEV: 10 versiones base (`20261011120000` a `20261027155737`) + `20261029044901`, `20261029104720` + 13 propuestas registradas como `20261107120003`…`20261107120015` = 25 versiones. La corrección de #868 (`20261108000001`), aplicada después por Codex solo en DEV, no se ha verificado desde este candidato.
- Repositorio (`supabase/migrations/`): 9 ficheros.
- En DEV pero no en `supabase/migrations/`:
  - `20261024125046`, `20261025203210`, `20261025203213`, `20261025203215`.
  - Las 13 propuestas, que están en `supabase/proposals/` sin marca de tiempo; sus versiones solo existen en `supabase/tests/cr015-p4-dev-rehearsal/deploy-order.txt`.
- Consecuencias:
  - `supabase/migrations/` no reconstruye por sí solo el esquema de DEV.
  - `20261108000001` requiere que la propuesta de P4 esté aplicada antes.
  - `supabase db push` contra DEV detectaría versiones remotas sin fichero local.
- `deploy-order.txt` incluye ya `20261108000001` como paso 16. El runbook describe el despliegue original de 15 ficheros.
- Antes de producción hay que consolidar una cadena completa y ordenada, ensayarla sobre un proyecto vacío y registrar sus huellas.

## 3. Sincronización en producción

Estado actual, que se mantiene: `app/src/production/.../SyncWiring.kt` y el de staging devuelven `null` y la ruta v2. Solo `app/src/dev/` construye el transporte HTTPS, la verificación de propietario y `ManualSyncCoordinator`. `ArchitectureBoundaryTest` impone esta frontera.

Para activarla, con GO real A↔B/C y autorización expresa:

1. Proyecto Supabase de producción propio. `build.gradle.kts` ya rechaza usar el de DEV o los de Tiempo.
2. Cadena SQL consolidada (§2), ensayada y aplicada con copia de seguridad previa.
3. Mover o duplicar a un conjunto de fuentes común `HttpsDeliveryRpcTransport` y `ManualSyncCoordinator` (hoy en `app/src/dev/`) y dar a producción un `SyncWiring` real.
4. Actualizar `ArchitectureBoundaryTest` y su autorización del 04/11/2026, que hoy limita el cableado a los ficheros DEV.
5. Revisar en la configuración de red de producción y en el manifiesto los enlaces de Auth (`${applicationId}://auth/callback`).
6. Repetir A↔B/C contra staging o producción, no solo DEV.
7. Implementar la resolución de conflictos (`CR015-P4-CONFLICT-RESOLUTION-DESIGN.md`, rama propia), hoy sin construir.

Limitaciones de 1.0 ya documentadas: `docs/sync/MANUAL_SYNC_1_0_HANDOFF.md`.

## 4. Google Play

| Requisito | Estado |
|---|---|
| `productionRelease` con ID propio, `targetSdk` 37, minificación | Desarrollado |
| Bloqueo de compilación sin parámetros (`verifyProductionRelease`) | Desarrollado |
| Política de privacidad enlazada en Ayuda | Desarrollado; falta la URL pública |
| «Solicitar eliminación de cuenta y datos» | Desarrollado; solo abre la URL |
| Proceso real de eliminación (Auth y datos) | Pendiente del titular |
| Proyecto Auth de producción y clave pública | Pendiente del titular |
| Firma de subida y `MAGINA_VERSION_CODE` | Pendiente del titular |
| Funciones de Tiempo y mercado en producción | Pendiente del titular |
| Data safety en Play Console | Pendiente del titular |
| Permisos a declarar: ubicación, notificaciones, inicio del dispositivo | A justificar en Play Console |

Detalle: `PLAY-1.0-RELEASE-CHECKLIST.md`.

## 5. Cambios propios de este candidato

- Oculto el acceso a «Asistente IA» de la cabecera; la ruta queda reservada. `AppNavigationTest` comprueba que no aparece.
- Inicio: el aviso de cooperativa deja de mencionar un panel de administración.
- `deploy-order.txt`: añadida la migración de #868.
