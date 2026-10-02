# CR-012 — Slice 3: maquinaria y coste del uso

Implementación desde main `07a25014` tras #313. El precio por unidad queda
conservado por uso; cantidad y precio se distinguen, y un único Expense
DAY_EQUIPMENT alimenta los costes. Room v22 añade snapshots opcionales sin
inventar precios para datos antiguos.

Validación: 297 tests JVM sin fallos; lint y builds DEV/STAGING/PRODUCTION/
AndroidTest correctos. Selección inicial Room/migración: 31/31. Formulario
final: 7/7. La ejecución Android sin exclusiones pasó 391/392; el único fallo
necesitaba la preparación online de Phase 18 antes de la reapertura. Ambas
pruebas pasaron al ejecutarlas en orden. La CI normal ya las separa por contrato.
No presentar esa ejecución sin preparar como una suite completa aprobada.

Evidencia nativa con datos sintéticos, revisada visualmente:

- [Formulario: dos vibradoras × 70 = 140](evidence/cr012-slice3/equipment-sheet.png).
- [Detalle: vibradora 70, peine 20 y remolque 30](evidence/cr012-slice3/day-resources.png).
- [Informe y comandos](evidence/cr012-slice3/task-3-report.md).

Revisión independiente aprobada y seis checks CI SUCCESS en el head final `c292d154`.
PR #314 fusionado; main `5fd86744`.
No se declara aceptación física ni Gate 21 aprobado. La limpieza de tarjetas,
colores y contexto económico corresponde a Slice 4; no cambia navegación raíz.

## Cierre de revisión de Slice 3

El commit final c292d154 mantiene las correcciones transaccionales de d81f6b77. Revisión independiente: spec PASS, quality APPROVED, F1/F2/F3 resueltos sin nuevos bloqueos. Fix financiero: JVM 10/10, Room 15/15, Compose 9/9, build DEV/AndroidTest. Follow-up de fixture: Compose 9/9 y dos casos afectados 2/2 con animaciones desactivadas.

La CI de d81f6b77 tuvo dos ejecuciones instrumentadas 394/394 aprobadas y dos fallidas por callback nulo en los tests de precio vacío/cero. Un diagnóstico reprodujo que el fixture estático no cerraba el modal tras guardar. El fixture final confirma el guardado, exige cierre/reapertura y mantiene precios exactos y controles de toque físico. La causa exacta del toque perdido en CI no quedó demostrada localmente; no se atribuye a producción ni se oculta con reintentos.

CI final: seis checks SUCCESS del head c292d154; PR #314 fusionado, main 5fd86744. Aceptación física continúa pendiente.

El head 1c62d272 pasó tres suites completas de 394 pruebas y falló la cuarta por el toque Save del test de override. El diagnóstico midió el modal parcialmente expandido tras cerrar IME, Save fuera de la ventana y scroll interno max0. El intento de arrastre diagnóstico no produjo una prueba concluyente. El head final verifica el contrato de precio mediante acción Save semántica habilitada y conserva controles físicos; Compose 9/9, spec PASS y quality APPROVED. No presenta ese resultado como arreglo de accesibilidad del modal. La comprobación física del guardado tras editar queda recogida en el brief de Slice 4 y en los pendientes de revisión final.

El head d1d7587e pasó tres suites completas 394/394 y falló la cuarta en el test legacy de edición de jornal JPY, por callback no recibido. La construcción y reenvío del snapshot son síncronos; no se observó redenominación. El test final conserva JPY1000, verifica vacío/disabled, texto real/enabled, fecha y base DAY; la clase pasa 5/5 y revisión spec PASS/quality APPROVED. El toque exacto fallido no se reprodujo localmente. La interacción física del modal de jornal tras editar queda registrada separadamente para Slice 4/dispositivo.
