# Fase 25 — Informes PDF de campaña y de temporada

Estado: implementado y verificado en rama; sin prueba física del propietario.
Base: `main` `b810f274`. Rama: `feat/android-campaign-pdf`.

## Qué pedía el propietario

Bloque 4 del orden de cierre: «Informes PDF de campañas, fincas y parcelas», y `ROADMAP-RC1.2.md`
§25 con **Gate 25: el PDF cuadra con los totales canónicos y con la verdad histórica de la campaña**.
Antes de esto no existía ningún informe: #696 pedía explícitamente «PDF de campaña/finca/parcela si
ya implementado (si no, bloqueo Fase25 explícito)».

## Cómo se garantiza Gate 25

`CampaignReport` es **dato puro, sin Android**, y no calcula dinero propio: lee los mismos agregados
que pintan la pantalla.

- Kilos, pesadas, días y rendimiento ponderado: `DeliverySummary` sobre las pesadas de la campaña.
- Costes: `RecollectionLedger` con sus **buckets** (jornales / maquinaria / otros) y su `costPerKgMilli`.
  Solo gastos **publicados**; un borrador no suma.
- Importes y kilos se formatean con `Money`, `Weight`, `Percent` y `CostPerKg`, los mismos
  formateadores de la app.

El test JVM compara línea por línea el informe con `CampaignCardSummary` **del mismo conjunto de
datos**: si alguna vez discrepan, el test falla. Eso es lo que hace que Gate 25 cuadre por
construcción y no por copia de cifras.

## Qué contiene el informe

Cabecera (campaña, finca, municipio, periodo, fecha de generación), avisos de incompletitud, y:

1. **Producción**: kilos pesados, pesadas, días, rendimiento graso, kilos sin repartir por parcela.
2. **Costes de recogida**: jornales, maquinaria, otros, total y coste/kg, una sección por moneda.
3. **Pesadas**: fecha · vale · kilos · rendimiento · árbol/suelo · destino.
4. **Días de recogida**: fecha y kilos del día (o «Kg pendientes de pesada»).

Lo que no se sabe se dice: «No disponible», «Sin análisis», «Sin pesadas registradas» — **nunca un 0
que parezca una medición**. Los avisos van en el documento: rendimiento medido sobre parte de los
kilos, más de una moneda, o un total que no se ha podido sumar.

Al pie de **cada página**: «Documento generado por Mágina Olivo desde los datos de este teléfono.
No es un certificado ni un documento oficial.» más el número de página.

## Cómo sale del teléfono

- Se escribe con `android.graphics.pdf.PdfDocument` (A4 a 72 dpi, una columna, salto de página
  cuando se llena): **offline, sin red y sin permisos de almacenamiento**.
- Vive en `files/reports/` de la propia app, que el FileProvider ya existente comparte **solo
  lectura** a petición. Se añade únicamente la ruta `reports/` a `attachment_paths.xml`.
- «Informe PDF de la campaña» en el detalle de campaña lo genera y abre el **selector del sistema**:
  el agricultor decide a dónde va. Nada se envía solo.
- El nombre es reconocible: `informe-campana-de-recogida-2026-27-10-10-2026.pdf`.

## Pruebas

JVM (`CampaignReportTest`, 4 casos):
- el informe dice los **mismos** kilos, pesadas, días, rendimiento y jornales que la tarjeta de
  campaña, y maquinaria/otros/total/coste-kg con los formateadores de la app; el borrador no suma y
  la campaña ajena no aparece en ninguna sección;
- una campaña vacía dice qué falta en vez de ceros, y sin gasto publicado no hay sección de costes;
- un rendimiento parcial lleva su cobertura al documento;
- una campaña cerrada imprime su periodo y, sin finca, conserva el nombre del *snapshot*.

Instrumentada (`CampaignReportPdfTest`, 2 casos): el fichero existe, empieza por `%PDF`, se llama
como debe, tiene una página A4 de 595×842 puntos, y un informe largo **se parte en varias páginas**.

JVM (`SeasonReportTest`, 3 casos): el documento dice lo mismo que el resumen de la explotación
(kilos, rendimiento, recogida, general, total y los dos coste/kg), el orden y el porcentaje por
finca, el periodo real de la temporada; una temporada sin campañas y los costes sin confirmar.

Instrumentada (`ReportPdfTest`, 2 casos): el fichero existe, empieza por `%PDF`, se llama como debe,
tiene una página A4 de 595×842 puntos, y un informe largo **se parte en varias páginas**.

Verificación local: Gradle 9.4.1 + JDK 17 → `lintDevDebug`, `testDevDebugUnitTest` y
`assembleDevDebugAndroidTest` SUCCESSFUL; en el AVD `MaginaOlivo_Claude_API35`, `ReportPdfTest`
2/2 PASS.

## Segundo informe: la temporada de la explotación

`SeasonReport` se construye desde el **mismo `FarmOverview`** que ya pinta «Resumen de la
explotación» en Mi Campo, así que tampoco calcula nada propio:

1. **Producción de la temporada**: kilos, pesadas, rendimiento y cuántas fincas tuvieron campaña.
2. **Costes de la temporada**: coste de recogida y su coste/kg, **gastos generales aparte**, coste
   total y coste total/kg. Una moneda por línea; nada se convierte.
3. **Por finca**: kilos, su porcentaje del total, rendimiento y coste/kg, mayor primero; una finca
   archivada se marca como historia.
4. **Periodo**: del 1 de septiembre al 31 de agosto, con las fechas reales de la temporada.

El test JVM compara el informe **contra los formateadores de la pantalla** (`overviewKilos`,
`overviewYield`, `overviewCost`, `overviewCostPerKg`, `overviewTotalCostPerKg`) sobre el mismo
`FarmOverview`: si alguna vez el documento y la pantalla divergen, falla. Una temporada sin campañas
sale con guiones y sus avisos, nunca con ceros, y los **costes sin confirmar** (#449) viajan al
documento con su «(incompleto)».

Se dispara con «Informe PDF de la temporada», junto a «Ver por finca»; el PDF lo escribe el mismo
`ReportPdf` y sale por el selector del sistema.

El renderizador se ha generalizado a `ReportDocument` (título, subtítulo, periodo, avisos y
secciones), así que el informe de parcela que venga después no toca el dibujo.

## Límites y siguiente paso (no inventados)

- **Falta el informe de parcela**, con el mismo patrón.
- **Sin mapa ni gráficas en el PDF**: el contrato de Fase 25 los menciona «where available»; dibujar
  el recinto y las series es otro *slice* y necesita su propia evidencia.
- **No hay previsualización dentro de la app**: se delega en el visor de PDF del teléfono a través
  del selector. Un visor propio sería alcance nuevo.
- Los **gastos generales de la finca** no entran en el informe de campaña ni en su coste/kg, igual
  que en pantalla (QA de #417).
- Falta la prueba física del propietario: generar el informe en el móvil, abrirlo y compartirlo.
