# Modelo de explotación REAFA y relaciones con SIGPAC y SIEX

**Corte de evidencia: 9 de noviembre de 2026.** La lectura distingue **VERIFICADO** (afirmación explícita en fuente primaria), **INTERPRETACIÓN** (modelo lógico derivado para el informe, no necesariamente esquema técnico publicado) y **PENDIENTE** (no se ha localizado evidencia pública primaria suficiente para afirmarlo).

## Hallazgos

### REAFA: una explotación, titularidad y unidades productivas

**VERIFICADO.** El Decreto andaluz 190/2018 (9 de octubre; BOJA 15 de octubre de 2018) crea REAFA como registro administrativo único en materia de explotaciones agrarias y forestales. El registro contiene datos personales y económicos del titular, naturaleza jurídica, NIF y régimen de tenencia (incluye, entre otros, propiedad, arrendamiento y aparcería); si existe, identifica al representante con nombre, apellidos y NIF. Para la explotación registra unidades de producción, aprovechamientos, cultivos según codificación REGEPA, variedades, localización, parcelas catastrales y recintos SIGPAC relacionados, superficies y delimitaciones gráficas. También contiene caracterizaciones específicas, autocontroles cuando proceda y códigos de otros registros/sistemas interoperables. [1]

**VERIFICADO.** Cada explotación recibe un código REAFA al asiento de alta. Es una clave alfanumérica que identifica unívocamente cada explotación registrada y permanece invariable mientras esta exista aunque cambie con el tiempo. La norma no define en el texto consultado el patrón/formato legible del identificador (longitud, segmentos o dígito de control). Los asientos de alta, modificación y cancelación son secuenciales y conservan trazabilidad temporal de la explotación y sus unidades de producción. [1]

**VERIFICADO.** La Orden andaluza de 27 de octubre de 2019 (BOJA 15 de noviembre de 2019), art. 4, dispone que tanto la explotación como sus unidades de producción se visualizan como terreno delimitado digitalmente, con los aprovechamientos superpuestos en capas. El alta/modificación se hace por declaración responsable y las modificaciones deben notificarse; la Sede de Junta describe inscripción a instancia del titular o habilitado, o de oficio, y actualmente indica presentación telemática mediante Sga Rex. [2] [3]

**VERIFICADO.** La inscripción no acredita titularidad de bienes o derechos ni sustituye autorizaciones sectoriales. Es información administrativa declarada y la responsabilidad por veracidad de los datos declarados recae en el titular. Por tanto, la inscripción de un recinto en una explotación no debe interpretarse como título de propiedad o prueba autónoma del arrendamiento. [1] [3]

**INTERPRETACIÓN.** En un modelo de datos, conviene tratar como entidades distintas: titular/operador (persona o entidad), explotación (código REAFA persistente), unidad de producción (agrupación productiva/geográfica), relación temporal de tenencia o disponibilidad, parcela catastral, recinto SIGPAC, cultivo/aprovechamiento y campaña. No deben fusionarse «finca» en sentido coloquial o jurídico, parcela catastral y recinto SIGPAC: las fuentes usan conceptos distintos y el REAFA puede enlazar una explotación con muchas parcelas y recintos.

### Identificadores y SIGPAC

**VERIFICADO.** SIGPAC es una base/registro oficial dependiente del FEGA y de las consejerías autonómicas. Constituye la referencia geográfica para ayudas por superficie y también una base de referencia para facilitar en SIEX la información de superficies y cultivos. Un recinto es una superficie continua dentro de una parcela catastral, con un único uso SIGPAC; el agricultor es responsable último de la veracidad y correspondencia con la realidad de los recintos declarados. [4]

**VERIFICADO.** En los datos vectoriales descargables por FEGA (especificación técnica publicada en febrero de 2026), la referencia de recinto se descompone en código provincial, municipio SIGPAC/catastral, agregado, zona, polígono, parcela y recinto; los ficheros se distinguen también por campaña. La especificación define el recinto como superficie continua geográficamente delimitada y estable, medible, dentro de parcela SIGPAC, de uso único y con referencia alfanumérica única e inequívoca. La geometría y atributos incluyen superficie, uso SIGPAC, incidencias y otros atributos como pendiente/altitud y coeficientes pertinentes. [5]

