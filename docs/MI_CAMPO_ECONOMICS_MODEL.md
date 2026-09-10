# Mi Campo — modelo económico V20

Estado: **estructura definida**. No implica todavía una pantalla contable.

## 1. Regla principal

Mágina debe ayudar a responder preguntas agrícolas sencillas:

- ¿cuánto me cuesta esta finca?
- ¿cuánto me cuesta esta campaña?
- ¿cuánto cuesta producir 1 kg de aceituna?
- ¿qué gastos tengo pendientes de pagar?
- ¿qué trabajos a terceros tengo pendientes de cobrar?
- ¿qué margen me deja una campaña?

No debe exigir al agricultor llevar contabilidad formal para obtener estas respuestas.

## 2. Cinco conceptos que no se mezclan

```text
COSTE
valor económico consumido para producir o trabajar

GASTO / EVENTO ECONÓMICO
registro del hecho que genera ese coste o ingreso

PAGO
movimiento real de dinero que liquida total o parcialmente una obligación

INGRESO
valor económico generado por venta, ayuda o servicio

COBRO
movimiento real de dinero recibido
```

Ejemplo:

```text
12 octubre
Poda realizada por Pedro
Coste generado: 180 €
Estado: pendiente de pago

20 octubre
Pago a Pedro: 100 €
Pendiente: 80 €

5 noviembre
Pago a Pedro: 80 €
Estado: pagado
```

El histórico debe conservar las tres fechas y cantidades.

## 3. Origen de los costes

Los costes pueden nacer automáticamente desde:

- participantes/jornales de un Trabajo;
- maquinaria utilizada;
- materiales;
- tratamientos;
- abonado;
- riego;
- combustible;
- electricidad;
- servicios externos;
- transporte;
- alquileres;
- mantenimiento;
- otros gastos.

También se permite un gasto manual independiente.

No se duplicará un coste si ya procede de un Trabajo o registro especializado.

## 4. Ingresos

Fuentes iniciales:

- venta de aceituna;
- venta de aceite;
- ayudas/subvenciones;
- trabajos realizados para terceros;
- indemnizaciones;
- otros ingresos.

Una Entrega de aceituna no implica por sí sola un ingreso. El ingreso aparece cuando existe liquidación, venta o valoración económica asociada.

## 5. Reparto entre fincas

Un gasto puede afectar a una o varias fincas.

Ejemplos:

```text
Factura abono 900 €
├── Las Cenillas 400 €
├── El Cerrillo 300 €
└── La Loma 200 €
```

O mediante reglas de reparto:

- manual;
- por superficie;
- por número de olivos;
- por horas de trabajo;
- por kg cosechados;
- reparto igualitario.

La regla usada debe quedar guardada para poder explicar de dónde sale el coste de cada finca.

## 6. Campaña

La campaña agrupa los eventos económicos relacionados con un periodo agrícola.

Resumen previsto:

```text
CAMPAÑA 2026/27

Producción
4.000 kg aceituna
Rendimiento ponderado 21,3 %
≈ 852 kg aceite equivalente

Costes
Jornales             1.395 €
Maquinaria             480 €
Tratamientos            86 €
Abonado                 310 €
Riego / energía         190 €
Otros                   105 €
────────────────────────────
TOTAL                 2.566 €

Coste/kg aceituna      0,642 €
Coste/kg aceite eq.    3,012 €

Ingresos              3.150 €
Margen                  584 €
```

Los cálculos unitarios solo se muestran cuando existe denominador válido. Nunca se inventan kg ni rendimiento.

## 7. Trabajo para terceros

Un Trabajo para terceros tiene dos caras separadas:

```text
COSTE INTERNO
├── trabajadores
├── maquinaria
├── combustible/materiales
└── otros costes

VENTA DEL SERVICIO
├── presupuesto
├── precio final
├── cobrado
└── pendiente
```

Esto permite calcular margen del servicio sin confundir lo que cuesta hacerlo con lo que se cobra al cliente.

## 8. Pagos y cobros parciales

Un pago/cobro puede liquidar:

- un único evento;
- parte de un evento;
- varios eventos a la vez.

Ejemplo: una transferencia de 500 € puede pagar tres jornales pendientes. La asignación debe conservarse.

## 9. Documentos

Factura, ticket, recibo o justificante puede vincularse tanto al evento económico como al pago/cobro.

El documento es evidencia; el importe estructurado sigue siendo el dato operativo.

## 10. Interfaz futura

No crear un módulo de “Contabilidad” pesado dentro de cada finca.

La ficha de Finca mostrará un resumen económico sencillo dentro de `Resumen` o `Actividad`, mientras que un informe más completo podrá existir a nivel de Mi Campo/Campaña.

Conceptos visibles preferidos:

- Coste de campaña
- Pendiente de pagar
- Pendiente de cobrar
- Ingresos
- Margen
- Coste/kg

## 11. Reglas fijadas

1. Coste y pago son cosas distintas.
2. Ingreso y cobro son cosas distintas.
3. Un evento puede liquidarse parcialmente.
4. Un gasto compartido puede repartirse entre varias fincas.
5. El reparto debe ser explicable y auditable.
6. Una Entrega de aceituna no crea automáticamente un ingreso.
7. Los costes derivados de Trabajo no se duplican como gastos manuales.
8. Los datos históricos no cambian porque cambie después una tarifa de catálogo.
9. Los indicadores unitarios solo se calculan con datos reales disponibles.
10. La experiencia de usuario debe seguir siendo agrícola, no contable.
