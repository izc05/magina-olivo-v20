# #684 — consolidación del parser y reparto de pesos

Slice P0.3 de #696, 2026-10-08. Rama `fix/497-weight-overflow`; PR https://github.com/izc05/magina-olivo-v20/pull/684. Rama heredada sin otro worktree ejecutor; scope aprobado por el propietario en #696. Actualizada con main `cda14003`, tras integrar #689 y #680 con gates verdes.

Único conflicto: docs/CHANGELOG-APP.md. Resuelto conservando ambas entradas #497 (peso/reparto) y #500 (total de gastos). Merge `98bb96fa`. Diff final contra main: parser Weight, ParcelSplit, sus tests y documentación; conserva todos los cambios de #689/#680.

El parser devuelve null ante cifras no representables en Long, sin convertirlas en cero ni provocar cierre del formulario. El reparto usa Math.addExact y rechaza overflow como exceeds_total; conserva cantidades desconocidas y reglas ordinarias. Tests cubren Long.MAX_VALUE, el primer gramo no representable, formatos con separadores y repartos exactos/mixtos/extremos.

Validación local: 520 unitarios sin fallos/errores; testDevDebugUnitTest, assembleDevDebug, assembleDevDebugAndroidTest y lintDevDebug BUILD SUCCESSFUL (5m39s). Revisión independiente sin bloqueos, diff check limpio. Checks remotos por SHA pendientes de publicación. No integrar hasta revisión independiente y foundation/gate3-emulator/gate3-evidence verdes. NEXT #692, después recorrido agrícola sobre main y APK física. Web V3 pausada.