**INTERPRETACIÓN.** La clave práctica del recinto no es el código REAFA: REAFA identifica la explotación, mientras la referencia SIGPAC localiza el recinto dentro del parcelario y la campaña describe datos geográficos/atributos válidos en un ciclo concreto. La asociación entre ambas debe ser una relación muchos-a-muchos o una tabla de asignación temporal: una explotación puede abarcar múltiples recintos y, según disponibilidad/gestión, un recinto puede relacionarse con más de un operador en momentos distintos. La norma de REAFA no publica una estructura relacional de base de datos, por lo que esta es una propuesta conceptual, no una afirmación sobre implementación Sga.

**PENDIENTE.** No se ha hallado documentación pública oficial que establezca que el código REAFA sea calculado a partir de la referencia SIGPAC, ni que parcela catastral y recinto sean intercambiables, ni un algoritmo de conversión entre los identificadores. No codificar el código REAFA a partir del SIGPAC sin especificación oficial.

### SIEX, REA y vínculo con REAFA

**VERIFICADO.** El Real Decreto 1054/2022 (27 de diciembre; BOE 29 de diciembre) establece SIEX como conjunto interconectado de bases y registros administrativos. El Registro autonómico de explotaciones agrícolas (REA estatal) es gestionado por cada comunidad y agrupa información administrativa relativa a explotación agrícola y unidades de producción. El CUE, en cambio, no es un registro administrativo: es un sistema electrónico del titular/representante para anotar datos de actividad agrícola del anexo II. [6]

**VERIFICADO.** Andalucía declara que REAFA se constituye, a efectos del capítulo III del RD 1054/2022, como Registro Autonómico de Explotaciones Agrícolas de Andalucía. La página temática REAFA confirma Sga Rex, comunicación del CUE (Sga Cex) y opción de descarga de datos de explotaciones para CUE comerciales. [3] [7]

**VERIFICADO.** MAPA informa que la aplicación histórica REGEPA dejó de estar operativa tras el RD 1054/2022 y que el intercambio entre autoridades se realiza conforme a ese real decreto. A todos los efectos, el anterior código REGEPA se sustituye por el código SIEX; la definición se incorporó al RD 1054/2022 mediante el RD 1177/2023, de 27 de diciembre. El buscador MAPA permite consultar únicamente el código REGEPA histórico. [8] [9]

**PENDIENTE.** No se debe confundir el identificador interno alfanumérico REAFA del decreto autonómico con el código SIEX nacional, aunque estén relacionados por el intercambio/interoperabilidad. Las páginas revisadas no publican una tabla de equivalencias detallada, algoritmo de asignación ni prueba de que ambos identificadores tengan la misma cadena o cardinalidad. Confirmar para integraciones mediante esquema SIEX vigente o la autoridad autonómica.

### CUE: qué guarda y UHC

**VERIFICADO.** El artículo 9 y anexo II del RD 1054/2022 distinguen los datos generales del cultivo en cada parcela agrícola de los registros de actividad. La autoridad incorpora los datos generales del cultivo desde REA y debe habilitar sincronización entre REA y CUE. El titular puede agrupar declaraciones gráficas de cultivo (DGC) en Unidades Homogéneas de Cultivo (UHC), definidas funcionalmente por recibir las mismas prácticas de cultivo. Por UHC se consignan tratamientos fitosanitarios; la fertilización se registra según normativa de nutrición sostenible del suelo, además de otros extremos que exija normativa PAC o sectorial. [6]

