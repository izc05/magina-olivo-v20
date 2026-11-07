# CR-015 P3 — implementación local de ciclos por finca

Base autorizada: P1 `cddf94ecf9e4231b2f8497d3ccd9412f3290b2bd`.
Especificaciones: P2 `5dd46664f`, CR-015 / #855 y auditoría #858,
`4ce9b94cc6f8681d7e2740a943fe9050eb183491` (documento completo leído).
GO explícito del propietario para P3 local. P4 y P5 no autorizadas.

## Decisiones y límites

- Room comprobada en la nueva rama: 34. La migración propuesta es 34→35 porque
  esta base concreta sigue en 34, sin reservar números para otras ramas.
- Tablas laterales con UUID: ciclos, vínculos de registros y operaciones locales.
  Ninguna copia de importes/kilos, ni cambios de payload G2.6, ni backfill histórico.
- Cierre inclusivo `effectiveEndDate: LocalDate` distinto de `closedAt: Instant`.
  Última pesada solo sugerida. Confirmación explícita e idempotencia local.
- Observación pura; autorización y revisión por finca dentro de la transacción.
  Sin ciclo abierto o con fecha incompatible, no asignar silenciosamente históricos.
- Las pruebas económicas 5–6 de #858 pertenecen a P5. La prueba 4 se limita
  a simulaciones locales; no se desarrolla un motor remoto de reconciliación.
- La PR #858 tiene una base main antigua y un diff acumulado amplio: se lee su
  documento por SHA; no se incorpora su rama ni código ajeno por merge/cherry-pick.
- No tocar Riego B5 ni los emuladores históricos. Migraciones iniciales solo en QA
  desechable con datos ficticios; no actualizar todavía la base del emulador personal.

## Bloques y contratos de entrega

1. Reglas puras `AgriculturalCycleRules`: límites, cierre y solapamientos; pruebas
   rojas/verdes de finca A/B, fecha efectiva, siguiente día y sugerencia no impuesta.
2. Room aditiva + repositorio `AgriculturalCycleRepository`: consultas, creación,
   previsualización/confirmación de cierre y asociaciones explícitas. Migración con
   comparación exacta de tablas anteriores, transacciones, versiones, cancelación,
   aislamiento entre cuentas y repetición de operaciones.
3. Integración con escritores actuales: asociación por UUID confirmada, controles
   locales de fecha/ciclo cerrado y ledger de costes existente; ningún segundo gasto.
   Pruebas de registros nuevos, históricos inmutables y contexto de consulta separado.
4. Panel de ciclos en Campaña: crear, consultar, asociar y cerrar; fechas legibles,
   resumen antes de confirmar, avisos de información local y balance incompleto.
   Capturas en QA para revisión del propietario antes de completar P3.
5. Regresión, migraciones desde versiones soportadas, reinicio/offline, APK DEV y PR
   Draft apilada sobre P1. Revisión independiente final y CI aplicable.

Cada bloque tendrá commits pequeños, evidencia RED/GREEN y resultados reales.
El registro operativo queda en `.superpowers/sdd/cr015-p3/` ignorado.
