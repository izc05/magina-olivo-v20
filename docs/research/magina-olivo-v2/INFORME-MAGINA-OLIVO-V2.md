# Mágina Olivo V2 — informe final y especificación de requisitos y pruebas

**Fecha de corte declarada: 9 de noviembre de 2026.** Este informe sintetiza nueve informes de investigación guardados en `/home/ubuntu/magina-research/`. Separa **VERIFICADO** (explícito en una fuente consultada), **INTERPRETACIÓN** (requisito/propuesta derivada, no mandato ni comportamiento oficial confirmado) y **PENDIENTE** (requiere confirmación oficial, inspección de implementación o prueba). Las referencias numeradas se asocian a fuentes primarias al final.

> **Cautela temporal y jurídica.** El corte indicado cae en 2026 y algunas evidencias describen páginas, listados, aplicaciones y versiones fechadas en octubre/noviembre de ese año. Es una fotografía temporal, no garantía de vigencia futura ni validación independiente de cada sistema. Antes de liberar V2 o afirmar cumplimiento, volver a contrastar BOE/BOJA, la página de Junta, anexos técnicos FEGA, catálogos y el servicio desplegado. Los textos consolidados del BOE son informativos y no sustituyen las publicaciones oficiales. Las obligaciones aquí resumidas no son asesoramiento jurídico individual.

## 1. Resumen ejecutivo

Mágina Olivo V2 debe concebirse como **sistema de gestión agrícola local-first, con captura offline, trazabilidad y conectores administrativos desacoplados**, no como sustituto automático del REAFA, SIGPAC, Sga Cex o del CUE oficial. La explotación tiene identidad y estructura registral en REAFA; SIGPAC aporta la referencia geográfica de recintos; SIEX integra/intercambia datos administrativos; el CUE registra actividad agrícola y puede organizar parcelas/DGC en UHC por prácticas homogéneas. No se debe derivar el código REAFA del SIGPAC ni presentar el código REAFA y el SIEX como equivalentes sin contrato técnico publicado. [1] [2] [3]

**VERIFICADO — calendario regulatorio al corte.** No es correcto decir que todo CUE digital sea universalmente obligatorio desde 1-1-2026. El RD 1054/2022, reformado por RD 34/2025, deja voluntaria la cumplimentación digital salvo exigencia sectorial; la obligación general se aplaza al siguiente periodo PAC para supuestos de umbral, sin que el artículo citado fije aquí una fecha calendario concreta. Distinto es el **registro fitosanitario**, que sigue siendo obligatorio: los operadores agrarios pueden conservarlo en papel hasta 31-12-2026 y deben pasar a registro electrónico desde 1-1-2027 según la redacción consultada del RD 1311/2012 tras RD 1039/2025. La anotación electrónica CUE, cuando aplicable, tiene plazo máximo de un mes; no confundirlo con el plazo mensual de REAFA ni con la transmisión administrativa a SIEX. [4] [5] [6] [7]

**VERIFICADO — nutrición.** Desde 1-1-2026, las operaciones de aporte de nutrientes/materia orgánica se anotan en un mes, con excepciones por superficie y otros supuestos. El plan de abonado por unidad productiva rige desde 1-9-2026, con entrada anticipada y excepciones específicas. Zona vulnerable a nitratos, condicionalidad PAC, sistema hídrico y tamaño pueden añadir o modificar requisitos; la aplicación debe capturar configuración y evidencias por unidad/recinto, y evitar reglas universales no justificadas. [8] [9]

**INTERPRETACIÓN — requisitos prioritarios V2.** (1) Separar entidades «titular, explotación, unidad productiva, finca de gestión, parcela catastral, recinto SIGPAC, campaña, DGC/UHC y actividad»; registrar identificadores externos con fuente, vigencia y versión, sin confundir sus semánticas. (2) Proveer formularios de tratamiento fitosanitario que cubran campo por campo el mínimo legal y los campos técnicos condicionales del modelo FEGA/Junta; conservar catálogos/snapshots y validación configurable por versión. (3) Capturar localmente con ID estable, historial de cambios, cola duradera, adjuntos y estados explícitos: borrador local, pendiente, enviado, aceptado/rechazado por validación técnica, y confirmado por servicio externo. (4) Tratar IUWS como integración externa batch REST/JSON con autorización, certificados/JWT y contrato versionado; aislarla detrás de adaptador y no dar por probadas idempotencia global, reintentos, sandbox ni esquemas OpenAPI. (5) Migrar V1 con inventario real de Room/backend antes de diseñar mapping; no se recibió esquema ni sync V1. [10] [11] [12]

**Recomendación provisional para una nueva plataforma multiplataforma.** React Native + Expo + TypeScript para móvil Android/iOS; portal React web separado si escritorio es prioritario; SQLite local persistente y backend PostgreSQL administrado o propio. MapLibre React Native es candidato cartográfico. Seleccionar **una** capa local/sync tras spike (WatermelonDB o RxDB; Expo SQLite por sí solo no resuelve sincronización). Diseñar outbox idempotente, resolución de conflictos por entidad, migraciones, adjuntos reanudables, RLS/grants auditados y pruebas adversas offline. Esta es una recomendación técnica, no una solución validada ni presupuesto. **No constituye autorización para cambiar el baseline del repositorio actual, que fija Kotlin + Jetpack Compose y Android-first; cualquier cambio de arquitectura requiere la gobernanza/cambio de alcance de ese proyecto.** [13] [14] [15]

**Criterios de salida esenciales.** No lanzar sin (a) matriz de aplicabilidad normativa aprobada; (b) modelo de datos y diccionario de correspondencias externo; (c) prueba de pérdida de red, reinicio, duplicados y edición concurrente; (d) migración representativa reconciliada con rollback; (e) pruebas de permisos negativos/positivos y privacidad; (f) prueba IUWS con credenciales y datos autorizados en entorno acordado; (g) textos de producto que no confundan captura local con recepción/aceptación administrativa. Los umbrales cuantitativos deben acordarse con usuarios/equipo y medirse, no atribuirse a la normativa.

