# CR-012 — Slice 1: contrato y tests de dominio

**Spec:** [Issue #309](https://github.com/izc05/magina-olivo-v20/issues/309), leído completo.
**Executor:** Codex. Solo Slice 1; detenerse en el PR para revisión.
**Base inicial:** `origin/main` `f7747731`. Slice 1 se preparó en rama aislada
mientras #308 seguía abierta. Su integración queda bloqueada hasta actualizar
la base tras la fusión de #308, repetir la CI y realizar la revisión final.

**Actualización de base:** #308 fusionado el 30/09/2026; `origin/main c0449ad2`
incorporado sin conflictos mediante `6a933044`. Revisión de código contra esa
base sin hallazgos importantes. CI y revisión del propietario pendientes antes
de integrar #310; no avanzar a Slice 2.

## Objetivo y límites

Fijar mediante modelos y reglas Kotlin puras las tarifas históricas, la identidad
del jornal, la deuda de cada persona/campaña y los movimientos de pago parcial.
Expense POSTED sigue siendo la única fuente del coste. No cambiar UI, navegación,
repositorios, tablas, migraciones, sincronización ni preferencias de avisos.
No declarar CR-012 completo ni avanzar a Slice 2.

## Contrato económico

- Usar `LabourEntry`, `LabourUnit`, `RecollectionRates` y `Expense` existentes.
- Un nuevo jornal de recogida exige workerId y quantity = 1. HALF_DAY se conserva
  en el dominio para el histórico; la futura UI nueva ofrecerá completa/horas.
- `LabourRateSnapshot`: precio aplicado en unidades menores, moneda ISO, fecha y base DAY/HOUR.
  Una tarifa ausente es desconocida; cero explícito es un precio válido.
  El snapshot de un jornal existente prevalece sobre la tarifa habitual posterior.
  Un cambio entre días y horas exige un nuevo acuerdo explícito; no reutilizar
  un precio diario como horario ni al revés.
- Calcular por línea con precisión decimal y redondear HALF_UP a unidades menores.
  No convertir jornadas en horas mediante una duración inventada.
- La asistencia y su snapshot son operativos: nunca sumarlos además de Expense.
- Conservar el Expense DAY_LABOUR único por día. Para atribuirlo a personas,
  `LabourLedgerAllocation` valida que cada línea tenga snapshot en su moneda
  y que la suma de las líneas coincida exactamente con su importe POSTED.
  Cada resultado conserva expenseId y labourEntryId. No dividir un gasto manual
  entre personas ni repartir importes desconocidos. Un conjunto no reconciliado
  no produce deuda individual; el gasto sigue contando en campaña.
- `LabourPayment`: UUID propio, workerId, campaignId, fecha, importe positivo,
  moneda y nota opcional. Los consumidores entregarán movimientos vivos/válidos.
  No tiene Expense, DRAFT/POSTED ni un booleano mutable de pago.
- `LabourSettlement`: generado de asignaciones respaldadas por Expense POSTED;
  pagado de movimientos propios de la persona/campaña/moneda; pendiente =
  generado - pagado. Estados derivados: cero pagos PENDING, parte PARTIAL,
  importe completo PAID. No aceptar un saldo negativo ni ocultar sobrepagos.
- Validar el movimiento contra el saldo actual en la futura transacción local:
  no sobrepago, no mezcla de persona/campaña/moneda. ACTIVE/HARVEST/CLOSED permiten
  pagar; PREPARATION no. Pagar una campaña cerrada no la reabre ni altera coste.
- Cambiar costes solo en campaña abierta, nunca por debajo de lo ya pagado.
- `RecollectionCostSummary`: sumar solo Expenses POSTED con campaignId explícito,
  moneda elegida y sin IDs duplicados. Los gastos agrícolas fuera de campaña no
  entran por coincidencia de finca/fecha. Coste/kg usa kg de Pesadas confirmadas,
  nunca kg históricos sin pesada ni pagos. Sin kg o gastos confirmados: null.
  Mantener precisión interna decimal (DECIMAL128), sin redondeo visual a céntimos.
- Histórico anónimo: sigue contando mediante Expense; `LabourByWorker.unnamed`
  conserva asistencia. No inventar trabajador ni atribuirle pagos.

## Archivos e interfaces

- `domain/labour/Labour.kt`: snapshot opcional de compatibilidad en LabourEntry.
- `domain/labour/LabourPricing.kt`: snapshot, validación de nuevas líneas,
  captura de precio habitual y cálculo histórico; no writer.
- `domain/labour/LabourSettlement.kt`: pago, asignación de Expense, saldo y
  validación de pago/edición; funciones sin efectos secundarios.
- `domain/expense/RecollectionCostSummary.kt`: proyección estricta de Expense
  y coste/kg preciso.
- Tests JVM en `domain/labour` y `domain/expense`.
- Contrato canónico en `docs/02-domain/CR012-LABOUR-COST-CONTRACT.md`.
- Evidencia en `docs/06-testing/CR012-SLICE1.md`.

## Plan de ejecución

- [x] Leer AGENTS, locks, estado vigente e Issue completo; aislar desde main.
- [x] Ejecutar suite JVM de la base: BUILD SUCCESSFUL.
- [x] Escribir y observar tests fallidos de snapshot y nueva identidad.
- [x] Implementar captura y cálculo puro; verificar completa, horas, HALF_DAY,
  precio ausente/cero/negativo, moneda y overflow.
- [x] Escribir y observar tests fallidos de asignación y pagos.
- [x] Implementar proyección reconciliada, saldo y validaciones: 240 generados,
  pagos 100 + 80, saldo 60; pago final 60; sobrepago 80 rechazado; cierre,
  duplicados y aislamiento por persona/campaña/moneda.
- [x] Escribir y observar tests fallidos de coste/kg: 530 / 3200 = 0,165625.
- [x] Implementar proyección y comprobar DRAFT, gastos fuera de campaña,
  anónimos, kg ausentes/cero, moneda y no duplicación.
- [x] Suite JVM completa, lint, builds DEV/STAGING/PRODUCTION y AndroidTest compilado.
- [x] Revisar diff y base remota; documentar evidencia para publicar PR independiente.
- [ ] Revisión del propietario sobre el PR; no fusionar ni avanzar a Slice 2.

## Riesgos y entrega posterior

Slice 2 deberá persistir snapshots y movimientos en Room mediante migración
aditiva, metadatos UUID/version/soft-delete, repositorios y transacciones que
reconcilien el único Expense del día. Los campos opcionales no se rellenan por
suposición durante la migración. Un Expense agregado/manual histórico sin reparto
fiable queda sin atribuir. Incluir tests Room de reapertura, ediciones/pagos
concurrentes y campañas cerradas antes de exponerlo en UI.

No se afirma persistencia de pagos ni funcionalidades visibles en esta slice.
Compose, Room y emulador tendrán tests nuevos cuando cambien esos límites en
Slice 2. Aquí se compilan los tests Android existentes sin modificar pantallas.
