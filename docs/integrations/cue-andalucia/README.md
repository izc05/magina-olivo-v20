# CUE Andalucía y fitosanitarios — índice vivo de interoperabilidad

**Revisión:** 2026-10-09. **Owner producto:** propietario Mágina Olivo. **Issue maestro:** [#534](https://github.com/izc05/magina-olivo-v20/issues/534).  
**Clasificación:** requisito prioritario P0, documentación/arquitectura; **NO homologado, NO conectado a IUWS real, NO verificado con acuse oficial**.  
**Ejecución:** preparación documental compatible con [#696](https://github.com/izc05/magina-olivo-v20/issues/696) / [#711](https://github.com/izc05/magina-olivo-v20/issues/711). No autoriza desarrollo productivo paralelo ni saltarse Gate21 Android.

> Meta: que un agricultor registre una vez su tratamiento en Mágina Olivo, lo conserve offline con datos reglamentarios y, tras alta de CUE comercial, autorizaciones y pruebas de servicio, pueda intercambiarlo con el sistema oficial andaluz. Mantener separados «registro local», «sincronizado con backend» y «aceptado por Administración». No inducir a pensar que el cuaderno cumple formalmente mientras falten los gates.

## A. Enlaces canónicos: leer el detalle sin duplicarlo

| Materia | Documento/issue |
| --- | --- |
| Matriz funcional del cuaderno V9, explotación, fitosanitarios, riego y fertilización | [CUE-ANDALUCIA-IMPLEMENTATION-MATRIX.md](../../07-plans/CUE-ANDALUCIA-IMPLEMENTATION-MATRIX.md), [#535](https://github.com/izc05/magina-olivo-v20/issues/535) |
| Contrato IUWS 3.11.4, autenticación, operaciones, códigos, idempotencia y riesgos | [IUWS-3114-DISCOVERY.md](IUWS-3114-DISCOVERY.md), [#536](https://github.com/izc05/magina-olivo-v20/issues/536) |
| Variables digitales, campos, cardinalidad, decimales y contradicciones | [DIGITAL-FIELD-MATRIX.md](DIGITAL-FIELD-MATRIX.md), [#572](https://github.com/izc05/magina-olivo-v20/issues/572) |
| Registro oficial MAPA de productos fitosanitarios, versionado, cancelaciones y escenarios | [REGFI CONTRACT](../mapa-regfi/CONTRACT.md), [REGFI HANDOFF](../mapa-regfi/HANDOFF.md), [REGFI QA](../mapa-regfi/QA.md), [#553](https://github.com/izc05/magina-olivo-v20/issues/553), [#681](https://github.com/izc05/magina-olivo-v20/issues/681) |
| Alta de software, certificados y autorizaciones | [#555](https://github.com/izc05/magina-olivo-v20/issues/555), [#536](https://github.com/izc05/magina-olivo-v20/issues/536) |
| Estados administrativos distintos del estado offline | [#549](https://github.com/izc05/magina-olivo-v20/issues/549) |
| REAFA, SIGPAC, identidad legal y parcelas | [#540](https://github.com/izc05/magina-olivo-v20/issues/540), [#545](https://github.com/izc05/magina-olivo-v20/issues/545) |
| Backend/Supabase, autorización y sincronización entre dispositivos | [#335](https://github.com/izc05/magina-olivo-v20/issues/335), [#330](https://github.com/izc05/magina-olivo-v20/issues/330) |
| Histórico QA/estado cierre Android | [Matriz Android 1.0](../../qa/MAGINA-OLIVO-1.0-CLOSURE-MATRIX.md) |

**Aviso de vigencia:** la matriz de implementación tiene partes fotografiadas cuando el modelo estaba en Room v22; la PR #680 ya integró Room v25. No crear migraciones basadas en el encabezado histórico sin inspeccionar código y schemas de main.

## B. Fuentes oficiales comprobadas el 09/10/2026

| Fuente primaria | Uso | Versión/estado |
| --- | --- | --- |
| [Junta — Cuaderno de explotación](https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/cuaderno-explotacion.html) | CUE comercial, REAFA, alta, entidad habilitada, productor, IUWS, obligatoriedad, enlaces al modelo | Página consultada 09/10/2026; contiene actualización 02/10/2026 |
| [Modelo cuaderno V9 Junta (PDF)](https://www.juntadeandalucia.es/sites/default/files/inline-files/2026/03/20260310_MODELO_DE_CUADERNO_DE_EXPLOTACION_v9.pdf) | Apartados, categorías y datos de los registros | 10/03/2026, contrastado previamente en matriz |
| [RD 1311/2012 consolidado](https://www.boe.es/buscar/act.php?id=BOE-A-2012-11605) | Registro de tratamientos, obligaciones sectoriales | Consultar texto actualizado |
| [RD 1039/2025](https://www.boe.es/buscar/doc.php?id=BOE-A-2025-23421) | Prórroga de soporte papel agrario hasta 31/12/2026 | BOE 20/11/2025 |
| [FEGA — documentación técnica agrícola SIEX](https://www.fega.gob.es/es/siex/documentacion-tecnica-agricola-siex) | Anexo V 3.11, Anexo VI IUWS 3.11.4, catálogos y autorizaciones | Usar versión interna, checksum y sección, no solo slug web |
| [FEGA — documentación técnica horizontal SIEX](https://www.fega.gob.es/es/siex/documentacion-tecnica-horizontal-siex) | Seguimiento de transición técnica 2027 | No dar por desplegada ni efectiva una versión prevista sin verificar |
| [MAPA — REGFI](https://servicio.mapa.gob.es/regfiweb/Productos/Index) | Productos registrados, usos y condiciones oficiales | Fuente externa a refrescar y conservar con versión/hora |
| [Junta — REAFA](https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/produccion-agricola/paginas/reafa.html) | Inscripción explotaciones y novedades de autorizaciones | Situación de titular/representación, no identidad única de parcela de app |

**Hito legal registrado:** según la Junta, desde el **01/01/2027** será obligatorio el formato digital para consignar **tratamientos fitosanitarios** agrarios. Se mantiene la obligación de registro antes de esa fecha; no confundir esta fecha con una obligación digital idéntica para todas las secciones del cuaderno. Fertilización/plan de abonado tienen calendario y excepciones propios; revisar por unidad de producción conforme a la Junta y la normativa vigente. No automatizar una interpretación jurídica definitiva sin reglas de aplicabilidad verificadas.

## C. Estado real (no equivale a funcionalidad oficialmente activa)

| Pieza | Evidencia GitHub a 09/10/2026 | Qué NO acredita |
| --- | --- | --- |
| Registro Android y persistencia | [PR #680](https://github.com/izc05/magina-olivo-v20/pull/680) fusionada: Room v25, referencias/snapshots fitosanitarios, migración y tests; [PR #676](https://github.com/izc05/magina-olivo-v20/pull/676) recursos/aplicadores | Que todo el formulario V9 y validación de uso estén terminados |
| Descripción interoperabilidad IUWS | [PR #690](https://github.com/izc05/magina-olivo-v20/pull/690) fusionada: documentación, variables y fakes | Cliente oficial, autenticación real ni aceptación de transacción |
| Catálogo MAPA | [PR #688](https://github.com/izc05/magina-olivo-v20/pull/688) fusionada: análisis REGFI y fixtures | Ingestión productiva desplegada ni decisiones de producto homologadas |
| Alta y acceso oficiales | Pendiente de probar en [#555](https://github.com/izc05/magina-olivo-v20/issues/555) y [#536](https://github.com/izc05/magina-olivo-v20/issues/536) | Software inscrito, certificado aprobado, relación habilitada, sandbox con éxito |
| Sincronización y acuses | Dependencias [#335](https://github.com/izc05/magina-olivo-v20/issues/335) / [#330](https://github.com/izc05/magina-olivo-v20/issues/330) | Copia remota, envío IUWS, estado ACCEPTED |

## D. Flujo aprobado a construir

1. **Contexto:** usuario selecciona finca/parcela(s), identidad y cultivo; no se pierde la selección al navegar entre Mi Campo y Cuaderno.
2. **Alta offline:** tratamiento con fecha o intervalo, parcela(s), **superficie efectivamente tratada**, motivo/plaga, justificación cuando proceda, producto y nº de registro, dosis/unidad o cantidad según regla, aplicador/ROPO, equipo/ROMA/REGANIP o aplicación manual según proceda, asesor si aplica, eficacia, observaciones y evidencias. Campos condicionados por normativa/modelo, no formulario gigante obligatorio.
3. **Validación:** catálogo MAPA identificado por versión; producto válido para el cultivo/uso/fecha conforme a la etiqueta/registro; vigencia y restricciones explícitas. Si una fuente externa no está disponible, conservar anotación offline pero no inventar aprobación reglamentaria.
4. **Vinculación oficial:** titular/explotación inscritos en REAFA; conciliación explícita con DGC/SIGPAC y cultivo. Finca, Parcel, Catastro y explotación oficial son identidades distintas. No sobrescribir geometría ni historial sin consentimiento.
5. **Consolidación y sync:** Android conserva hecho local y outbox; backend autorizado sincroniza la revisión del tratamiento de manera durable, sin duplicados.
6. **Gateway IUWS:** backend aislado transforma revisiones canónicas al Anexo V/VI, maneja certificado/firma/credenciales en servidor, estados por actividad, reintentos y acuses. No incrustar claves ni token de IUWS en APK.
7. **Resultado administrativo:** SUBMITTING no significa ACCEPTED; procesamientos batch pueden acabar parcialmente. Guardar id de petición, respuestas y estados por actividad; errores legibles; corrección versionada sin devolver artificialmente el trabajo realizado a PLANNED.
8. **Histórico:** original inmutable o versionado, producto/snapshot de referencia, fecha, responsable, documentos, acuse oficial y nueva revisión si se rectifica.

## E. Contrato: lo que sí está verificado y lo que requiere ensayo

**Verificado por lectura del Anexo VI 3.11.4 y documentado en [Discovery](IUWS-3114-DISCOVERY.md):**

- IUWS emite JWT de sesión. No declarar que el CUE firma su propio JWT de sesión.
- Modalidades de comunicación: entidad habilitada o productor individual con callback/autorización.
- Operaciones de comunicación, exportación REA/CUE, importación de actividades, consulta del estado de petición y versión.
- Creación es procesamiento potencialmente asíncrono y parcial: un HTTP 200 o idPeticion no prueba aceptación final.
- La Junta indica sustitución del host genérico por https://ws108.juntadeandalucia.es/ para el contrato.
- Los fakes dentro de docs/ prueban reglas locales de diseño; no sustituyen operaciones firmadas contra entorno oficial.

**Abierto:**
- Alta de Mágina Olivo como CUE comercial, identificación del titular/representante, certificado permitido y condiciones exactas de habilitación/revocación.
- Formato definitivo de firma data-signed, cabeceras Authorization/certificate-sscc, si se necesita mTLS en el punto de autenticación andaluz, tiempo de sesión, rutas/métodos y Swagger efectivos: corroborar en descriptor/sandbox.
- Matriz 100 % exacta para tratamiento, DGCs, alternativas cantidad/dosis, asesor, catálogos de códigos y precisión decimal; no codificar enumeraciones inventadas.
- Identidades de actividades externas (límite numérico) y números oficiales de hasta 16 dígitos: representación numérica exacta (evitar pérdida de precisión de JS).
- Infraestructura de credenciales/firma: probar capacidades del runtime antes de decidir Supabase Edge Function frente a worker protegido.
- Plan de consentimiento, RGPD, minimización de datos y retención con asesoramiento legal aplicable.

## F. Puertas de aceptación (GO/NO-GO)

| Gate | Prueba requerida | Estado inicial documentado |
| --- | --- | --- |
| CUE-01 Datos offline | Crear tratamiento, reiniciar, modo avión, varias parcelas y superficie parcial; conservar datos en Room v25 | PENDIENTE aceptación E2E |
| CUE-02 Campos y reglas | Comparar V9 3.1/3.1bis y campos especiales 3.2–3.5 con Anexo V/descriptor/catálogos, por aplicabilidad | PARCIAL: matriz de análisis existente |
| CUE-03 Catálogo MAPA | Catálogo completo versionado, producto activo/baja, usos exactos, caché y last-known-good, sin datos contradictorios | PREPARACIÓN #688 |
| CUE-04 Identidad oficial | Alta REAFA y conciliación SIGPAC/DGC, permisos y explotación; nada se sobrescribe sin confirmación | PENDIENTE |
| CUE-05 Seguridad y backend | Autorización titular/habilitado, pruebas de certificados, secretos solo servidor, aislamiento multiusuario | PENDIENTE |
| CUE-06 Comunicación oficial | Sandbox/servicio autorizado: enviar, comprobarEstado, resultado por actividad, reintentar, corregir, descargar | PENDIENTE — **no afirmar integración** |
| CUE-07 Prueba agricultor | Registrar sin duplicidad; ver estado real, errores recuperables y evidencias; confirmar en teléfono real | PENDIENTE |

**Definition of Done:** no declarar «compatible con la Junta» en términos de transmisión hasta pasar CUE-01 a CUE-07 y obtener al menos un acuse oficial verificable en entorno autorizado. Puede describirse «modelo local preparado parcialmente para CUE V9» si se documenta el alcance exacto. No obligar al usuario a introducir dos veces los mismos datos **es un objetivo de producto**, no una capacidad disponible hoy.

## G. Riesgos y decisiones pendientes

| ID | Riesgo | Mitigación / responsable |
| --- | --- | --- |
| R-01 | 2027 se acerca sin acceso oficial | Priorizar documentación, solicitud de alta #555 y ensayo autorizado; propietario debe gestionar certificados/altas sin compartir clave privada |
| R-02 | Cambios SIEX o versión IUWS | Gateway versionado y mapeo aislado; verificar versiones oficiales; no acoplar DTO a Room |
| R-03 | Registro MAPA no vigente o usos incompatibles | Descarga oficial versionada, advertencias de frescura, reglas y snapshots; ninguna recomendación fitosanitaria automática |
| R-04 | Tratamiento en finca/parcela errónea | Contexto canónico, pruebas multi-workspace y confirmación al cambiar explotación |
| R-05 | Duplicado por fallos de red | Idempotencia interna, estado UNKNOWN_DELIVERY, consulta de resultados antes de reenvío, sin suponer soporte remoto de header de idempotencia |
| R-06 | Declarar presentado sin acuse | Estados técnicos y administrativos separados; UI basada en resultado por actividad, no en HTTP |
| R-07 | Confundir aprobación formal con pruebas fake | Guardar enlaces a logs, respuestas expurgadas, fecha, versión, ambiente y autorización |
| R-08 | Interferir en cierre Android | Investigación y documentación permitidas; producción se secuencia tras #696/Gate21 y PR pequeñas |

## H. Procedimiento de actualización

Al abrir/cerrar cada PR o cuando cambie norma/API: (a) añadir fecha, commit SHA y responsable en #534; (b) actualizar **solo** el contrato técnico correspondiente enlazado en A; (c) cambiar el gate de F solo con evidencia; (d) describir qué falta y el siguiente responsable; (e) documentar fuentes oficiales con fecha, versión, apartado y checksum para anexos descargables. No almacenar en GitHub secretos ni payloads oficiales con NIF/datos personales. Para revisar el estado de Android siempre consultar [índice maestro del proyecto](../../00-master/PROJECT-DOCUMENTATION-INDEX-2026-10-09.md).

### Histórico breve de decisiones

- **2026-10-05:** matriz CUE Andalucía y prioridad de compatibilidad con el modelo V9.
- **2026-10-07/08:** integradas #680 Room v25, #690 IUWS/Anexo V y #688 REGFI como bases independientes; la documentación y los fakes no son integración remota.
- **2026-10-08:** #696 centraliza Android; Web V3 en pausa; único ejecutor productivo.
- **2026-10-09:** propietario ratifica prioridad de compatibilidad real con fitosanitarios Junta y exige documentación consolidada antes de añadir conectividad no verificada.