## 2. Matriz normativa y requisitos de cumplimiento

| Tema / fuente | Estado y regla al corte | Requisito verificable para V2 | Prueba de aceptación |
|---|---|---|---|
| CUE, REA, SIEX — RD 1054/2022 art. 9 y anexos, RD 34/2025 | **VERIFICADO.** CUE no es registro administrativo; digital voluntario salvo mandato sectorial. Obligación general futura en siguiente periodo PAC para >30 ha de cultivos permanentes y tierras de cultivo (excluidos pastos temporales), o >5 ha de regadío dentro de esas superficies, o invernadero >0,1 ha, con matices del precepto. No se debe inventar fecha exacta del siguiente periodo. SIEX central: actualización administrativa al menos dos veces al año; no equivale a envío mensual del titular. [4] | **INTERPRETACIÓN.** Parametrizar condición de digitalización por regla, fecha de vigencia y evidencia; informar por qué aplica. Mantener registros y exportación independientemente de interfaz. | Dataset de explotaciones bajo/sobre cada umbral y caso invernadero: resultado trazable a configuración y fuente. Probar que regla general no activa digitalización prematura ni anula regla sectorial. |
| Fitosanitarios — RD 1311/2012 art. 16 y Anexo III.I; RD 1039/2025 | **VERIFICADO.** Registro obligatorio y conservación mínima de tres años desde emisión. Papel permitido a operadores agrarios hasta 31-12-2026; registro electrónico específico desde 1-1-2027 según texto consultado. CUE electrónico, cuando exigible, anotación en plazo máximo de un mes. No concluir que todo 2026 tuvo que ser digital. [5] [6] | Registro inmutablemente identificable, fecha de aplicación, validaciones, soporte/documentos, exportación y alerta de plazo; permitir carga histórica en papel con procedencia. | Simular tratamiento en fechas antes/después del cambio: reglas de soporte correctas; recordatorio y cómputo del mes; exportar todos los datos requeridos; retención no inferior al mínimo sujeto a política aprobada. |
| Fertilización — RD 1051/2022 modificado por RD 934/2025 | **VERIFICADO.** Aporte de nutrientes/materia orgánica: asiento en un mes desde operación desde 1-1-2026, con exención para explotación ≤5 ha computables y regadío ≤1 ha, o pastos exclusivos no fertilizados; matices para pastos fertilizados/invernaderos. Plan por unidad productiva desde 1-9-2026; entrada anticipada para ciertos cultivos de regadío sembrados/plantados 1-mar–30-jun-2026. Plan exento, entre otros, en pastos no fertilizados y unidades ≤10 ha de secano o pastos/forrajes para autoconsumo. [8] [9] | Evaluador de aplicabilidad distinto para **asiento por aporte** y **plan**. Guardar unidad productiva, fecha, material, composición, dosis, forma, prestador/maquinaria cuando proceda, plan y soportes. | Tabla de verdad de superficie, regadío, uso y fechas; confirmar que exención del plan no elimina automáticamente anotación de fertilización ni obligaciones fitosanitarias. |
| REAFA — Decreto 190/2018, Orden 27-10-2019 y Junta | **VERIFICADO.** Alta/modificación dentro del mes siguiente al inicio/cambio; habilitación/representación con autorización; inscripción de oficio posible. Baja y trámites tienen su propio procedimiento. No equiparar a campaña PAC. [1] [3] | Guardar código REAFA recibido, titularidad/tenencia declarada, resguardo, estado, fechas de vigencia y fuente. Recordatorios de cambio registral separados de PAC. | Alta/modificación simulada produce aviso de plazo; el sistema no genera por sí mismo código REAFA ni declara una operación presentada sin justificante. |
| Nitratos, PAC y agua | **VERIFICADO.** Zona vulnerable, BCAM/ayuda y título de uso de agua pueden generar condiciones adicionales; no se deducen solo por municipio o cultivo. [9] | Configurar aplicabilidad a nivel recinto/campaña/ayuda y almacenar vínculo a resolución/cartografía y autorización de agua. | Casos dentro/fuera de zona vulnerable y con/sin ayuda/derecho de agua; no presentar alertas como dictamen sin datos oficiales vigentes. |
| Trazabilidad/operación comercial | **VERIFICADO.** Trazabilidad alimentaria exige poder identificar proveedores y destinatarios; no se acreditó obligación general de enviar albaranes, kilos, precios, costes, jornales o liquidaciones a CUE/SIEX. [16] | Separar módulos empresariales opcionales del registro normativo y controlar permisos/consentimiento de cooperativa/terceros. | Export de CUE no incorpora automáticamente nóminas, precios ni documentos de cooperativa; las funciones comerciales se activan por finalidad y permiso. |
| Nombres de normas | **VERIFICADO.** RD 1039/2025 no es reforma general SIEX/CUE ni de nutrición; su disposición final modifica la transición fitosanitaria consultada. RD 934/2025 reforma directamente nutrición; RD 939/2025 es norma de ayudas por incendios con modificación vinculada a RD 1054/2022. [6] [8] | Mantener catálogo de normas por artículos/efecto, no por semejanza de número o título. | Revisión de trazabilidad norma→regla del producto; ninguna regla vinculada a norma equivocada. |

**INTERPRETACIÓN — postura de producto:** mostrar «registro guardado en este dispositivo», «enviado», «procesado» y «aceptado/validado» como estados distintos. No comunicar «cumple SIEX» por el mero hecho de permitir capturar campos. Ofrecer exportación y evidencia de presentación; el titular conserva la responsabilidad sobre veracidad. [4] [10]

## 3. Matriz completa de campos fitosanitarios, campo por campo

La base sustantiva es art. 16 y Parte I del Anexo III del RD 1311/2012. La Junta publica el modelo de cuaderno V9 (10-03-2026); el Anexo V FEGA pertenece al Documento Técnico de Coordinación SIEX v3.11.0, vigente desde noviembre de 2025, orientativo y no norma autónoma. El formulario V9 3.1 bis se refiere a superficies objeto de asesoramiento. La matriz distingue mínimo legal de campos técnicos y de diseño. [5] [10] [17]

