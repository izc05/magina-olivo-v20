# #696 — inventario de pruebas reutilizables para macrobloque M1
**Fecha:** 2026-10-08. **Inspección estática** del árbol de `main` en `cbab39bdeee95746cc4c1f8a33752d17dab4cdba` tras integrar #698. **Esto NO es una ejecución de pruebas ni evidencia de PASS.** Prevalece el estado actual de #696, AGENTS/Product Lock, y la PR de macrobloques #703.

## Hallazgo principal
El repositorio **ya contiene bastante cobertura**: 203 archivos Kotlin de pruebas bajo `app/src/test/` (108 archivos JVM) y `app/src/androidTest/` (95 archivos de instrumentación); contar archivos **no equivale** a contar tests ejecutados ni aprobados.

No reescribir pruebas existentes ni lanzar una implementación duplicada por leer `#696`. El objetivo inmediato es **componer evidencia E2E real desde tests existentes**, detectar huecos de recorrido y reparar únicamente defectos reproducidos.

## Base reutilizable: dónde empezar

| Bloque | Pruebas existentes identificadas | Qué se sabe por lectura |
| --- | --- | --- |
| Año agrícola + euros | `data/local/AgriculturalYearContractTest.kt` | `aWholeOliveYearKeepsEveryRecordWhereTheFarmerExpectsItAndCountsEachEuroOnce`: repositorios offline, labores, riego, tratamiento, campaña, dos días de Pesadas, jornales, maquinaria, gasto, cierre, reapertura Room, costes. **No equivale a recorrido por pantallas o teléfono físico.** |
| Recolección + foto | `data/local/RecollectionFlowContractTest.kt` | Una Pesada 2390 kg, destino, vale, foto, jornada, 5 jornales, gasto, consulta tras reabrir BD. |
| Navegación Compose | `AppNavigationTest.kt` | Finca/parcela/campaña, reentradas y Back, Jornal/Cuaderno/avisos, recreación de Activity. |
| Histórica vs activa | `NotebookHomeScreenTest.kt` | Incluye `selectedHistoricalCampaignDoesNotReplaceRunningContext`. |
| Múltiples fincas | `RecoleccionTwoFarmsTest.kt` + `data/local/CoreWorkspaceBoundaryContractTest.kt` | Ejercitan aislamiento y presentación entre fincas/workspaces; revisar aserciones para el escenario concreto. |
| Pesada manual | `ManualPesadaUiTest.kt` + `data/local/DeliveryContractTest.kt` | Entrada manual, foto opcional, ausencia de OCR obligatorio, validación de campaña. |
| Jornales y pagos | `data/local/LabourContractTest.kt`, `LabourPaymentContractTest.kt`, `JornadaCostContractTest.kt` y `LabourPaymentsUiTest.kt` | Base para pagos parciales y costes; comprobar cobertura integrada M1. |
| Adjuntos seguros | `data/local/AttachmentFixtureIsolationTest.kt` | Cuatro regresiones para impedir borrado de fotos ajenas durante setup/teardown. **NO acredita fotos de propietario tras actualizar APK.** |
| Migraciones | `data/local/RoomMigrationTest.kt` | Entre sus casos figura migración 24→25 y varias versiones anteriores; no acredita por sí sola upgrade real del APK sin desinstalar. |
| Mapa/offline | `Phase18OfflineMapTest.kt`, `FarmMapScreenTest.kt`, `data/local/OfflineFirstCampaignRepositoryTest.kt` | Base de pruebas a aprovechar, no declaración de ergonomía física aceptada. |
| Perfil y avisos | `MyProfileScreenTest.kt`, `data/local/ProfileSettingsContractTest.kt`, `data/local/ReminderPreferencesContractTest.kt` | Base para Gate21, aceptación física sigue pendiente. |

## Posibles huecos para diagnosticar; NO afirmar bug sin reproducir
1. `AgriculturalYearContractTest` utiliza **una finca, una parcela y dos días de recolección**. #696 exige además **dos Pesadas el mismo día**, campaña con **dos parcelas**, dos fincas/workspaces y cambio desde UI. Aprovechar otros tests si ya cubren ese cruce, **antes** de añadir otro test.
2. Una prueba de reabrir Room no equivale a hacer `adb install -r` sobre una versión anterior con **foto conservada byte a byte**. Registrar SHA256 antes/después en un entorno QA aislado; nunca someter datos reales a fixtures que borren carpetas.
3. Hay muchos tests de contrato y de pantalla separados, pero el **único recorrido integral visible por UI** Finca → Parcela → Campaña → 2 Pesadas/día → Jornales/Pagos → Gasto → Coste/kg → Histórico → cambio de finca no queda demostrado por los archivos inspeccionados. Primero mapear cobertura antes de agregar uno acotado; una megaprueba frágil no sustituye contratos aislados.
4. Gate21 requiere notificaciones/Perfil/radar en móvil real, no solo emulador. Separar `PASS AUTOMATIZADO` vs `PENDIENTE PROPIETARIO`.

## Secuencia exacta para Codex (primera ejecución de M1)
1. Releer `main` SHA, #696, `AGENTS.md` y estado CI; confirmar que no hay otra PR Android productiva activa.
2. Ejecutar pruebas dirigidas **existentes**: `AgriculturalYearContractTest`, `RecollectionFlowContractTest`, `AttachmentFixtureIsolationTest`, `RoomMigrationTest`, `AppNavigationTest`, `NotebookHomeScreenTest`, `ManualPesadaUiTest`; anotar comando, conjunto exacto, número y resultado, emulador API35.
3. Preparar dataset aislado: finca F1+F2, 2 parcelas en C1, 2 Pesadas mismo día, vale+foto, jornal completo/media y pagos parciales, gasto general y campaña, C1 histórica+C2 activa.
4. Reutilizar tests existentes para probar cada transición; añadir **solo los escenarios realmente descubiertos como no cubiertos** y aceptar que la parte física no se puede automatizar completamente.
5. Si falla: usar `systematic-debugging` para encontrar causa, `test-driven-development` para proteger el caso, PR pequeña y tres checks verdes. Si no falla, evidencia de PASS sin cambiar código.
6. Entregar lista de pasos para una APK DEV de `main` con hash/versión/artefacto reales y checklist del móvil del propietario; no cerrar Gate21 sin su aceptación.

**Prohibido:** declarar 203 tests PASS (son archivos); reinstalar borrando datos; relanzar OCR; crear funcionalidades de #699/#700 antes del Gate 21; mezclar Web V3.
