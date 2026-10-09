# #696 / #708: título del editor de campaña

Rama `codex/696-campaign-edit-title`, base main `fe79efd17c67450d0923868be110a52c33e6d4a3`. Corrección P2 detectada durante M1 y autorizada en la cola #708.

Al abrir una campaña existente para seleccionar parcelas, la hoja mostraba «Nueva campaña». El editor recibe ahora un contexto explícito de edición y muestra «Editar campaña». La creación conserva «Nueva campaña». El borrador, las validaciones y la operación de guardado permanecen iguales; no hay cambios de Room ni de cantidades, snapshots, adjuntos o navegación.

## Regresión y revisión

Antes de modificar producción: `CampaignScreensTest#preparationWithoutParcelsGuidesToTheSelectorInsteadOfActivation` falló por no mostrar «Editar campaña»: 1 test / 1 fallo, 14.752 s. Se añadió también la comprobación de «Nueva campaña» al caso de creación. El fallo confirma el defecto solicitado.

Revisión independiente del diff de código: sin hallazgos ni bloqueos. Las dos llamadas de creación mantienen el valor por defecto; la hoja de detalle pasa edición explícita. `git diff --check`: sin errores.

Después de la corrección, clase completa `CampaignScreensTest`: PASS, 10 tests / 96.415 s, en el AVD desechable API35 `emulator-5580`. Se instalaron allí los APK locales de app y tests; el AVD conservado no se usó para instrumentación. XML de pruebas unitarias: 524 tests, cero fallos, errores o ignorados.

Validación completa local: `gradle --no-daemon lintDevDebug testDevDebugUnitTest assembleDevDebug assembleStagingDebug assembleProductionDebug assembleDevDebugAndroidTest`, BUILD SUCCESSFUL / 15m22s. Los tres checks de GitHub del HEAD final deben estar verdes antes de integrar; su resultado se publica en la PR y #696. Móvil físico y Gate21 siguen pendientes. Esta corrección no los sustituye.

Logs locales: `artifacts/696-e2e/campaign-title-red-build.txt`, `campaign-title-red-instrumentation.txt`, `campaign-title-green-foundation.txt` y `campaign-title-green-instrumentation.txt`. La evidencia agrícola complementaria está en [M1 media jornada y horas](ANDROID-696-M1-HALF-HOURS-2026-10-09.md).