| Campo | Obligatoriedad / condición | Diseño y validación V2 (INTERPRETACIÓN salvo donde se indica) | Prueba de aceptación |
|---|---|---|---|
| Fecha de tratamiento | **VERIFICADO: universal** | Fecha local; guardar zona horaria/offset y timestamp aparte. El campo hora aparece en modelo técnico, no como mínimo literal general; opcional si interfaz no lo exige. | Rechazar fecha ausente/futura según regla acordada; preservar fecha local tras cambio de zona. |
| Parcela / lugar / medio | **VERIFICADO: universal**, según contexto: parcela agrícola o local/medio de transporte | Referencias externas separadas: explotación REAFA, parcela catastral, recinto SIGPAC/campaña, UHC; superficie efectivamente tratada. No colapsar los identificadores. | Selección de recinto válida en campaña; tratamiento en local permite su tipo de localización, no fuerza SIGPAC. |
| Superficie tratada / volumen | **VERIFICADO: superficie o volumen según caso** | Valor positivo, unidad explícita; contrastar contra superficie cultivada con excepción documentada. En local conservar volumen tratado m³ cuando corresponda. | Rechazar valor negativo/ceros; alertar si excede área vinculada y permitir justificación auditada. |
| Plaga / organismo / problema | **VERIFICADO: universal** | Código de catálogo FEGA si disponible más texto legible; categoría sin inventar códigos; separar objetivo de observaciones. | Código y etiqueta preservados; entrada fuera de catálogo marcada para revisión, no recodificada silenciosamente. |
| Aplicador | **VERIFICADO: universal; identificación** | Persona o empresa, nombre/razón social e identificador disponible; NIF/ROPO y rol según aplicabilidad; tratar datos personales con minimización. | Requerir identidad de aplicador; datos adicionales condicionales según actividad. |
| Asesor | **VERIFICADO: condicionado** a existencia de asesoramiento | Identidad, ROPO, fecha, contrato/validación/firma si flujo aplicable. No exigir universalmente. | Sin asesor, asiento válido en flujo no asesorado; si marcado asesorado, exigir campos requeridos por flujo V9/técnico aplicable. |
| Cultivo, especie y variedad | **VERIFICADO: universal** | Código de cultivo y texto; especie/variedad; vincular a parcela/unidad y campaña. | No aceptar tratamiento sin cultivo; comparar con cultivo registrado y registrar discrepancias como excepción, no sobrescribir REA. |
| Estado fenológico | **VERIFICADO: campo técnico**, no mínimo legal general explicitado | Catálogo FEGA cuando exista; permitir «no conocido/no aplica» si esquema de intercambio lo permite; no inventar códigos. | Ensayar catálogo vigente y validar compatibilidad con versión del payload. |
| Justificación/umbral/alternativas | **VERIFICADO: condicionado**; V9 3.1 bis para superficie asesorada; principios GIP aplicables | Capturar monitoreo, umbral y medidas no químicas/químicas en flujo asesorado; evitar universalizar subcampos del formato asesorado. | La configuración de asesoramiento activa el formulario/validaciones pertinentes; flujo básico no bloqueado por datos no universales. |
| Producto comercial y nº de registro MAPA | **VERIFICADO: universal** | Guardar nº de registro, nombre comercial y snapshot/versionado de autorización; tipo, materia activa/formulación según esquema técnico. Validar autorización para cultivo, uso, plaga y fecha, no solo nombre. | No seleccionar producto no vigente/incompatible sin advertencia/bloqueo definido por fuente; conservar snapshot usado. |
| Dosis | Campo técnico FEGA, con exclusión frente a cantidad según campos definidos | Valor, unidad, base (por ha, concentración u otra según catálogo); no confundir dosis y total consumido. | Combinaciones dosis/cantidad respetan exclusión del esquema; validar unidades y etiqueta del producto según datos consultados. |
| Cantidad total producto | **VERIFICADO: mínimo legal exige kg/l empleados** | Cantidad total y unidad kg/l; conservar separada de dosis por ha. | Cantidad obligatoria y unidad compatible; impedir conversión sin densidad/concentración documentada. |
| Volumen de caldo / volumen tratado | **VERIFICADO: condicionado** al contexto; volumen de caldo por ha no mínimo universal | Campo separado de cantidad de producto; volumen local en m³ cuando proceda; unidad y base explícitas. | Un registro de cultivo no requiere automáticamente volumen de caldo; tratamiento de local acepta volumen contextual. |
| Equipo de aplicación | **VERIFICADO: universal identificar equipo**; número registral si procede | Método manual/mecanizado, tipo, equipo y ROMA/REGANIP si corresponde; potencia, energía, repeticiones/duración solo según esquema/caso. | Identificar equipo; número requerido solo por aplicabilidad; permitir aplicación manual sin datos de máquina incompatibles. |
| Eficacia | **VERIFICADO: universal** según Anexo III | Código compatible con catálogo; modelo V9 indica buena/regular/mala; fecha de evaluación opcional/recomendable y observaciones. | Eficacia no se infiere del producto; valor de catálogo o excepción trazable. |
| Observaciones | **VERIFICADO: observaciones pertinentes** | Texto con longitud/saneamiento acordados, sin sustituir campos estructurados; datos sensibles minimizados. | Export conserva texto y no trunca sin aviso. |
| Plazo de seguridad | **PENDIENTE como campo autónomo universal** en mínimo y plantilla consultados | Puede almacenarse como snapshot informativo derivado de etiqueta para uso/fecha concretos; nunca marcar campo genérico como mandato universal. | La ficha concreta se conserva y presenta; falta de dato no bloquea campos mínimos salvo regla validada aplicable. |
| Adjuntos/documentación | No consta como campo universal de asiento; conservación de soportes aplicables sí es relevante | Adjuntar etiquetas, factura, asesoramiento, inspección/documentación pertinente, con hash, MIME, fecha y entidad vinculada. | Descarga íntegra autorizada y conservación conforme a política; hash de archivo verificado. |
| Excepción/autorización excepcional | **VERIFICADO: campos condicionales técnicos FEGA** | Activar los campos requeridos por la excepción concreta; versionar regla/catálogo. | Ensayo de caso ordinario y excepcional sin imponer campos condicionales al caso ordinario. |
| Corrección/auditoría/volcado | Plazo electrónico máximo de un mes **VERIFICADO** cuando aplicable; bitácora inmutable es **INTERPRETACIÓN** | ID estable, autor, timestamp, valor previo/nuevo, motivo, estado de envío, ID externo; no sobrescribir silenciosamente. | Corrección produce evento auditable; cada envío queda correlacionado; reintento no duplica. |

