# #411 / #696: crear un gasto desde una campaña concreta

Base main `bd638511e03df821570b17b3c20f64264df5ab1d`, tras integrar #710 con los tres checks verdes. Rama `codex/696-campaign-expense-context`. Corrección agrícola acotada antes del contraste #706.

## Reproducción y contrato

En la APK DEV 1606 de main `fe79efd1`, API35 conservado: Cuaderno → Campaña QA_C2_M1 → Otros gastos / Ver gastos → Gastos de recogida → Añadir gasto. El editor permitía marcar «Finca/parcela · Fuera de campaña» pese a entrar desde esa campaña concreta. Se seleccionó y canceló sin guardar. Ningún gasto se creó o reclasificó en el AVD conservado.

[#411, decisión del propietario](https://github.com/izc05/magina-olivo-v20/issues/411#issuecomment-5984240934) exige contexto compacto y finca/campaña fijas en esta entrada. La entrada ambigua desde Cuaderno debe seguir pidiendo clasificación explícita; tener campaña activa no convierte automáticamente un gasto general en recogida.

El contrato posterior de [#522](https://github.com/izc05/magina-olivo-v20/pull/522) conserva la limpieza de relaciones al elegir/quitar Trabajo o cambiar Finca en flujos generales. Esta corrección no amplía el bloqueo a edición histórica ni cambia DocumentReview.

## Cambio

`ExpensesScreen` identifica la creación con campaña explícita. `ExpenseEditor` muestra «Recogida · Campaña …» como contexto, ofrece solo trabajos de esa misma campaña y conserva su identificador al quitar un vínculo de trabajo opcional. Mientras las opciones se cargan, mantiene el contexto explícito sin inventar el nombre de la campaña.

No cambia repositorios, Room, ledger, sincronización, OCR, importes, pagos o cantidades desconocidas. La creación desde Cuaderno y las relaciones de edición mantienen su comportamiento anterior.

## Evidencia inicial

- Tres regresiones nuevas compiladas antes del cambio productivo: BUILD SUCCESSFUL / 57 s.
- API35 desechable `emulator-5580`: 3 tests / 3 fallos esperados / 45.817 s. Selector ambiguo presente; trabajo general ofrecido; contexto explícito ausente mientras cargaban opciones.
- Revisión independiente del código corregido: sin bloqueadores. Recomienda la clase completa para conservar casos de Cuaderno, cambio de finca y desvinculación general.
- `git diff --check`: sin errores.

## Validación verde

`gradle --no-daemon --max-workers=2 lintDevDebug testDevDebugUnitTest assembleDevDebug assembleStagingDebug assembleProductionDebug assembleDevDebugAndroidTest`: BUILD SUCCESSFUL / 12 min 59 s. Los XML recién generados contienen 524 tests JVM, 0 fallos, 0 errores y 0 omitidos. Lint y las tres variantes compiladas correctamente.

API35 desechable `emulator-5580`, APK y test APK recién compiladas: 42 instrumentadas PASS / 167.581 s: RecollectionChoiceUiTest 20, ExpenseDayRoleEditorTest 4, ActivityRelatedExpenseTest 13 y FormDetailsDisclosureTest 5. Incluye las tres regresiones, creación desde Cuaderno, cambio de finca, quitar trabajo general, roles de coste y guardar con foto. Ningún test se ejecutó sobre el AVD conservado.

Preflight B0 de [#711](https://github.com/izc05/magina-olivo-v20/issues/711): #684, #692, #709 y #710 figuran MERGED; main sigue en `bd638511e03df821570b17b3c20f64264df5ab1d`. Su Android CI [37871499735](https://github.com/izc05/magina-olivo-v20/actions/runs/37871499735) ha terminado SUCCESS. #705/#706 siguen abiertos y no hay otra PR Android productiva nueva.

Tres checks del HEAD final pendientes antes de integrar. Logs locales `artifacts/696-e2e/411-{red-build,red-instrumentation,green-foundation,green-instrumentation}.txt`; XML de reproducción `m1-economic-campaign-{add-regression,can-clear}.xml`.

Móvil físico/Gate21: PENDIENTE. Web V3 pausada. Este slice no cierra las otras entradas de edición pendientes de #411 ni autoriza fases posteriores.
