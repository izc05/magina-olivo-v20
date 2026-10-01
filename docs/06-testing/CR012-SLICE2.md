# CR-012 — Slice 2: jornales y movimientos de pago

Aplicación Android nativa. Base integrada: `main` tras #308, #311 y #310.
La entrega añade Room v21, precios históricos, pagos independientes y su UI.
`Expense POSTED` conserva la autoridad del coste; pagar solo liquida deuda.

## Evidencia local del 01/10/2026

- Datos: 42 tests Room de jornales, pagos, costes y migraciones pasaron.
- Corrección de cierre: 45 tests Room de Expense, pagos y costes pasaron;
  incluyen rechazo y rollback de cambios directos e indirectos sobre campañas
  cerradas, sin depender de que existan pagos previos.
- UI: 27 tests Compose enfocados pasaron; captura final enfocada 1/1.
- JVM: 286 tests en 66 clases, cero fallos, errores o tests omitidos.
- Lint y builds DEV, STAGING, PRODUCTION y AndroidTest: correctos.

Comando completo:

```text
testDevDebugUnitTest lintDevDebug assembleDevDebug assembleStagingDebug
assembleProductionDebug assembleDevDebugAndroidTest
```

Emulador API 35 dedicado. Las ejecuciones Room anteriores son selecciones
distintas y solapadas; no representan una única ejecución de toda la suite.
La selección Compose incluye identidad, precio, horas, pagos iniciales,
validación, conservación del borrador/UUID, sobrepago, corrección explícita,
histórico desconocido y liquidación tras cierre.

## Capturas

Fixtures sintéticos de los componentes Compose nativos, no datos del usuario.
Capturados mediante UiAutomation y revisados visualmente:

- [Formulario](../../artifacts/cr012-slice2-ui/form.png).
- [Persona: 240 generados, 180 pagados, 60 pendientes](../../artifacts/cr012-slice2-ui/person.png).
- [Trabajo y movimientos de 100 + 80](../../artifacts/cr012-slice2-ui/person-history.png).
- [Registrar pago en contexto](../../artifacts/cr012-slice2-ui/payment.png).

El helper de captura rechaza el contenido interior uniforme antes de aceptar
un PNG; una captura inicial en blanco se sustituyó y no cuenta como evidencia.

## Estado de revisión

Datos: revisión independiente aceptada, incluida la corrección de cierre.
UI e integración: revisiones independientes aceptadas. PR #313 fusionado el
01/10/2026 (main `07a25014`), seis checks SUCCESS en el head `0a692899`.
La CI completa incluye 380 tests nativos por ejecución. Las correcciones
de etiquetas y de interacción conservaron las aserciones financieras; el
último ajuste añadió precondiciones y OnClick Compose tras un fallo aislado
no reproducido en doce intentos. Su causa exacta no se demostró. Los nueve
tests específicos y el build AndroidTest también pasaron.
No se declara aceptación en dispositivo físico ni se cierra el Gate 21.
Maquinaria y superficies finales de campaña corresponden a Slices 3 y 4.