**Pendiente técnico:** no se verificó una especificación pública completa de validaciones Sga Cex/IUWS, catálogo interactivo descargable, comportamiento de campo a campo en el despliegue andaluz ni esquema OpenAPI. RegFiWeb se actualiza semanalmente y debe consultarse para uso/producto concretos; no se incluye lista de productos de olivar inferida o no reproducible. [10] [18]

## 4. Mapa REAFA–SIGPAC–SIEX–IUWS–CUE

```text
Titular/representante con autorización
      │ declara / habilita (no implica por sí solo permiso de proveedor)
      ▼
REAFA (Andalucía; registro REA) ── unidades, cultivo, tenencia declarada,
      │                           parcela catastral, recinto y geometría
      │ sincronización/descarga autorizada
      ├──────────────────────────────────────────────────┐
      ▼                                                  ▼
SIGPAC (referencia espacial/campaña)               SIEX (interoperabilidad estatal)
      │                                                  ▲
      │ clave/geom./uso de recinto                       │ transmisión administrativa;
      │                                                  │ CUE central al menos 2 veces/año
      ▼                                                  │ (no envío mensual del usuario)
DGC / UHC del CUE comercial o público ── IUWS REST/JSON ┘
      │ actividad: tratamientos, fertilización y otros datos exigibles
      └── app V2: captura local → outbox → backend → IUWS (si autorización y contrato)
```

**VERIFICADO.** REAFA da código alfanumérico persistente de explotación; SIGPAC identifica recintos mediante componentes provinciales/municipales/agregado/zona/polígono/parcela/recinto y campaña; REAFA relaciona explotación y unidades con parcelas catastrales/recintos, cultivos y superficies. REAFA no acredita título de propiedad. SIEX integra/interconecta bases; REAFA cumple función de REA autonómico andaluz. CUE no es registro administrativo: consigna actividad. DGC puede agruparse en UHC si comparten prácticas de cultivo. [1] [2] [4]

**INTERPRETACIÓN — modelo canónico requerido:** `Holding/Operator` → `Holding (rea_id)` → `ProductionUnit` → relaciones temporales a `CadastralParcel` y `SigpacRecinto(campaign, key, geometry/version)` → `CropDeclaration/DGC` → `UHC` → `Activity` (treatment, nutrient, irrigation u otro). Mantener nombres coloquiales de finca como etiqueta de gestión, no clave oficial. Guardar identidad REAFA, código SIEX y referencia SIGPAC en campos separados, source/version/effective dates y mapping table; permitir muchos-a-muchos y temporalidad donde los datos reales lo requieran.

**PENDIENTE.** No hay patrón técnico público del código REAFA ni equivalencia demostrada con SIEX; no se ha confirmado esquema completo de temporalidad de tenencia, reglas exactas UHC ni permisos IUWS por tipo de asesor/proveedor. La asociación muchos-a-muchos es una propuesta de modelo, no descripción de Sga. No inferir código REAFA del SIGPAC ni asumir una UHC por recinto. [2]

## 5. Contrato de integración IUWS: solo lo verificable

**VERIFICADO.** El Anexo VI FEGA IUWS v3.11.4 (despliegue noviembre 2025) describe REST con intercambio JSON, certificados de persona jurídica y JWT para aplicaciones/entidades; flujo separado de productor individual validado por el organismo pagador. Explotación debe estar inscrita en REA. Se documentan importación asíncrona, consulta de estado y tamaño máximo de fichero de 2 MB. Junta publica para Andalucía el host `https://ws108.juntadeandalucia.es/IUWS/`; el anexo usa rutas descriptivas/genéricas y no debe tomarse como garantía de URL completa para producción. [11] [12]

| Operación descrita | Contrato verificable (sin completar lo no publicado) | Requisito de integración V2 |
|---|---|---|
| `comunicacion/{nombreAppCue}` | Autentica servidor/app con certificado válido y devuelve JWT; dominios/aplicación se asocian al registro. | Certificados fuera del cliente móvil; almacén seguro y renovación operativa por definir. No asumir valores de `exp`/códigos HTTP. |
| `comunicacionProductor/{nombreAppCue}/{peticion}` | Inicia validación de productor particular por aplicación del organismo pagador; JWT incorpora NIF (`cif` en descriptor); productor debe ser titular. | Adaptador independiente; no asumir proveedor/flujo OAuth/Cl@ve/callback concreto. |
| `exportarREA/{NIF}_{CIF Gestora}` | Exportación de datos mínimos REA sujeta a autorización; CIF de gestora se omite para productor particular. | Guardar petición, autorización y respuesta de forma auditable; schema y catálogo deben fijarse por versión. |
| `exportar/{Nº explotación}_{CIF Gestora}` | Exporta actividades CUE; según anexo, incluye actividades completas y reconoce CUE que importó cada actividad; autorización necesaria. | Conciliar respuestas sin sobrescribir el origen local; distinguir identidad de autoría/origen externo. |
| `crear/` POST | JSON con uno o más cuadernos, cada uno con una explotación y actividades; puede crear/modificar/borrar actividades, alta DGC/cambio de cultivo; relaciones dependientes juntas. Identificador externo repetido de actividad se interpreta como edición salvo si ya se envió a SIEX; borrado mediante campo indicado. DGC repetida en alta da error. Respuesta inmediata con ID petición; proceso batch posterior. | Lotes ≤2 MB; ID estable por actividad; tratar rechazo parcial; no equiparar repetición del payload a idempotencia global. Capturar estado y errores originales. |
| `comprobarEstado/{Id Petición}` | Consulta el procesamiento asíncrono; pendiente puede devolver resultado OK con detalle código 3; final incluye resultado/incidencias y estado válido por actividad/ID de cuaderno. | Sondeo configurable tras confirmación; conservar `Id Petición`; sin política de reintento/retención supuesta. |

