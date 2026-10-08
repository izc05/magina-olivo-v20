# IUWS 3.11.4 — matriz digital observada

Preparación #536, 2026-10-07; ampliación de PR #690. No activa backend/Room/UI ni declara cumplimiento. Separa obligación de variable, cardinalidad de bloque y aplicabilidad legal.

## Fuentes y localizadores

- Anexo VI DOCX oficial y checksum: [discovery](IUWS-3114-DISCOVERY.md).
- Excel incrustado `word/embeddings/Microsoft_Excel_Worksheet1.xlsx`, hoja `EstructuraCuadernoWS`, título 3.11.4. SHA-256 `eec4e809732e17cd845343db1e0b7bb51507b8878c8f6c0f4d57039cac752217`.
- Anexo V descargado directamente: https://www.fega.gob.es/sites/default/files/files/document/2025.11.20-ANEXO_V_Definicion_de_variables_3.11_CORRECCION_ERRORES_PUBLICACION.xlsx
- SHA-256 Anexo V: `0f7391ff59df46d83fd18071cbed8765033dd269497cc7704d60258d84e16e19`; hoja CUE, filas 151–204, variables 395–448 de tratamientos.
- Anexo VII: https://www3.sede.fega.gob.es/bdcsixpor/catalogos ; portal identificado pero su contenido no pudo extraerse con el navegador de investigación. Nombres de catálogos documentados desde descriptor, códigos concretos pendientes de descarga verificada. No copiar códigos desde ejemplos como enums.

[wire-fields.observed.json](wire-fields.observed.json) conserva 119 entradas seleccionadas del descriptor (raíz, tratamiento, riego, analítica) y 54 variables de Anexo V. Incluye fila Excel y profundidad de columna; no contiene valores de tratamientos reales ni schema completo. Los `*` y cardinalidades son literales del descriptor, **no reglas universales de obligatoriedad**. No es fixture de envío IUWS.

## Fitosanitario de superficie: V9 3.1 ↔ Anexo V ↔ descriptor

Bloque descriptor: `TratamFito`, filas 276–359. El contenedor completo se debe corroborar contra schema incrustado antes de producir JSON. Filas siguientes son localizadores, no rutas ejecutables.

| Campo conceptual/V9 | Anexo V variable | Descriptor: fila/campo/tipo | Mapping propuesto y condición |
| --- | --- | --- | --- |
| Id de actuación | Técnico | 277 IdAjenaTratamFito, number(10), * | Asignación durable backend; UUID de Activity no cabe en este campo |
| Fecha/intervalo | 395/396 | 279–280 FechaInicio/FechaFin, date * | LocalDate a dd/mm/yyyy; fecha única requiere regla explícita de intervalo, no null sin validar |
| Superficie por parcela | 399 | 284–288 DGCs 1..n; Superficie number(8,2) | m² locales → ha explícitas; cuantización revisada, nunca toda parcela por defecto |
| Cultivo/variedad | 400 | 285–287 CodigoDGC/CodigoDGCAjena/CodigoCultivo number(16) | Identidad oficial/contexto histórico, no alias ni referencia Catastro |
| Problema | 405–408 | 297–308 ProblematicaFito; TipoEnfermedad/TipoPlaga/TipoMalaHierba/TipoRegulador | Al menos una problemática; catálogo por categoría, no reason texto como código |
| Justificación | 409 | 311–312 Justificaciones 1..n / JustAct number(1) * | Catálogo de justificación independiente del problema |
| Producto | 415 | 322 TipoProducto number(1) * | Catálogo tipos; no asumir siempre autorización normal |
| Nombre comercial | 416 | Bloque ProductosFito 321–330 no incluye campo nombre separado | Conservar snapshot local/MAPA; no inventar campo JSON por similitud con V9 |
| Registro | 418 | 323 NumRegistro string(10), condicional | Registro MAPA como cadena; obligatorio para fitosanitarios según nota |
| Sustancia | 417 | 324–325 MateriaActivaFormulado / MateriaActiva number(5) | Condicional autorización excepcional tipo 4 según descriptor; no convertir nombre de sustancia en código |
| Dosis/cantidad | 419/420 | 327 Dosis number(8,3); 328 Cantidad number(8,2) | XOR: elegir una representación para wire; local puede conservar ambas históricas |
| Unidad | 421 | 329 Unidad number(2) * | Catálogo unidades, diferente según cantidad/dosis; no mapear texto local a número arbitrario |
| Aplicador | 422–427 | 331 IdentificadorAplicador 1..n; 333 NumROPO string(14) * | Snapshot persona/empresa; ROPO no se sustituye por Worker UUID ni carné sin crosswalk |
| Equipo | 428–431 | 335–342 EquipoAplicador; NumROMA/NumREGANIP/IdEquipoAplicador; AplicacionManual boolean * | Referencias alternativas; un UUID Machine no es registro oficial; manual no implica automáticamente ausencia de requisitos |
| Asesor | 438–447 | 350 AsesorValidacion 0..1; 351 NumROPO * | Bloque opcional/condicional, ROPO requerido si se informa; no exigir asesor a todo tratamiento |
| Eficacia | 448 | 358 Eficacia number(1) * | Catálogo; V9 buena/regular/mala no determina por sí solo códigos numéricos |
| Observaciones | Ver V9 | 359 Observaciones string(150) | No truncar silenciosamente notas locales para satisfacer wire |
| Hora/fase/cubierta | 397/398/401–404 | 281–294, varios condicionales | Extensiones separadas; FechaSeca ligada a arroz en nota, no requisito de olivar |

