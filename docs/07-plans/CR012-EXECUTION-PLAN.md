# CR-012 — ejecución de las entregas restantes

Norma: Issue #309 completo y contrato de Slice 1. Autorización de ejecución e
integración concedida en este chat el 01/10/2026. No ampliar a fases backend ni
cerrar gates de dispositivo sin evidencia. Cada entrega parte del main vigente,
se revisa y pasa CI antes de fusionar; no hacer una contabilidad paralela.

## Secuencia

1. Actualizar #310 con main tras #311, esperar los seis checks y fusionar Slice 1.
2. Slice 2: Room v21 aditivo, snapshots de jornal, movimientos de pago con UUID,
   versiones/soft-delete/outbox, transacciones y saldos derivados. Formulario
   identificado, completa/horas y tarifa editable, pago inicial opcional. Persona
   reutilizable, detalle Trabajo/Pagos y pagos posteriores incluso tras cierre.
3. Slice 3: coste simple por equipo/uso con habitual precargado editable,
   snapshot histórico y Expense DAY_EQUIPMENT único; sin matriz de amortización.
4. Slice 4: Día, Campaña y Cuaderno con recursos navegables, producción separada
   de costes, coste/kg desde Expense POSTED/Delivery, tarjetas/iconos/tokens.

## Interfaces y decisiones de Slice 2

- CrewDraft y LabourChange añaden snapshot opcional y pago inicial explícito;
  ningún snapshot existente se reemplaza al cambiar la tarifa habitual.
- LabourPayment persistido separado de Expense con workspace, trabajador,
  campaña, fecha, importe, moneda, nota y LocalMetadata. Un UUID repetido es
  idempotente solo si coincide el contenido; contenido distinto es conflicto.
- DAO consulta líneas vivas por campaña y pagos vivos por campaña. Settlement
  reutiliza LabourLedgerAllocation contra el ledger confirmado; legado sin
  snapshot/reparto fiable se muestra sin inventar deuda.
- Todo pago se valida en transacción contra saldo actual y contexto vivo; no
  crea Expense ni cambia campaña cerrada. Sobrepago nunca se guarda.
- El writer DAY_LABOUR suma LabourPricing.amountMinor por snapshot. Líneas
  legacy sin snapshot preservan el ledger existente; no capturar tarifas nuevas
  como si fueran históricas. Edición explícita puede confirmar el precio.
- Edición/borrado y modificación de Expense/Harvest no pueden dejar generado
  confirmado por debajo de pagos existentes. Validar todos los workers afectados
  en la misma transacción para impedir rutas alternativas de borrado.
- No crear nuevos jornales anónimos en recogida; conservar los históricos HALF_DAY.
- Persistencia y UI mantienen load/error/empty/closed states y mensajes españoles.

## Tests de Slice 2

- Room migración 20→21 conserva jornales legacy con snapshot null; pagos vacíos.
- Tres personas a 60 generan un único Expense180. Dos líneas horarias fraccionales
  prueban redondeo individual diferente de minutos agregados.
- Cambio tarifa finca60→65 conserva snapshot60 y Expense histórico.
- 4 jornadas60 → pagos100+80 → generado240/pagado180/pendiente60; cerrar y pagar60
  no reabre ni altera Expense/coste/kg; sobrepago80 sobre pendiente60 falla.
- Reapertura de database conserva movimientos y saldo; concurrencia de pagos
  nunca supera pendiente; reintento mismo UUID no duplica.
- Reducir/quitar jornal/Expense/Harvest bajo pagado falla sin escritura parcial.
- Compose: persona obligatoria, completa/horas, tarifa editable, ningún/parcial/
  completo, errores y estados con texto+icono+color; legacy visible identificado
  como Sin identificar. Pago desde historial de persona sin campaña activa.

## Tests de Slice 3 y 4

- Vibradora70+peine20+remolque30=120, único Expense; defaults futuros no reescriben
  snapshots ni campañas cerradas. Null y cero no son equivalentes.
- 300+180+50 sobre3200kg =0,165625 interno; sin kg no inventar coste/kg.
- Fuera de campaña no entra en sus costes; no duplicar KPIs ni largos listados.
- Compose navegación de tarjetas a detalles, estados vacíos/errores, texto grande.
- Evidencia instrumentada y capturas de Día/Campaña/persona/pago en emulador.

## Verificación de cada PR

testDevDebugUnitTest lintDevDebug assembleDevDebug assembleStagingDebug
assembleProductionDebug assembleDevDebugAndroidTest. Schema generado por KSP,
nunca fabricado. CI foundation + gate3-emulator + gate3-evidence. Revisión final
independiente de código financiero y diffs con alcance del slice. Adjuntar cada
PR a este chat, fusionar solo después de validar el head actual, actualizar main.

## Review Focus

- Monedas/contextos incompatibles, datos legacy y ausencia de precio.
- Mutaciones indirectas del ledger: tarifas, gastos manuales, baja de Harvest.
- Pago duplicado/concurrente, rollback y outbox sin duplicación de Expense.
- Cerrado: liquidar deuda sí, reescribir coste histórico no.
- Máquina: coste por unidad vs coste total del uso explícito, sin multiplicar dos veces.

### Task 2A: persistencia financiera de Slice 2

Requisitos completos e interfaces: `docs/07-plans/CR012-SLICE2-DATA.md`.
Implementar modelo/Room/writers y tests; no modificar UI. Revisar spec y calidad
contra ese brief, incluyendo transacciones, reintentos y rutas alternativas del ledger.

### Task 2B: UI de jornales y pagos

Requisitos completos: `docs/07-plans/CR012-SLICE2-UI.md`. Consumir las interfaces
de Task 2A; no introducir cálculo paralelo ni modificar sus writers sin revisión.
Pruebas Compose y evidencia de formulario, persona y pago; después CI del Slice 2.

### Task 3: maquinaria y coste del uso

Requisitos completos: `docs/07-plans/CR012-SLICE3-MACHINERY.md`.
Slice 2 integrado en #313 (main 07a25014), seis checks verdes. Congelar
precio aplicado, preservar legado y reconstruir únicamente DAY_EQUIPMENT.

### Task 4: pantallas y contexto económico

Requisitos completos: `docs/07-plans/CR012-SLICE4-UI.md`.
Slice 3 integrado en #314 (main 5fd86744), seis checks verdes. Eliminar
la asignación económica implícita por finca/fecha; comprobar tarjetas, semántica,
coste/kg, navegación a detalles y accesibilidad con evidencia de emulador.