**INTERPRETACIÓN — protocolo seguro propuesto:** interfaz anticorrupción y adaptador versionado; mapeo explícito desde modelo canónico; export/import en JSON validado; batch fragmentado; outbox local con `external_activity_id` estable; registro de petición/respuesta y checksum; máquina de estados separado (local, enviado, en proceso, válido, rechazo/incidencia); reintento solo después de confirmar semántica; conciliación y visibilidad de errores al usuario. Nunca reportar éxito administrativo por una respuesta de transporte solamente.

**PENDIENTE antes de producción:** OpenAPI/JSON Schema formal vigente; formatos/catálogos/campos por versión; base URL de pruebas; certificado y atributos aceptados; flujo productor/callback; OAuth u otro mecanismo (no inferir); códigos HTTP/negocio exhaustivos; expiración JWT; rate limits, reintentos, retención de batch y estados terminales; rectificación/borrado tras remisión SIEX; revocación de consentimiento y efecto; reglas de cambio de dominio/software; ejemplos completos. El anexo describe REST/JSON; no acredita WSDL, SOAP, XSD ni API key. [11] [12]

## 6. Comparativa de competidores y productos relevantes

La Junta publicó lista de software CUE comercial de 28-10-2026 con Agricolum, oSIGris, AGROPTIMA y Olearia. **VERIFICADO:** estar listado confirma inclusión publicada, no calidad certificada, cuenta conectada ni transacción IUWS probada. Funciones de proveedores son declaraciones comerciales salvo fuente institucional independiente. [19] [20]

| Producto | Evidencia de funciones/olivar | Offline, integración y precio al corte | Lectura para Mágina (INTERPRETACIÓN) / prueba pendiente |
|---|---|---|---|
| Agricolum | Cuaderno, labores, costes, personal/maquinaria, informes; SIGPAC y base de fitosanitarios anunciados. | Proveedor declara offline y sincronización al recuperar red; planes publicados: gratuito, 168 € + IVA/año, 336 € + IVA/año; figura en lista Junta. IUWS no probado por cuenta. | Referente de bajo umbral de entrada y gestión horizontal. Probar conflicto multiusuario, cobertura offline real, fichas de uso y export/import andaluz. [19] [21] |
| Agroptima | Labores/partes, órdenes, costes, informes/certificaciones; declara adaptación a olivar. | Proveedor declara offline y SIGPAC; precio por hectáreas/usuarios, cotización; listado Junta. No verificada transacción IUWS. | Referente para operación de equipos y explotación multiusuario. Solicitar matriz offline, tarifa comparable y demo de cambio de recinto/REAFA. [19] [22] |
| oSIGris | Cuaderno, incidencias, tratamientos, fertilización, fenología, riego; asistente fitosanitario y conexión SATIVUM anunciada. | Offline y tarifa completa no confirmados; en listado Junta; integración IUWS/SIGPAC concreta pendiente. | Referente de asistencia fitosanitaria/fenología. Probar catálogo autorizado, conectividad, permisos asesor y aceptación de importación. [19] [23] |
| Olearia | Producto explícitamente olivarero: fincas, cosechas, entregas, aceite, cuadrillas, maquinaria y cuentas por finca/socio, según proveedor. | Proveedor declara offline, SIGPAC/SIEX; versión 1.4.0 y precios observados: gratuito hasta 10 ha, Plus 11,99 €/mes, Profesional 23,99 €/mes (condiciones del proveedor); listado Junta. Integración real no auditada. | Referente de flujo oleícola y diferenciación vertical. Verificar distribución, seguridad, cobertura rural, exactitud SIGPAC, liquidaciones e IUWS. [19] [24] |
| farmOS / Field Kit | Open source GPL; plataforma agrícola configurable; no se verificó módulo español de cumplimiento fitosanitario ni adaptación olivarera. | Hosting/operación aparte; estado offline/móvil vigente de Field Kit e IUWS no corroborados en evidencia revisada. No listado Junta consultado. | Referencia de extensibilidad, no sustituto inmediato de SaaS conforme. Revisar licencia GPL, hosting, UX y mantenimiento. [25] |
| RAIF App | Herramienta oficial Junta: visor SIG, alertas/estado fitosanitario, estaciones meteorológicas; no CUE general. | Gratuita; offline no acreditado. | Complemento posible a alertas oficiales con fuente/fecha; no duplicar asesoramiento oficial ni confundir con CUE. [26] |
| REUTIVAR | Aplicación especializada de riego/fertirriego de olivar, desarrollada con participación UCO; no cuaderno general. | La fuente consultada describe cálculo de programación de riego/fertilización; mantenimiento/precio/offline actuales pendientes. | Referencia de dominio hídrico; valorar integración de datos con consentimiento y validar versión vigente. [27] |

## 7. Comparativa tecnológica y recomendación