El descriptor exige DGCs de mismo producto/variedad en bloque; no enviar automáticamente una Activity multiparcela heterogénea como una sola actuación. El backend deberá particionar de forma determinista y guardar correlación Activity/revisión→ids externos sin duplicar registros locales.

Anexo V marca varias alternativas como Obligatorio simultáneamente (cantidad y dosis; categorías de problema). El descriptor/observaciones dan XOR y cardinalidades. El motor de reglas #554 debe preservar ambas evidencias y aplicar condiciones, no crear validaciones «todo obligatorio para todos».

Contradicciones pendientes: el changelog DOCX menciona TipoRegulador string(5), mientras esta hoja 3.11.4 fila 308 muestra number(3). No elegir uno silenciosamente. El schema incrustado/Swagger y sandbox deben resolver el formato definitivo.

## Identificadores y precisión

- CodigoDGC/CodigoCultivo tienen hasta 16 dígitos: el máximo excede `Number.MAX_SAFE_INTEGER` de JavaScript. Mantener decimal exacto como cadena/BigInt internamente y usar serializer numérico sin conversión por Number si schema exige JSON number.
- IdAjenaTratamFito e IdAjenaRiego son number(10), no UUID ni hash recortado. Asignador durable y estable por workspace/holding/actividad/partición; no regenerarlo en retries/correcciones.
- NumRegistro/NumROPO/ROMA son cadenas: conservar ceros a la izquierda.
- Los números decimales necesitan escala específica: Dosis 3 decimales, Cantidad/Superficie 2. Guardar dato original; si excede precisión, devolver problema de validación/revisión y no redondear silenciosamente.
- El fake de precisión es interno de diseño; no se presenta como serializer completo ni contrato de red certificado.

## Riego, fertilización y suelos

**Riego independiente:** hoja WS 583–604 define IdAjenaRiego, FechaInicio, FechaFin, SistemaRiego, Cantidad y UnidadMedida obligatorios dentro del bloque; OrigenAgua, energía, contador y buenas prácticas son opcionales según cardinalidades. Anexo V CUE 255–265 (variables 499–509) confirma cantidad y unidades totales m³/L. V9 8 tiene m³/ha: la dosis superficial no es el payload de cantidad total. Convertir únicamente con superficie explícita compatible, unidad y trazabilidad. `volumeM3` legacy no se reinterpreta como m³/ha, duración no se inventa como volumen.

**Fertilización:** descriptor 360–459 incluye Fertilizacion y PlanAbonado como bloques distintos; MaterialFertilizante/AplicacionMaterialFertilizante están condicionados a ausencia de abonado en verde. Fertirrigacion 420–434 tiene su propio contexto de agua y reglas condicionales. No reutilizar Riego independiente sin comprobar mappers/catálogos; mapping detallado sigue pendiente.

**Suelos:** existe `Analitica` 487–517 con `ParametrosSuelo` 501–510: MateriaOrganica, Arena, Limo, Arcilla, Ph, FosforoAsimilable, PotasioAsimilable, NitrogenoTotal, Conductividad; asociación DGCs 0..n. Anexo V 295–303 clasifica analíticas realizadas como voluntarias, incluida variable 546 de parámetros. Esto acredita una vía de datos analíticos, **no equivalencia completa con V9 6 Caracterización de suelos**. No afirmar que todo dato V9 de suelos se envía por esa vía sin matriz por campo/catálogo MaterialAnalizado.

## Comparación con main y pendientes

Main revisado `34dccddd`: ActivityDetail.Phytosanitary conserva nombre/sustancia/cantidad/dosis/motivo/equipo textual; riego conserva volumen/duración/sistema textual. #680 es otra línea todavía separada: no se da por integrada ni se modifica aquí. El catálogo v25 ayudará al snapshot, pero no resuelve automáticamente ids DGC, JustAct, TipoProducto, unidades/eficacia o partición.

No hay comparación de schema Supabase live en este slice. No se ejecutaron credenciales ni calls oficiales de envío. Pendientes #536: schema JSON incrustado, variables REA, catálogos VII con códigos/versiones, mappings completos fertilización/cosecha, VIII/IX/X, callback y sandbox #555. Descubrimiento digital avanza; gate productivo sigue cerrado.