**VERIFICADO.** El marco temporal ha cambiado: la página oficial REAFA recoge la actualización del RD 34/2025 (21 de enero). Indica aplazamiento general de la obligación del CUE digital al próximo marco PAC, con excepción aplicable a explotaciones pequeñas, y obligación desde 1 de enero de 2026 limitada a consignar información de tratamientos fitosanitarios. Para obligaciones concretas, umbrales y excepciones debe leerse el texto consolidado vigente del RD 1054/2022 y normativa sectorial, no extrapolar el calendario original de 2022. [7] [10]

**INTERPRETACIÓN.** UHC es una agrupación operativa para registrar acciones homogéneas; no sustituye necesariamente recinto SIGPAC, parcela, cultivo ni unidad REAFA. Una UHC puede agrupar varias DGC si comparten las mismas prácticas, y el CUE registra eventos/insumos aplicados a esa agrupación. La delimitación y el cultivo base se vinculan al REA/parcelas, pero la práctica ejecutada se anota en CUE.

**PENDIENTE.** No está verificado que el sistema imponga una UHC por recinto, ni una sola UHC por cultivo o campaña; tampoco que todos los campos de un CUE comercial sean exigibles por igual a cada explotación. Es preciso distinguir mínimo legal del anexo II, campos de aplicación autonómica y campos añadidos por software comercial.

### Tenencia, representación, asesores y servicios

**VERIFICADO.** El régimen de tenencia forma parte de los datos de titularidad REAFA, e incluye expresamente categorías como propiedad, arrendamiento y aparcería. El diseño de la norma no demuestra que REAFA sea un registro de contratos: no se ha verificado en las fuentes consultadas la obligatoriedad de guardar contrato, fechas, renta o documentos probatorios en el asiento ordinario. [1]

**VERIFICADO.** Junta permite habilitar a personas físicas o jurídicas para trámites electrónicos en representación del titular, previa autorización escrita del titular y habilitación/documento aprobado por la Dirección General. El portal diferencia acceso a Sga Rex/Sga Cex y descarga de datos para CUE comerciales. La novedad de 5 de noviembre de 2026 actualiza entidades habilitadas hasta el 3 de noviembre y señala que las actualizaciones se efectúan generalmente los días 1 y 15. [3] [7]

**VERIFICADO.** La sede distingue entidades reconocidas, que pueden consultar datos de REAFA/CUE de interesados a quienes presentan solicitud única, de entidades habilitadas con capacidades de gestión/mantenimiento sujetas a habilitación. No es correcto asumir que toda entidad reconocida, asesor o proveedor comercial posee automáticamente permiso para modificar la explotación o el CUE. [3]

**PENDIENTE.** En las fuentes abiertas consultadas no se ha identificado una categoría legal autónoma de «asesor» con acceso general a REAFA/CUE. El acceso parece depender del consentimiento/habilitación y de la función concreta. Confirmar permisos, alcance del mandato, revocación, seguridad y tratamiento de datos del proveedor en la aplicación/acuerdo aplicable.

## Ejemplo de olivar: tres fincas y varios recintos

Ejemplo conceptual, no registro real: la titular **Olivarera X** explota un olivar y consta como titular de **una explotación REAFA** (código alfanumérico asignado por el registro). Se organizan tres unidades geográficas denominadas convencionalmente Finca A, B y C; el término «finca» aquí es etiqueta de gestión, no una afirmación de identidad catastral.

* Finca A comprende una parcela catastral con dos recintos SIGPAC (R1 y R2), por ejemplo, usos o delimitaciones distintas.
* Finca B comprende dos parcelas catastrales y tres recintos SIGPAC (R3–R5).
* Finca C comprende una parcela catastral con un recinto (R6).

En REAFA, la explotación enlaza cada unidad/aprovechamiento con la geometría, superficies, cultivos/variedades y referencias de parcela catastral y recinto SIGPAC. El titular declara el régimen de tenencia pertinente para los elementos que componen la explotación. La referencia concreta de cada recinto incluye su clave SIGPAC completa, no el nombre coloquial de la finca. [1] [2] [5]