| Opción | Ventajas verificadas / encaje | Límites y riesgos | Decisión provisional |
|---|---|---|---|
| Kotlin + Compose + Room | Room: SQLite Android, consultas verificadas en compilación, migraciones; fuerte ajuste Android. | No resuelve sync ni iOS/web por sí solo; más clientes/toolchains si se requieren tres plataformas. | Mantener si V1 ya es nativa y Android domina; no migrar sin coste/beneficio y compatibilidad confirmados. [28] |
| React Native + Expo + TypeScript | Reutilización lógica móvil, ecosistema; EAS para builds; `expo-sqlite` persiste datos localmente. MapLibre RN es candidato GIS MIT. | SQLite no sync; mapa offline/teselas/licencia deben resolverse; dependencias nativas y versión Expo requieren spike. Web React no comparte automáticamente UX ni base móvil. | **Recomendación provisional** para producto nuevo o V2 multiplataforma; arquitectura local-first propia. [13] [14] [15] |
| Flutter | Un codebase multiplataforma y patrón documentado de repositorios offline-first. | Dart/equipo; sincronización sigue siendo propia; Flutter web debe validarse con tablas y workflows de gestión. | Alternativa si el equipo apuesta por Dart y UI compartida; spike comparativo antes de decisión irreversible. [29] |
| React web + Capacitor | Web-first, reutilización alta de UI existente y APIs/plugins nativos. | WebView y mapas/uso intensivo en campo necesitan pruebas; almacenamiento browser/nativo distinto; plugins y segundo plano por validar. | Adecuado si escritorio/web es centro y móvil es captura auxiliar. [30] |
| WatermelonDB o RxDB | Opciones local-first TS; licencias MIT/Apache-2.0 según informes; ofrecen primitivas/adaptadores de sync. | No eximen de diseñar backend, conflictos, permisos, cola, adjuntos y compatibilidad Expo; revisar licencia de complementos. | Elegir una mediante spike con dataset real y red intermitente; no usar ambas. [31] [32] |
| ODK Collect / QField | Referencias maduras de formularios offline y GIS de campo; productos completos. | No son librerías Expo; ODK licencia no confirmada en informe; QField GPL requiere evaluación si se modifica/distribuye. | Benchmark UX o producto separado; no dependencia RN directa. [33] [34] |

**Recomendación arquitectónica para el producto multiplataforma solicitado:** Expo/RN móvil + portal React web independiente (UI separada; compartir esquemas, validaciones y lógica pura); SQLite offline; PostgreSQL backend; servicio API controlado; adjuntos en object storage con control de acceso; IUWS detrás de adaptador. Supabase es viable, pero requiere RLS por tabla expuesta, grants mínimos y pruebas negativas; `service_role` nunca en cliente. La guía Expo indica que herramientas local-first evolucionan y la sincronización puede requerir implementación propia. El repositorio actual mantiene un baseline aprobado Kotlin + Jetpack Compose, Android-first; publicar esta investigación no cambia ese baseline ni autoriza reescritura, y el eventual cambio debe pasar por su proceso de control. [13] [14] [35]

## 8. Arquitectura objetivo y especificación funcional/no funcional

### Componentes

1. **App móvil:** RN/Expo, captura de tratamiento/fertilización/riego, parcelas y tareas; modo offline completo para alcance acordado; mapas con capas descargables si se valida fuente/teselas/licencia.
2. **Persistencia local:** SQLite; migraciones numeradas; IDs UUID generados una sola vez; outbox durable; estado de sincronización; metadatos de actor/dispositivo y marca temporal; adjuntos en almacenamiento local seguro. SQLCipher es opción a probar/configurar, no garantía automática de cifrado.
3. **Dominio y esquema:** modelo canónico independiente de tablas FEGA/IUWS; catálogos externos versionados; adaptadores para SIGPAC, REAFA, SIEX y CUE sin acoplar pantallas al formato externo.
4. **Backend:** PostgreSQL fuente autoritativa para datos compartidos; API idempotente y paginada; control de concurrencia; auditoría; RLS/grants por tenant/rol; almacenamiento de adjuntos separado con ACL y subida reanudable. Revisar privacidad, retención y jurisdicción de alojamiento.
5. **Integración IUWS:** servicio servidor a servidor, certificado/JWT según habilitación oficial; logs de auditoría sin secretos; lotes ≤2 MB según anexo; consulta batch; estados explícitos. Certificados y credenciales no se incluyen en la app móvil.
6. **Portal web:** gestión de usuarios, informes, correcciones, catálogos, permisos, conectividad y resolución de conflictos; evidencia de sincronización administrativa y herramientas de exportación.
7. **Observabilidad:** métrica de antigüedad de cola, resultados batch, conflictos, errores/reintentos, versiones de catálogo y última sincronización; minimizar datos personales en logs.

### Requisitos de calidad propuestos (INTERPRETACIÓN; umbrales por aprobar)

- **OFF-01:** En modo avión, crear/editar/consultar registros del alcance piloto; tras cierre forzado y reinicio no se pierde ningún cambio confirmado localmente.
- **SYNC-01:** recuperar conexión y procesar outbox sin duplicar; cada operación termina aplicada o visible como accionable; medir p50/p95 bajo red/dispositivo de referencia acordados.
- **CON-01:** dos dispositivos editan offline el mismo registro; política por entidad/campo produce resultado determinista o conflicto explícito. Cantidades, tratamientos enviados y documentos no se sobrescriben silenciosamente.
- **SEC-01:** pruebas API directas de lectura/escritura/borrado demuestran aislamiento entre titulares/organizaciones y privilegio mínimo; secreto privilegiado solo servidor.
- **DATA-01:** toda referencia SIGPAC conserva clave, campaña y fuente; relación externa no reemplaza código interno/REAFA. Sincronizar geometría sin alterar trazabilidad histórica.
- **REG-01:** formularios validan el mínimo fitosanitario y condiciones de registro; reglas por fecha y tipo de explotación son configurables/versionadas.
- **AUD-01:** corregir datos produce historia con quién/cuándo/motivo según política aprobada; conservar payload y resultado IUWS vinculados a la actividad.
- **MIG-01:** rutas de migración desde cada versión V1 soportada preservan datos y cola; conciliación de recuentos, claves, relaciones y hashes de adjuntos; rollback restaurado en ensayo.
- **ACC-01:** formularios y estados accesibles, legibles en campo y utilizables con conectividad variable; probar teléfonos/dispositivos reales y usuarios representativos.

## 9. Plan de implementación y migración

No se recibió esquema Room/SQL ni implementación V1 de sync; no se atribuyen tablas ni relaciones existentes. Los nombres «fincas, parcelas, jornales, pesadas, gastos» son áreas que auditar, no evidencia de tablas. Este límite está documentado en el informe fuente 09-migracion-plan.md, conservado en los anexos.

