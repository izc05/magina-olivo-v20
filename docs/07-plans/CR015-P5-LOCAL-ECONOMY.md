# CR-015 P5 — Economía local: liquidaciones, ingresos, anticipos, cobros y rentabilidad

Rama `feat/cr015-p5-local-economy`, apilada sobre P4 (#862) solo por la versión de Room (36→37). Solo local: sin
Supabase, sin sincronización, sin OCR ni IA. Aplica la restricción aprobada en P2 §5.

## Documentos

| Tipo | Significado |
|---|---|
| `ADVANCE` | Anticipo de la cooperativa. Es dinero a cuenta, **no** un ingreso reconocido. |
| `PROVISIONAL` | Liquidación provisional. Puede sustituirla una posterior. |
| `FINAL` | Liquidación definitiva. Puede sustituir a una provisional; no la sustituye ninguna. |

Cada documento guarda bruto, retenciones, cuotas o gastos de la cooperativa y neto, en unidades menores y una moneda.
La regla es neto = bruto − retenciones − cuotas, todos ≥ 0. Kilos y referencia son opcionales. Finca y campaña
agrícola son opcionales:
- Sin finca, el documento es del workspace y **no se reparte** entre fincas.
- Sin campaña agrícola, cuenta en la finca, pero no en ningún ciclo.

## Reglas deterministas

1. **Ingreso reconocido:** bruto de las liquidaciones vigentes (activas y no sustituidas). Una provisional sustituida
   por otra o por la definitiva no vuelve a sumar. Los anticipos nunca suman ingreso.
2. **Aplicación de anticipos:** se aplica un importe de un anticipo a una liquidación de la misma moneda y del mismo
   ámbito (workspace, finca y campaña). La suma aplicada nunca supera el neto del anticipo ni el de la liquidación. Lo
   aplicado a una provisional sustituida pasa a su sustituta.
3. **Cobros:**
   - Se registran contra un documento.
   - Pendiente de una liquidación = neto − anticipos aplicados − cobros de su cadena.
   - Pendiente de un anticipo = neto − cobros.
   - Ningún cobro deja un pendiente negativo.
4. **Rentabilidad,** por moneda y sin convertir:
   - Margen = ingreso bruto reconocido − cuotas − costes.
   - Las retenciones se muestran aparte: son un pago a cuenta de impuestos, no un coste.
   - Costes: cada `Expense` POSTED una sola vez, con el mismo criterio que P3.
   - Si hay provisionales vigentes o faltan liquidaciones, el balance se marca **provisional/incompleto**.
5. Anular conserva el documento, con estado `CANCELLED`. Una sustitución nunca borra el original.
6. Escrituras solo del propietario del workspace activo (mismo criterio que P3), en transacción Room.

## Fuera de alcance

Sincronización remota, importación de PDF/OCR, fiscalidad, prorrateo entre fincas y conversión de moneda.
