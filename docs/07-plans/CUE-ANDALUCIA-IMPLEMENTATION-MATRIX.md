# CUE Andalucía — matriz de implementación

Estado de auditoría: **2026-10-05**  
Issue maestro: #534  
Android/Room: #535  
IUWS/backend: #536

> Este documento es una guía técnica de implementación, no una declaración de cumplimiento jurídico.
> No declarar Mágina Olivo “conectado oficialmente” hasta superar #555 y pruebas reales de IUWS Andalucía.

## 1. Fuentes de verdad

Orden de uso:

1. **Junta de Andalucía — Cuaderno de explotación / CUE**  
   https://www.juntadeandalucia.es/organismos/agriculturapescaaguaydesarrollorural/areas/agricultura/cuaderno-explotacion.html
2. **Modelo de Cuaderno de Explotación V9 — 10/03/2026**  
   https://www.juntadeandalucia.es/sites/default/files/inline-files/2026/03/20260310_MODELO_DE_CUADERNO_DE_EXPLOTACION_v9.pdf
3. **FEGA SIEX — documentación técnica agrícola 3.11**  
   https://www.fega.gob.es/es/siex/documentacion-tecnica-agricola-siex
4. **Anexo V 3.11 — definición de variables REA/CUE**: obligatoriedad y estructura digital.
5. **Anexo VI IUWS 3.11.4**: operaciones/transporte/contrato.
6. **Anexo VII**: códigos y catálogos.
7. **Anexos VIII/IX/X**: representación, autorización y funcionamiento de CUE comercial.
8. BOE consolidado y normativa sectorial cuando determine aplicabilidad.

Regla: **V9 define el contenido/UX; Anexo V/VII/VI define la representación digital final.**

## 2. Deadline y prioridad

La Junta indica actualmente que el CUE digital será obligatorio desde **01/01/2027 para tratamientos fitosanitarios**.

Por eso el primer vertical productivo será:

REAFA/explotación + parcela/cultivo  
→ tratamiento offline  
→ producto oficial MAPA  
→ aplicador/equipo  
→ superficie afectada  
→ validación CUE  
→ sync Mágina Olivo ↔ Supabase  
→ cola administrativa  
→ IUWS Andalucía  
→ ACCEPTED/REJECTED  
→ histórico auditable.

Fertilización, riego, suelos, cosecha y documentación siguen en el modelo, pero no deben retrasar ese vertical.

## 3. Qué existe ya en Room v22

| Área | Modelo actual | Reutilizar | Gap principal |
|---|---|---|---|
| Explotación visible | FarmEntity | Sí | identidad administrativa/titular separada #545 |
| Parcela | ParcelEntity | Sí | links oficiales SIGPAC/REAFA versionados #540 |
| Actuación | ActivityEntity | Sí | intervalo de fechas #547 |
| Parcelas de actuación | activity_parcels | Sí | no asumir parcela completa #546 |
| Fitosanitario | PhytosanitaryDetailEntity | Sí | nº registro, eficacia, snapshots, refs legales #535 |
| Abonado | FertilizationDetailEntity | Sí | composición/plan/variables V9 #538 |
| Riego | IrrigationDetailEntity | Sí | semántica m³/ha y variables V9 #539 |
| Máquinas | MachineEntity | Sí | perfil reglamentario ROMA/inspección #544 |
| Jornaleros | WorkerEntity | **No como aplicador** | identidad legal separada #544 |
| Organizaciones | AgriculturalOrganizationEntity | Sí | conservar address/web + identidad legal #550 |
| Pesada/Entrega | DeliveryEntity + delivery_parcels | Sí | snapshot legal receptor + campos CUE #541 |
| Documentos | DocumentEntity/Attachment | Sí | categoría/retención/owners regulatorios #543 |
| Sync local | LocalMetadata + sync_outbox | Sí | motor remoto todavía pendiente #330 |
| Perfil | ProfileSettings | Sí como preferencias | no usar como titular administrativo #545 |

## 4. Fitosanitarios — mapping actual

### Ya existe