| Hito | Entregable | Gate y pruebas de salida |
|---|---|---|
| 1. Alcance y reglas | Brief, territorios, perfiles, flujos y matriz normativa por explotación | Gate jurídico A: identificar obligaciones y finalidad de cada dato antes de congelar reglas. |
| 2. Auditoría V1 | Room exportado por versión, esquema backend, sync, storage, permisos, catálogo de IDs | Perfilado reproducible: huérfanos, duplicados, nulos, unidades, fechas, colas/adjuntos; no mapping sin inventario. |
| 3. Modelo V2 | Diccionario campo-a-campo y grafo de entidades; separación negocio/administrativo | Gate jurídico B: aprobar campos, conservación, corrección, exportación, privacidad y relación SIGPAC/REAFA. |
| 4. Contrato de sync | UUID/idempotency, outbox, ACK, cursores, conflicto, borrado/tombstone, adjuntos | Reintento repetido no duplica; orden invertido y dos dispositivos dan resultado definido. |
| 5. Backend/seguridad | Migraciones SQL, RLS, grants, buckets, auditoría, API | Gate privacidad C: pruebas allow/deny; revisar datos personales, encargados, retención, backups y logs. |
| 6. Migraciones locales | Migraciones Room/export y transición al cliente V2 | Probar cada versión publicada y upgrades completos; fallo no deja BD parcialmente utilizable. |
| 7. Ensayo de migración | Copia anonimizada, mapping, staging, informe excepciones, rollback | Conciliar claves, cardinalidades, sumas y hashes; una segunda ejecución no duplica. |
| 8. Spike IUWS | Certificados/autorización y operaciones autorizadas de sandbox/producción de prueba acordada | Confirmar contrato/catálogos, error parcial, tamaño y aceptación; no usar producción sin autorización. |
| 9. Piloto offline | Cohorte acotada, Android/iOS, zonas con cobertura débil | Reinicio, pérdida de red, conflicto, actualización app, carga adjunto, permisos y soporte; cola cero pérdida. |
| 10. Corte gradual y cierre | Feature flag/cohortes, monitorización, rollback y retirada V1 | Gate jurídico D; sin cola irresuelta ni divergencias no explicadas; retirar V1 solo tras observación y restauración probadas. |

**Estrategia de identidad y datos (INTERPRETACIÓN):** conservar UUID V1 si único/estable; IDs numéricos legacy quedan mapeados de forma auditable; no crear nueva clave en cada reintento. Mantener los datos históricos de campaña/recinto y no sustituirlos por SIGPAC actual. Colas pendientes se migran separadas de hechos confirmados con payload original, dependencias, orden, estado, intentos y error; no reproducir automáticamente semánticas desconocidas. Ejecutar backup cifrado y restauración aislada antes de corte. Acordar retención limitada y rectificación con asesoría jurídica/privacidad. [2] [28]

## 10. Preguntas pendientes para Junta de Andalucía / FEGA

1. ¿Cuál es el contrato vigente completo del IUWS andaluz para producción y pruebas: URL base, versión, OpenAPI/JSON Schema, descriptores REA/CUE, catálogos y ejemplos válidos por campaña?
2. ¿Qué certificados cualificados se aceptan, qué atributos/dominio deben contener, cómo se renuevan/revocan y cuál es el proceso de instalación/validación mTLS/JWT? ¿Cuál es la duración/renovación de JWT?
3. En flujo productor individual, ¿qué aplicación del organismo pagador valida al usuario, cómo opera callback y cómo se vincula NIF/titular con el CUE comercial? No inferir Cl@ve u otro proveedor.
4. ¿Qué conjunto exhaustivo de errores HTTP y de negocio devuelve cada operación? ¿Qué estados batch existen, cuánto se retiene `Id Petición`, límites de frecuencia/tamaño y política oficial de reintentos seguros?
5. ¿Cómo se rectifica o elimina una actividad ya enviada a SIEX? ¿Qué ocurre si se reenvía el mismo identificador externo tras remisión? ¿Existe flujo formal de anulación/recuperación?
6. ¿Cómo se registra, conserva y revoca autorización del titular para entidad/software? ¿Qué evidencias y plazos deben conservarse, cómo se informa IUWS, y qué efecto tiene cambiar software, entidad, dominio o certificado?
7. ¿Cuál es el patrón/campo canónico de código REAFA y su relación con código SIEX? ¿Se publica tabla/endpoint de equivalencia, cardinalidad y reglas de vigencia?
8. ¿Cuáles son las reglas oficiales de agrupación de DGC en UHC, granularidad temporal y campos que obligatoriamente deben conservarse por unidad/campaña?
9. ¿Qué representa exactamente en Andalucía cada estado de aceptación y qué evidencia puede presentar el titular? ¿Hay diferencias entre procesado técnicamente, válido y comunicado a SIEX?
10. ¿Se publican reglas de validación/catálogos de RegFiWeb/Sga Cex, inclusión de hora, dosis frente a cantidad, autorización excepcional y plazo de seguridad? ¿Cuáles son los campos mínimos por tipo de tratamiento?
11. ¿Hay novedades posteriores a la evidencia 2026 que alteren calendarios CUE/REAFA, registro fitosanitario, fertilización, IUWS o SIGPAC? ¿Qué orden concreta las fechas de transmisión administrativa semestral SIEX?
12. ¿Qué requisitos de habilitación se aplican a empresa desarrolladora, entidad representante y asesor técnico, y cómo se verifica alcance de consentimiento en cada flujo?

**Gate recomendado:** obtener respuesta documentada y artefactos versionados antes de declarar integración IUWS productiva, conformidad reglamentaria integral, uso de identificadores oficiales o automatizar envíos reales.

## 11. Anexos de investigación conservados

Los nueve informes originales se conservan junto a este consolidado en la carpeta `anexos/` de este directorio:

