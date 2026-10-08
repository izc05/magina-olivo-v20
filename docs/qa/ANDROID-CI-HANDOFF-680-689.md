# Handoff Android CI — #680 / #689

Auditoría solo lectura #693, 2026-10-08. Responsable de corrección: Claude. Ningún archivo Android, test, schema o workflow se modifica en este slice. Resultados obtenidos de logs GitHub, no reproducción local nueva.

## #689 — compilación de tests

Head `45b6743d840d5ea9c45916e2fa359a878cc2a447`, igual al headSha del [run 37725766855](https://github.com/izc05/magina-olivo-v20/actions/runs/37725766855). foundation/gate3-emulator y gate3-evidence fallan antes de una validación útil de emulador.

Errores observados:
- `JornadaTest.kt:50–51`: Unresolved reference `delivery`. La prueba nueva llama `delivery(...)`, mientras el helper presente se llama `pesada(...)` y acepta más parámetros.
- `CampaignChartsScreenTest.kt:96` y `AgriculturalYearContractTest.kt:219`: actual Long?, expected Long.
- `HarvestContractTest.kt:166`: overload ambiguity tras cambiar la nulabilidad del total.

Siguiente paso Claude: corregir helper y assertions respetando total desconocido, sin convertir null a cero ni !! generalizado; revisar usos afectados de Long?. Reproducir `gradle testDevDebugUnitTest assembleDevDebugAndroidTest`; después ejecutar los tres gates. No basta relanzar CI sin corregir compilación. Mantener esta PR separada de #684/#683/#680.

## #680 — instrumentación, no compilación

Head `f7c897a30d3813e052069496ace94cb088a85240`, igual al headSha de [Android run 37725710321](https://github.com/izc05/magina-olivo-v20/actions/runs/37725710321) y [evidence run 37725710303](https://github.com/izc05/magina-olivo-v20/actions/runs/37725710303). foundation SUCCESS; gate3-emulator y gate3-evidence FAILURE.

Run Android: 194 tests, 24 fallos. Errores repetidos `create(TYPE) failed: Validation(field=workspaceId, code=context_mismatch)`, comparisons que esperaban `parcelIds`/`parcelAreasM2` pero recibieron workspaceId y casts Failure→Success. Stacks incluyen `TypedActivityDetailContractTest.create:622` y otros tests de detalles/targets. Evidence falla por la misma familia; `adb rc=0` no equivale a tests correctos.

Hipótesis a comprobar, no causa raíz declarada: mismatch entre workspace activo y workspace de fixtures tras cambios de scope. `TypedActivityDetailContractTest` usa workspace fijo y seed; repositorio/ActiveWorkspaceScope emiten el código observado. Claude debe contrastar seed + contexto activo + orden de validación en cada suite antes de decidir si falla fixture o implementación.

Siguiente paso: ejecutar `TypedActivityDetailContractTest` y `ActivityEngineContractTest` en emulador con DB limpia; identificar la validación que rechaza el comando. Si fixtures están obsoletas, alinearlas sin debilitar aislamiento A/B. Si es código, conservar la protección de ownership. Después repetir suite completa offline y ambos gates. No calificar estos fallos como infraestructura: los logs muestran aserciones funcionales reproducidas en ambos workflows.

## Otros estados verificados

- #684 head d6c20e7d: foundation, gate3-emulator, gate3-evidence SUCCESS. Sigue OPEN; no fusionada por Codex.
- #692 head f84da9b0: los tres checks SUCCESS, OPEN/draft; 14 unitarios y 4 Compose API35 locales. Pendiente aceptación/integración y prueba física.
- #688 y #690 MERGED el 2026-10-08; contratos PREP en main, sin deploy productivo.

Actualizar esta evidencia si cambia el SHA. No extrapolar logs de una revisión anterior a una nueva ni integrar con gates rojos/desconocidos.