- fecha: `activities.activity_date`;
- finca: `activities.farm_id`;
- parcelas N:M: `activity_parcels`;
- superficie por target: `activity_parcels.area_affected_m2`;
- producto: `phytosanitary_details.product_name`;
- sustancia activa: `active_substance`;
- cantidad/unidad: `total_quantity` + `unit`;
- dosis: `dose_value` + `dose_unit`;
- motivo/problema: `reason`;
- notas: `activities.notes`;
- documentos/fotos: Attachment existente.

### Falta o debe normalizarse

- fecha fin / intervalo → #547;
- superficie afectada introducida por el usuario → #546;
- especie/cultivo y variedad como snapshot del momento;
- nº registro oficial del producto;
- producto/catálogo MAPA con versión → #553;
- aplicador/persona/empresa → #544;
- ROPO/carné/tipo cuando aplique → #544;
- equipo de aplicación + ROMA/censo/inspección → #544;
- eficacia;
- asesoramiento/3.1bis solo cuando aplique;
- completeness/reglas versionadas → #549/#554.

No crear un aggregate `Treatment` paralelo. El canónico sigue siendo:

`Activity + PhytosanitaryDetail + ActivityParcelTarget + refs/snapshots`.

## 5. Slices Room propuestos

### Slice 0 — integridad
#548 / PR #551: una sola fuente de verdad para la versión Room.

### Slice 1 — #546, sin cambio de schema
`area_affected_m2` ya existe. Cambiar contrato para aceptar área por target y dejar de escribir automáticamente toda `managedAreaM2`.

### Slice 2 — #547, Room v23
Solo añadir:

`activities.activity_end_date TEXT NULL`

Migración v22→v23 puramente aditiva; legacy = null.

### Slice 3 — #544, Room v24
Identidad legal reutilizable de aplicadores/asesores y perfil reglamentario de equipos.  
No reutilizar Worker.

### Slice 4 — #535, Room v25
Ampliación fitosanitaria tras cerrar matriz Anexo V/VII:
- registration number;
- efficacy;
- crop/species snapshot;
- variety snapshot;
- operator/advisor refs + snapshots;
- equipment ref + snapshot;
- catálogo/source version;
- extensión GIP cuando corresponda.

No reinterpretar datos legacy ni inventar snapshots históricos.

## 6. UX objetivo

Mantener el diseño actual de Mágina Olivo.

Primera capa de un tratamiento:
- producto oficial;
- problema;
- dosis;
- parcela(s) y superficie.

Datos que deben poder venir precargados:
- cultivo/variedad desde parcela oficial;
- aplicador habitual;
- equipo habitual.

“Más detalles”:
- sustancia;
- nº registro;
- cantidades;
- datos administrativos/contextuales que no necesiten atención en cada alta.

Si falta un dato obligatorio para envío:
- se guarda igualmente offline;
- estado: **“Guardado · faltan datos para CUE”**;
- no se inventa nada;
- no se envía hasta quedar READY.

## 7. Estados: no mezclar

### Sync técnico Android ↔ backend
`LOCAL_ONLY / PENDING / SYNCING / SYNCED / FAILED / CONFLICT`

### Estado administrativo CUE
Modelo separado, #549:
`NOT_APPLICABLE / INCOMPLETE / READY / QUEUED / SUBMITTING / ACCEPTED / REJECTED / SUPERSEDED`

Un registro puede estar:
- cloud = SYNCED;
- CUE = REJECTED.

Eso es válido y no debe borrar ni alterar el original.

## 8. Aplicabilidad

#554 decide si un requisito es REQUIRED/CONDITIONAL/OPTIONAL/NOT_APPLICABLE usando:
- versión normativa;
- superficie total;
- regadío/secano;
- unidad de producción;
- contexto/cultivo;
- otras condiciones oficiales.

Nunca hardcodear en el formulario “todo V9 es obligatorio para todos”.

## 9. REAFA/SIGPAC/Catastro

No convertir `ParcelSource` en una sola fuente excluyente.

Una parcela interna puede coexistir con:
- origen manual;
- vínculo Catastro;
- unidades/recintos SIGPAC;
- datos importados de REAFA.