- `01-normativa.md` — CUE, SIEX, REAFA, fitosanitarios y nutrición.
- `02-rea-sigpac.md` — modelo REAFA, SIGPAC, SIEX, UHC y representación.
- `03-fitosanitarios.md` — campos y Anexo V FEGA.
- `04-iuws.md` — IUWS y autorización comercial.
- `05-nutricion.md` — fertilización, riego, labores y trazabilidad.
- `06-competencia.md` — competidores y productos para olivar.
- `07-opensource.md` — repositorios, librerías, licencias y riesgos.
- `08-arquitectura.md` — tecnologías, arquitectura y criterios.
- `09-migracion-plan.md` — migración y hoja de ruta.

Estos informes constituyen el anexo de investigación fuente y se publican junto al consolidado. Sus referencias y alcances específicos completan la lista consolidada siguiente.

## References

[1]: https://www.juntadeandalucia.es/boja/2018/199/8 "Decreto 190/2018, de 9 de octubre, creación y regulación de REAFA"
[2]: https://www.fega.gob.es/es/pepac-2023-2027/sistemas-gestion-y-control/sigpac "FEGA, SIGPAC: descripción y funciones"
[3]: https://www.juntadeandalucia.es/servicios/sede/tramites/procedimientos/detalle/11737.html "Junta de Andalucía, procedimiento REAFA"
[4]: https://www.boe.es/buscar/doc.php?id=BOE-A-2022-23054 "Real Decreto 1054/2022, de 27 de diciembre, SIEX, REA y CUE, texto consolidado"
[5]: https://www.boe.es/buscar/act.php?id=BOE-A-2012-11605 "Real Decreto 1311/2012, de 14 de septiembre, uso sostenible de productos fitosanitarios"
[6]: https://www.boe.es/buscar/doc.php?id=BOE-A-2025-23421 "Real Decreto 1039/2025, de 19 de noviembre, modificación del plazo de registro fitosanitario y otras disposiciones"
[7]: https://www.boe.es/buscar/doc.php?id=BOE-A-2025-998 "Real Decreto 34/2025, de 21 de enero, modificación del RD 1054/2022 y calendario CUE"
[8]: https://www.boe.es/buscar/doc.php?id=BOE-A-2025-21211 "Real Decreto 934/2025, de 21 de octubre, modificación de normativa de nutrición sostenible"
[9]: https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/produccion-agricola/paginas/fertilizantes-rd-nutricion-sostenible.html "Junta de Andalucía, fertilizantes y RD de nutrición sostenible"
[10]: https://www.fega.gob.es/sites/default/files/files/document/2025.11.14-DT%20AGRICOLA_v3.11_CORRECCION%20ERRORES_LIMPIO.pdf "FEGA, Documento Técnico de Coordinación SIEX v3.11, Anexo V variables-campos"
[11]: https://www.fega.gob.es/es/siex/documentacion-tecnica-agricola-siex/anexo-vi-interfaz-unico-comun-3114 "FEGA, Anexo VI IUWS SIEX v3.11.4"
[12]: https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/cuaderno-explotacion.html "Junta de Andalucía, cuaderno de explotación, modelo V9 y acceso CUE"
[13]: https://docs.expo.dev/guides/local-first/ "Expo, guía de arquitectura local-first"
[14]: https://docs.expo.dev/versions/latest/sdk/sqlite/ "Expo SQLite, documentación oficial"
[15]: https://github.com/maplibre/maplibre-react-native "MapLibre React Native, repositorio oficial"
[16]: https://eur-lex.europa.eu/eli/reg/2002/178/oj "Reglamento (CE) n.º 178/2002, artículo 18, trazabilidad"
[17]: https://www.juntadeandalucia.es/sites/default/files/inline-files/2026/03/20260310_MODELO_DE_CUADERNO_DE_EXPLOTACION_v9.pdf "Junta de Andalucía, Modelo de Cuaderno de Explotación V9, 10-03-2026"
[18]: https://servicio.mapa.gob.es/regfiweb "MAPA, Registro Oficial de Productos y Material Fitosanitario (RegFiWeb)"
[19]: https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/cuaderno-explotacion.html "Junta de Andalucía, software CUE comercial disponible y procedimiento IUWS"
[20]: https://www.juntadeandalucia.es/sites/default/files/inline-files/2026/10/Empresas%20Desarrolladoras%20de%20CUE%20Comercial_261028.ods "Junta de Andalucía, relación de empresas desarrolladoras CUE comercial, 28-10-2026"
[21]: https://agricolum.com/precios "Agricolum, planes y precios"
[22]: https://www.agroptima.com/precios/ "Agroptima, precios"
[23]: https://osigris.com/ "oSIGris, producto y funcionalidades"
[24]: https://olearia.io/agritech "Olearia Agritech, producto y planes"
[25]: https://farmos.org/ "farmOS, plataforma agrícola libre"
[26]: https://www.juntadeandalucia.es/agriculturapescaaguaydesarrollorural/raif/raif-app/ "Junta de Andalucía, RAIF App"
[27]: https://www.uco.es/servicios/actualidad/ciencia/item/138194-lanzan-una-aplicacion-para-controlar-la-cantidad-optima-de-fertilizantes-y-agua-en-el-riego-del-olivar "Universidad de Córdoba, aplicación REUTIVAR para riego/fertirriego del olivar"
[28]: https://developer.android.com/training/data-storage/room/migrating-db-versions "Android Developers, migrar bases Room"
[29]: https://docs.flutter.dev/app-architecture/design-patterns/offline-first "Flutter, arquitectura offline-first"
[30]: https://capacitorjs.com/docs "Capacitor, documentación oficial"
[31]: https://watermelondb.dev/docs/Sync/Intro "WatermelonDB, introducción a sincronización"
[32]: https://docs.expo.dev/guides/local-first/ "Expo, herramientas y adaptadores local-first (incluido RxDB)"
[33]: https://docs.getodk.org/collect-intro/ "ODK Collect, introducción oficial"
[34]: https://docs.qfield.org/ "QField, documentación oficial"
[35]: https://supabase.com/docs/guides/database/postgres/row-level-security "Supabase, Row Level Security y privilegios"
