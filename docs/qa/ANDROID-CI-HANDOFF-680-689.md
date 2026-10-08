## Estado vigente — #696, 8 de octubre de 2026 (15:15 UTC)

Prioridad: Android; Codex ejecuta una sola tarea productiva cada vez, Claude apoya revisión/pruebas. **Web V3 y Web-1 pausadas** hasta orden expresa. #696 sustituye el reparto/cursor anteriores.

| PR | HEAD validado | Merge en main | Validación final |
| --- | --- | --- | --- |
| #689 | 6ad5848a | 8618a721 | 514 unitarios; 671 instrumentados CI; tres checks SUCCESS |
| #680 | 73f79a02 | cda14003 | 517 unitarios; 676 instrumentados CI; offline196; tres checks SUCCESS |
| #684 | d30e3ec4 | 690e2195 | 520 unitarios; parser extremo y persistencia UI API35; tres checks SUCCESS |
| #692 | 4157013d | c2556e4e | 524 unitarios; 677 instrumentados CI; offline196; tres checks SUCCESS |

Main integrado: `c2556e4e96787faea7fde015a850b91404f9c0f2`. Los informes ANDROID-689-FIX, ANDROID-680-FIX, ANDROID-684-INTEGRATION y ANDROID-692-INTEGRATION de esta carpeta y los comentarios DONE en #696 conservan ramas, commits, pruebas y gates. Ninguna de estas cuatro PR sigue bloqueada por los errores descritos en la auditoría inicial. En #680 la causa confirmada fue la selección del workspace en fixtures; no se relajó ownership productivo. Además se corrigió la mezcla de snapshots regulatorios y se probó migración soportada 1–24→25.

NEXT vigente: ejecutar recorrido agrícola sobre este main, preparar candidata APK y evidencia de actualización sin borrar datos; aceptación física por el propietario pendiente (Gate21). La cámara virtual y tests no acreditan móvil físico. Exportación PDF pendiente Fase25; Gates22–28 no abiertos. #694 conserva evidencia histórica, no ejecuta Web ni acepta release.

### Auditoría histórica conservada

**Todo lo que sigue describe el snapshot inicial c4617f87 / af1384ab, anterior a #696.** Sus fallos CI, PRs pendientes, asignaciones y próximos pasos son históricos y quedan sustituidos por el estado vigente de arriba. Sus resultados Web se conservan como evidencia previa y no autorizan reanudarla. La existencia de tests o guiones en aquel snapshot no acredita recorrido completo.
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
