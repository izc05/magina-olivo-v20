# CR-012 — contrato de jornales, liquidaciones y coste de recogida

**Autoridad:** [Issue #309 / CR-012](https://github.com/izc05/magina-olivo-v20/issues/309)
y RC1.2, incluido el addendum D2. **Entrega:** Slice 1 exclusivamente.

Esta slice añade contratos Kotlin puros y tests JVM. No cambia lo que el usuario
puede guardar ni introduce nuevas tablas, repositorios o pantallas. Slice 1 se
preparó en rama aislada mientras #308 seguía abierta. Su integración queda
bloqueada hasta actualizar la base tras la fusión de #308, repetir la CI y realizar
la revisión final. No avanzar a Slice 2.

## 1. Asistencia e identidad

`LabourEntry` conserva sus campos existentes y añade `appliedRate:
LabourRateSnapshot?` con null por defecto. No recrear Worker ni LabourByWorker.
`LabourPricing.validateNewRecollection` exige un workerId estable y quantity = 1.
La validación antigua de CountDraft continúa para poder leer/reproducir histórico;
la prohibición de nuevas entradas anónimas se conectará al writer/UI en Slice 2.

FULL_DAY, HALF_DAY y HOURS siguen siendo unidades del dominio. No inferir cuántas
horas tiene una jornada. La futura UI nueva ofrecerá completa/horas; HALF_DAY
permanece compatible. Los históricos anónimos siguen en el bloque «Sin
identificar», no se crean trabajadores ni se reparte su deuda entre personas.

## 2. Tarifa histórica

`LabourRateSnapshot(unitPriceMinor, currency, priceDate, basis)` almacena el precio
acordado en unidades menores, la moneda ISO con unidad monetaria definida y la
fecha aplicada. Precio negativo es inválido; cero explícito es válido.

`LabourPricing.capture(entry, rates, date)` toma la tarifa habitual solo si la
línea aún no tiene snapshot: completa/media usan fullDayMinor y horas hourlyMinor.
Si no hay tarifa, conserva null; no convierte desconocido en cero. No debe
invocarse para rellenar por suposición el histórico sin tarifa conocida.
Un snapshot ya existente, incluido un precio individual introducido por el
usuario, prevalece sobre los cambios posteriores de RecollectionRates.

basis conserva DAY/HOUR. Completa y media comparten DAY; horas exige HOUR.
Cambiar la unidad a una base incompatible rechaza tanto la valoración como
la recaptura implícita: es necesario acordar un precio nuevo explícitamente
(reemplazar/limpiar el snapshot en el borrador antes de capturar). No interpretar
60 €/día como 60 €/hora ni al revés. Las ediciones futuras respetarán además el
cierre de campaña y el importe pagado antes de actualizar Expense.

`amountMinor(entry)` calcula el valor operativo de la línea:

- completa: precio por persona × cantidad;
- media histórica: precio/2 HALF_UP a unidad menor por persona × cantidad;
- horas: precio por hora × minutos/60 HALF_UP por persona × cantidad.

No redondear todos los jornales juntos: el coste individual debe ser reconstruible.
La aritmética decimal evita overflow intermedio; un resultado fuera de Long se
rechaza, nunca se envuelve en un importe negativo.

## 3. Expense sigue siendo la única fuente del coste

No agregar `amountMinor(entry)` al ledger. Es una valoración operativa, no otro
total contable. Conservar el único Expense DAY_LABOUR del día, actualizado en
la futura transacción de Slice 2; no crear un Expense por pago.

`LabourLedgerAllocation.of(expense, allLiveDayEntries)` produce asignaciones
efímeras `ConfirmedLabourCost` solamente si:

1. Expense es POSTED, DAY_LABOUR y categoría LABOR, con campaña y día explícitos;
2. todas las líneas vivas del día están disponibles y sus IDs son únicos;
3. cada línea es válida, tiene tarifa histórica en la moneda del Expense, y las
   líneas identificadas representan una persona cada una;
4. sus valores suman exactamente el importe del Expense.

Cada asignación conserva labourEntryId y expenseId. No son nuevas filas de coste
ni otro ledger: juntas describen un único Expense reconciliado. No sumarlas a
Expense en el KPI. null significa **no atribuible**, nunca «gasto cero».

Un manual/escaneado sin reparto fiable, una tarifa ausente, un DRAFT o un total
distinto no permite inventar deuda individual. El Expense confirmado continúa
contando en el coste de campaña. Una línea anónima con precio históricamente
conocido puede reconciliarse, pero su asignación conserva workerId = null y no
genera saldo pagable por persona. Una línea anónima sin precio continúa legible,
sin backfill de precios ni identidad.

Slice 2 deberá asegurar que el conjunto de líneas es completo y que el snapshot
y el Expense se escriben/actualizan atómicamente. No aceptar un gasto agregado
histórico como si tuviera un reparto individual confirmado.

## 4. Pagos y saldo

`LabourPayment` contiene UUID, workerId, campaignId, paymentDate, amountMinor,
currency y note opcional. El importe se valida antes de guardar. Son movimientos
de liquidación separados de Expense; no confundir POSTED con pagado.

`LabourSettlement.of(workerId, campaignId, currency, costs, payments)`:

- filtra por persona, campaña y moneda, sin conversión de monedas;
- generado = asignaciones respaldadas por Expense POSTED;
- pagado = suma de movimientos vivos/válidos de esa persona/campaña/moneda;
- pendiente = generado - pagado; no acepta sobrepago ni importes negativos;
- IDs repetidos de jornal o pago se rechazan, nunca se cuentan dos veces.

Estado derivado: pagado = 0 → PENDING; 0 < pagado < generado → PARTIAL;
pagado = generado con pagos → PAID. Sin deuda ni pagos se mantiene PENDING
(no se afirma que alguien cobró).

`LabourPaymentRules.validate(payment, balance, campaignStatus)` rechaza importe
no positivo, contexto/moneda distintos o importe superior al saldo pendiente.
ACTIVE, HARVEST y CLOSED permiten liquidar; PREPARATION no.
`validateGeneratedChange` bloquea costes de campaña cerrada y reducciones por
debajo de lo pagado. Corregir primero los pagos; no crear anticipos/saldos a favor.

Ejemplo: 4 × 60 € = 240 €; movimientos 100 € y 80 € → 180 € pagados, 60 €
pendientes, PARTIAL. Un nuevo pago de 60 € → PAID. El coste de campaña sigue
siendo 240 € antes y después, incluso si el último pago ocurre tras el cierre.

## 5. Coste/kg

`RecollectionCostSummary.of(campaignId, expenses, deliveries, currency)` usa
solo Expenses POSTED en la moneda elegida y con campaignId explícito.
Un gasto de poda fuera de campaña no entra por compartir finca/fecha.
Solo Pesadas canónicas con ese campaignId aportan netGrams; no acepta Harvest
manual ni fabrica reparto por parcelas. No hay argumento de pagos ni tarifas.

`costPerKg = coste generado confirmado / kg pesados`. El modelo mantiene
BigDecimal DECIMAL128 y respeta las unidades menores de la moneda ISO. El
redondeo de presentación corresponde a la futura UI.

530 € / 3.200 kg = **0,165625 €/kg**, aunque parte del jornal siga sin pagar.
Sin Pesadas/kilos positivos o sin gastos confirmados: null. Cero confirmado sí
es cero conocido. IDs duplicados, importes negativos y overflow se rechazan.

## 6. Trabajo reservado a Slice 2 y posteriores

Slice 2: entidad Room y migración aditiva de snapshots/pagos; UUID y metadatos
version/soft-delete; DAO/repositorios; transacciones locales con comprobación
actual de saldo y reintentos/idempotencia; histórico de pagos; writer de Expense
reconciliado; UI de jornales y pago inicial/posterior. No usar un paidAmount mutable
en Worker ni un booleano como fuente de verdad. No backfill ficticio de legado.

Guard de integración futura: reconstruir el único Expense DAY_LABOUR sumando
los importes de cada snapshot con el redondeo individual de LabourPricing.
DayCostCalculator agrega minutos antes de redondear y no debe reutilizarse sin
adaptación: Slice 2 deberá cubrir con test las fracciones horarias que divergen.

La persistencia Room y la reapertura de app deberán tener tests propios en esa
slice. No se afirman como implementadas ni probadas aquí. Maquinaria y pantallas
de resumen/pulido permanecen en slices 3 y 4. CURRENT-STATE no declara un nuevo
Gate PASS por esta entrega.
