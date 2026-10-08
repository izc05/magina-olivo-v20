# PR #704: guard de datos y navegación de CI

La ejecución destructiva de Gate 3 requiere consentimiento explícito y un emulador verificado. El workflow Android habilita ese consentimiento únicamente en su AVD desechable. No se habilita para teléfonos ni para el emulador con datos retenidos.

## Fallos y correcciones

- El run Android 37826582227 rechazó correctamente el paso sin consentimiento. El commit 5a01fd37df897c98193aabfd2a60c67d01d577cc lo añadió al paso del AVD de CI. Las seis comprobaciones del guard pasaron.
- El run Android 37831066613 pasó. Evidence 37831066598 falló: 681 tests, un fallo en `cuadernoJornalOpensTodaysDayOnceAndItSurvivesARestart`, al pulsar una finca fuera del área visible.
- El helper del test vuelve a localizar y desplazar la fila mientras termina de cargar el resumen estacional. Conserva las comprobaciones de visibilidad, habilitación y acción; realiza un solo clic. Añade las raíces de pantalla al diagnóstico. No cambia código de producción ni datos.

## Validación local

En el AVD desechable `emulator-5580`, separado del AVD retenido `emulator-5566`:

- Compilación `assembleDevDebugAndroidTest`: correcta.
- Caso dirigido anterior: tres ejecuciones consecutivas correctas, un caso por ejecución (39.001, 22.520 y 22.389 segundos).
- Antes de estos resultados hubo un ANR de System UI y una ejecución que no encontró la fila; ambos quedan registrados en los logs locales. No se contabilizan como PASS.
- `git diff --check`: sin errores.

Logs locales: `artifacts/696-e2e/704-navigation-run-{1,2,3}.txt`. Los tres checks de GitHub del nuevo HEAD y la revisión independiente deben pasar antes de integrar. Este documento no declara esa integración ni sustituye la prueba física de Gate 21.
