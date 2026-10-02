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

Revisión independiente y CI del head final pendientes antes de integrar.
No se declara aceptación física ni Gate 21 aprobado. La limpieza de tarjetas,
colores y contexto económico corresponde a Slice 4; no cambia navegación raíz.