Usar relación oficial versionada (#540).  
No reemplazar silenciosamente alias, geometría propia o histórico.

## 10. Nube e IUWS

Orden obligatorio:

Room  
→ #335 backend Supabase  
→ #330 sync/WorkManager  
→ #549 cola/estado CUE  
→ #536 adaptador IUWS backend  
→ #555 alta/certificado/sandbox  
→ producción.

La APK **no** se conecta directamente a IUWS y nunca contiene certificados privados/secretos.

Base Andalucía indicada por la Junta:
`https://ws108.juntadeandalucia.es/`

## 11. Alta comercial

#555 es un gate de producción.

Camino recomendado para 1.0, sujeto a verificación vigente:
**empresa desarrolladora → agricultor individual** mediante autorización correspondiente, en lugar de asumir desde el primer día el rol de entidad habilitada.

No usar “homologado”, “conectado con la Junta” o equivalente hasta completar alta y pruebas reales.

## 12. Definition of Done del primer vertical

No se considera cerrado hasta demostrar:

1. alta de tratamiento sin conexión;
2. persistencia tras matar/reabrir app;
3. varias parcelas con superficies parciales;
4. producto oficial + snapshot;
5. aplicador/equipo;
6. validación por reglas/versiones;
7. sync exactamente una vez a backend;
8. reintento idempotente;
9. envío IUWS sandbox;
10. ACCEPTED/REJECTED separado de sync;
11. corrección/reenvío sin destruir histórico;
12. segundo dispositivo converge;
13. certificado/secretos solo backend;
14. pruebas de migración sin pérdida.

## 13. Issues de referencia

- #534 master
- #535 fitosanitarios Android
- #536 IUWS/backend
- #537 suelos
- #538 fertilización/plan
- #539 riego
- #540 parcela oficial REAFA/SIGPAC
- #541 cosecha comercializada
- #542 ecorregímenes
- #543 documentos
- #544 aplicadores/asesor/equipos
- #545 explotación/titular
- #546 superficie afectada
- #547 intervalos
- #549 estado administrativo
- #550 identidad legal Organization
- #553 catálogo MAPA
- #554 aplicabilidad
- #555 alta oficial Andalucía
- #335 backend Supabase
- #330 sync/convergencia


---

## 14. Addendum auditoría V9 oficial — 2026-10-05

Esta sección se verificó directamente contra el **Modelo de Cuaderno de Explotación Andalucía V9 (10/03/2026)**:

https://www.juntadeandalucia.es/sites/default/files/inline-files/2026/03/20260310_MODELO_DE_CUADERNO_DE_EXPLOTACION_v9.pdf

### 14.1 Tratamiento fitosanitario normal — sección 3.1

El registro oficial de parcela contiene:

- parcelas;
- cultivo: especie y variedad;
- fecha concreta o intervalo;
- superficie tratada;
- problema fitosanitario;
- aplicador;
- equipo;
- producto: nombre comercial / sustancia activa;
- nº registro;
- dosis kg/ha o l/ha;
- eficacia: buena / regular / mala;
- observaciones.

Mapping:

| V9 3.1 | Mágina Olivo |
|---|---|
| Parcelas | `activity_parcels` |
| Especie/variedad histórica | #557 |
| Fecha | `Activity.activityDate` |
| Fecha fin | #547 |
| Superficie tratada | #546 / PR #560 |
| Problema | `PhytosanitaryDetail.reason` |
| Aplicador | #544 |
| Equipo | #544; `equipmentText` queda legacy |
| Nombre comercial | `productName` |
| Sustancia activa | `activeSubstance` |
| Nº registro | #535/#553 |
| Dosis | `doseValue + doseUnit` |
| Eficacia | #535 |
| Observaciones | `Activity.notes` |

`totalQuantity + unit` sigue siendo útil en Mágina Olivo, pero no se convierte en obligatorio CUE 3.1 por el mero hecho de existir localmente.

### 14.2 GIP 3.1bis

No forma parte del formulario normal.

#563 modela por separado el seguimiento de superficies objeto de asesoramiento:

- plaga;
- justificación por umbrales/meteorología/etc.;
- medida no química + intensidad + fecha;
- intervención química + producto/registro/dosis/fecha;
- eficacia;
- validación intermedia y final del asesor/ROPO.

### 14.3 Otros registros fitosanitarios

#564 cubre de forma condicional:

- 3.2 semilla tratada;
- 3.3 postcosecha;
- 3.4 locales de almacenamiento;
- 3.5 medios de transporte.

#565 cubre análisis fitosanitarios realizados: muestra, laboratorio, boletín y sustancias detectadas.

Ninguno debe inflar el formulario 3.1.

### 14.4 Identidad de parcela/cultivo V9 2.1

El modelo distingue:

- provincia;
- municipio;
- agregado;
- zona;
- polígono;
- parcela;
- recinto;
- uso SIGPAC;
- superficie SIGPAC;
- superficie cultivada;
- especie;
- variedad;
- secano/regadío;
- aire libre/protegido;
- sistema GIP.

#540 conserva identidad SIGPAC/REAFA versionada.  
#557 conserva contexto agronómico por periodo.  
Catastro sigue siendo una fuente diferente.

### 14.5 Fertilización V9 7

#538 debe soportar, según aplicabilidad:

- fecha/intervalo;
- superficie;
- secano/regadío;
- cultivo;
- tratamiento fondo/cobertera/enmienda;
- tipo de material;
- proveedor + REGA/NIF/NIMA cuando corresponda;
- forma de aplicación;
- empresa aplicadora + REGFER;
- rendimiento esperado;
- riqueza N/P2O5/K2O/MO;
- dosis;
- nutrientes aportados/acumulados;
- extensión de metales para residuos valorizables.

El Plan de abonado es una entidad separada y versionada.

### 14.6 Riego V9 8

#539:

- superficie regada;
- sistema oficial;
- fecha/intervalo;
- volumen m3/ha;
- acumulado m3/ha;
- nitratos mg/L;
- fósforo soluble mg/L.

`durationMinutes` y `sectorText` son útiles localmente pero no sustituyen esos campos.  
`volumeM3` legacy no se reinterpreta como m3/ha.

### 14.7 Cosecha comercializada V9 5

#541 reutiliza Delivery para:

- fecha;
- producto;
- cantidad;
- parcelas origen;
- albarán/factura voluntario;
- lote voluntario;
- cliente nombre/NIF/dirección;
- RGSEAA voluntario.

La identidad del cliente se guarda como snapshot histórico.

### 14.8 Documentos y retención

El V9 exige conservar **al menos 3 años**, según proceda:

- compra de fitosanitarios;
- contratos de tratamientos;
- inspección de equipos;
- entrega de envases;
- boletines de residuos;
- asesoramiento;
- venta de cosecha;
- plan de abonado;
- documentación de fertilizantes/estiércoles/residuos.

#543 implementa categorías documentales + política de retención versionada.  
Si otra norma exige un plazo mayor, prevalece el mayor.

### 14.9 Ecorregímenes prioritarios para olivar

#542 prioriza:

- 9.5 cubiertas vegetales en cultivos leñosos;
- 9.6 cubiertas inertes de restos de poda.

Se registran solo cuando aplican; no se convierten en ruido permanente en Inicio/Cuaderno.

### 14.10 Corrección de hechos realizados

#561 separa:

- lifecycle del trabajo (`COMPLETED`);
- revisión/corrección administrativa.

Corregir un tratamiento no lo devuelve artificialmente a `PLANNED`.  
Una submission ya aceptada no se sobrescribe: se versiona/supersede según el contrato IUWS.

### 14.11 Issues añadidos tras auditoría directa V9

- #561 correcciones versionadas de Completed;
- #563 GIP 3.1bis;
- #564 fitosanitarios especiales 3.2–3.5;
- #565 análisis fitosanitarios opcionales.

## 15. Discovery de transporte IUWS 3.11.4 — 2026-10-07

El [discovery verificado de #536](../integrations/cue-andalucia/IUWS-3114-DISCOVERY.md) documenta la descarga directa del Anexo VI, certificado/cabeceras de comunicación, JWT emitido por IUWS, callback de productor, operaciones y resultados batch por actividad. Incluye fake interno ejecutable bajo `docs/**`, sin runtime ni conexión oficial.

Este slice no cierra la matriz Anexo V/VII/descriptores, el alta #555, el audit del Supabase desplegado ni el sandbox. No libera integración productiva; el gate vigente se mantiene.
