# CR-012 — Slice 1: evidencia de contrato y tests

Fecha: 30/09/2026. Rama: `feat/cr012-slice1-contract`.
Base: `main` / `f7747731b9eb8448080fad5da2d0ce6fdc454bfb`.
Issue: [#309](https://github.com/izc05/magina-olivo-v20/issues/309) (solo Slice 1;
no cerrar el issue completo). Sin merge automático.

## Alcance verificado

Cuatro archivos de dominio: LabourEntry con snapshot opcional, LabourPricing,
LabourSettlement y RecollectionCostSummary. Tres suites JVM nuevas, 38 tests:

| Suite | Tests | Contrato |
|---|---:|---|
| LabourPricingTest | 13 | Identidad obligatoria, completa/horas/media histórica, moneda, precio ausente/cero, tarifa/date/basis históricos, cambio de base explícito y overflow |
| LabourSettlementTest | 16 | Reconciliación con Expense, generado/pagado/pendiente, movimientos parciales/exactos, sobrepago, cierre, reducción bajo lo pagado, aislamiento y legado anónimo |
| RecollectionCostSummaryTest | 9 | 530/3200 = 0,165625, POSTED, campaña explícita, moneda, Delivery canónica, pagos independientes, duplicados, datos ausentes y precisión |

La suite de la base pasó antes de escribir código. Se observaron fallos de
comportamiento antes de cada implementación: 11 de pricing; 14 de settlement
(los otros dos ya caracterizaban negativa de asignación); 9 de coste/kg.
La revisión independiente detectó la reinterpretación de tarifa diaria como
horaria; se añadieron dos regresiones, se observaron fallar y se corrigió con
basis DAY/HOUR. La revisión de la corrección no encontró nuevos defectos importantes.

## Comando local

Gradle 9.4.1 instalado; JBR de Android Studio; SDK Android 37.0 / build-tools 37.0.0.
Sin instalar herramientas, sin secretos ni cambios de configuración versionada.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\ISICIO\AppData\Local\Android\Sdk'
& 'C:\Users\ISICIO\.gradle\manual\gradle-9.4.1\bin\gradle.bat' --no-daemon testDevDebugUnitTest lintDevDebug assembleDevDebug assembleStagingDebug assembleProductionDebug assembleDevDebugAndroidTest
```

**Resultado final:** BUILD SUCCESSFUL (2 min 54 s), exit 0. Suite JVM completa:
**272 tests, 0 fallos/errores**, incluidos los 38 nuevos. Lint: 0 errores,
41 warnings y 2 hints en código existente; ninguna incidencia en los archivos
nuevos. APK DEV, STAGING, PRODUCTION y AndroidTest generados correctamente.

Primera ejecución completa antes del arreglo de basis: BUILD SUCCESSFUL;
270 tests, 0 fallos. Lint sin errores, con advertencias existentes fuera de los
archivos nuevos. La ejecución final incluye las dos regresiones adicionales.

## Límites de evidencia

- No se cambiaron pantallas, navegación, DI, repositorios, DAOs, entidades Room,
  versión de base de datos, migraciones ni workflows.
- Se compilan los tests instrumentados existentes; no se ejecuta un recorrido
  nuevo en emulador porque esta slice no incorpora comportamiento UI/Room.
- No se afirma persistencia offline de LabourPayment ni reapertura con pagos:
  corresponden a Slice 2 y exigirán migración/tests Room y de interfaz propios.
- Las reglas nuevas no están conectadas a writers actuales: el modelo/contrato
  queda preparado para la revisión antes de modificar el flujo de usuario.
- Los KPIs usan Expense POSTED. Un pago no suma gasto ni altera coste/kg.
- No se declara Gate CR-012 PASS ni se inicia Slice 2.
- Slice 1 se preparó en rama aislada mientras #308 seguía abierta. Su integración
  queda bloqueada hasta actualizar la base tras la fusión de #308, repetir la CI
  y realizar la revisión final.