Para la campaña pertinente, los datos de cultivo de cada parcela/DGC (olivar) se sincronizan desde REA hacia CUE. Si todas las DGC R1–R6 reciben la misma práctica de manejo/tratamiento, el titular podría agruparlas en una UHC; si difieren las prácticas, debe representarlas en agrupaciones homogéneas separadas para reflejar lo aplicado. En CUE se anotan las actuaciones sujetas al contenido mínimo vigente —en particular tratamientos fitosanitarios desde 2026 bajo el calendario descrito por Junta— y los otros datos exigibles según norma sectorial. La agrupación no convierte los seis recintos en un solo recinto ni altera el identificador REAFA. [6] [7]

**INTERPRETACIÓN / límite del ejemplo.** La existencia de exactamente tres unidades llamadas fincas y la configuración 2+3+1 recintos son datos ilustrativos. La norma no exige ese número ni prescribe esa agregación. En operación real habrá que respetar geometrías y datos SIGPAC vigentes, disponibilidad jurídica/material de las superficies, campaña y requerimientos aplicables al cultivo.

## Conclusión operativa

**VERIFICADO:** REAFA responde a «quién explota qué» en Andalucía y conserva un identificador de explotación persistente, relaciones de tenencia, unidades, cultivos y vínculos espaciales con parcelas y recintos. SIGPAC aporta la unidad geográfica de referencia de superficie, con clave y geometría dependientes de sus datos/campaña. SIEX integra/intercambia información administrativa nacional; el REA andaluz es el componente autonómico de explotaciones agrícolas. CUE registra la actividad agrícola, con agrupación opcional de declaraciones en UHC según prácticas comunes. [1] [4] [5] [6] [8]

**PENDIENTE para diseño definitivo:** confirmar en especificación técnica autonómica/nacional el formato y equivalencias de identificadores REAFA–SIEX, granularidad y reglas de vigencia de tenencias, esquema completo de unidades de producción, campos técnicos de Sga Rex/Cex, y requisitos de acceso de cada tipo de representante/asesor. No inferirlos del código SIGPAC ni de formularios genéricos.

## References

[1]: https://www.juntadeandalucia.es/boja/2018/199/8 "Decreto 190/2018, de 9 de octubre, creación y regulación de REAFA"
[2]: https://www.juntadeandalucia.es/boja/2019/221/4 "Orden de 27 de octubre de 2019, procedimientos REAFA y declaración gráfica"
[3]: https://www.juntadeandalucia.es/servicios/sede/tramites/procedimientos/detalle/11737.html "Sede electrónica de Andalucía, procedimiento REAFA 11737"
[4]: https://www.fega.gob.es/es/pepac-2023-2027/sistemas-gestion-y-control/sigpac "FEGA, SIGPAC: descripción y funciones"
[5]: https://www.fega.gob.es/orig/pdfs/202602_Especificaciones_tecnicas_y_datos_a_descarga_SIGPAC_SIGC.pdf "FEGA, especificaciones técnicas y datos a descarga SIGPAC/SIGC, febrero de 2026"
[6]: https://www.boe.es/buscar/doc.php?id=BOE-A-2022-23054 "Real Decreto 1054/2022, de 27 de diciembre, SIEX, REA y CUE, texto consolidado"
[7]: https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/produccion-agricola/paginas/reafa.html "Junta de Andalucía, REAFA: novedades, trámites y entidades habilitadas"
[8]: https://www.mapa.gob.es/es/agricultura/temas/sanidad-vegetal/higiene_y_trazabilidad/registro_general_de_la_produccion_agricola_regepa "MAPA, Registro General de la Producción Agrícola y sustitución de REGEPA por SIEX"
[9]: https://www.boe.es/buscar/doc.php?id=BOE-A-2023-26460 "Real Decreto 1177/2023, de 27 de diciembre, modificación del RD 1054/2022 y otros reales decretos PAC"
[10]: https://www.boe.es/buscar/doc.php?id=BOE-A-2025-998 "Real Decreto 34/2025, de 21 de enero, modificación de SIEX y calendario del CUE"
