# Recuperación de las pruebas de contrato históricas (Jornadas, Pesadas y Jornadas manuales), 2026-11-03

- **Base:** #843 @ `d437e76a7` (R3-e-limit, GO técnico). Diagnóstico previo: auditoría #841, sección 2.
- **Orden:** GO del propietario para este gate independiente.
- **Alcance:** solo pruebas, el runner y el workflow QA de #808. **No cambia ningún archivo de producción.**
- **Fuera de alcance:** A ↔ B, merges, despliegues, #796, los emuladores A/B y AntiGravity.

## 1. Resultado

| Clase | Antes (`d437e76a7`, recuento crudo) | Ahora |
| --- | --- | --- |
| `JornadaPesadasContractTest` | 0/11 | **11/11** |
| `DeliveryContractTest` | 3/87 | **88/88** (87 históricas + 1 nueva) |
| `HarvestContractTest` | 1/15 | **15/15** |
| **Total** | **4/113** (109 fallos) | **114/114** (113 históricas + 1 nueva) |

- **Ninguna** `@Ignore`, ninguna aserción borrada.
- Solo cambian las 3 expectativas que la enmienda J sustituye de forma explícita (sección 2.3).
- **Defectos funcionales nuevos: ninguno.** No ha hecho falta tocar producción.

## 2. Cambios

### 2.1 Selección de workspace de G2.6 (93 pruebas, infraestructura de test)

`ActiveWorkspaceScope` devuelve `selection_required` cuando un repositorio no recibe `WorkspaceRepository`. Es intencionado (#615): en producción siempre se inyecta.

- Cada clase declara ahora un miembro `activeWorkspace`, que devuelve el workspace de la propia prueba.
- Se pasa a los 10 repositorios de Pesadas y Jornadas que se construían sin él.
- Las pruebas que construyen repositorios de **otro** workspace a propósito no cambian. Siguen demostrando el aislamiento.

**El `NullPointerException` del flujo OCR** (`confirmingATicketRecordsTheReviewedValuesOnceAndFreezesTheReading`) queda verificado con el workspace bien configurado:
- `documents.confirmDeliveryTicket` creaba la Pesada;
- el repositorio de Pesadas, montado sin workspace, devolvía `null` al leerla, por la frontera de alcance;
- ahora pasa sin ningún cambio en el flujo OCR. **No era un defecto funcional.**

### 2.2 Orden causal Jornada → Pesada en el worker G2.4 (13 pruebas, infraestructura de test)

Desde #808, `DeliveryDispatchWorker` no envía una Pesada hasta que su Jornada tiene versión remota (`PARENT_NOT_SYNCED`). Los fixtures del worker G2.4 son anteriores a esa regla.

- **`confirmParentJornada(deliveryId)`:** deja la Jornada exactamente como la deja su propio emisor tras el recibo del servidor (versión remota 1).
- **`dispatchable()`:** devuelve `preparable()` con la Jornada confirmada. Lo usan las 8 funciones que despachan por el worker, y la prueba de token caducado llama a `confirmParentJornada` directamente.
- **Prueba nueva `g24WorkerRefusesAPesadaWhoseJornadaIsNotConfirmedBeforeAnyConnection`:** fija la regla. Sin Jornada confirmada da `PARENT_NOT_SYNCED`, sin ninguna petición, sin PREPARE, y con la operación, la Pesada y la Jornada idénticas.

### 2.3 Las tres expectativas sustituidas por la enmienda J

En las tres se conserva la garantía original de la prueba.

| Prueba | Antes | Ahora | Garantía que se conserva |
| --- | --- | --- | --- |
| `aDaysOriginIsTheUnionOfItsPesadasParcelsWithoutASplit`, renombrada a `…WithTheServerSplit` | El día queda sin reparto (`UNALLOCATED` y sin kilos) | **J-D1:** Norte 2 000 000 `EXACT` y Sur 1 000 000 `EXACT` (regla del servidor) | Las parcelas del día son la unión de las de sus Pesadas; total 3 000 000; cada Pesada conserva sus kilos exactos |
| `aPesadaLinkedBeforeCr010KeepsItsJornadaUntilItsDateChanges` | Fase 19B: la MANUAL pasa a valer los kilos de su Pesada (2 300 000) y, al irse la Pesada, queda en «Kg pendientes» | **J-D2:** la MANUAL conserva 1 000 000 tecleados y muestra `linkedPesadasGrams = 2 300 000`. Su formulario cambia sus kilos (7 777 000) y los trabajadores. Al irse la Pesada conserva 7 777 000 y deja de mostrarla | La Pesada sigue en su Jornada mientras no cambie de fecha; los kilos de la Jornada nunca vienen de las Pesadas; con otra fecha pasa al día automático; un día con Pesadas no se borra bajo ellas (#457) |
| `aDayKeepsItsParcelRowsAndNamesWhenItsPesadasChange` | La fila de parcela del día es idéntica en todo, peso incluido | Se mantienen la misma fila (`id`), la parcela, `campaignParcelId` y el nombre registrado «Norte», aunque la parcela se renombre. **J-D1:** el peso es la suma `EXACT` (2 000 000 → 2 500 000) | #458: solo las parcelas que entran o salen añaden o quitan filas, y el nombre registrado nunca cambia |

## 3. Runner y workflow

- **Nuevo runner** `supabase/tests/g26-contract-android/run.sh`, que usa `am instrument -r` y cuenta **por prueba** los códigos crudos:
  - pasa solo si arrancan exactamente las 114, todas terminan con código 0 (ningún fallo, error, ignorada ni supuesto) y la instrumentación termina (`INSTRUMENTATION_CODE: -1`);
  - imprime el recuento por clase y cada prueba que falla;
  - **comprobación de que no es permisivo:** sobre la salida cruda de la base sin cambios informa `bad=109`.
- **Workflow QA de #808** (`g26-parcel-mutation-android-qa.yml`, trabajo `producer-sender`): el paso del emulador ejecuta ahora el runner Room y después este runner, en el mismo emulador desechable. Se añade también el filtro de rama de la base `codex/g26-808-r3e-limit`.

## 4. Riesgos y pendiente

1. **Otras clases de contrato** (`AgriculturalYearContractTest`, `DayCostContractTest`, `EquipmentContractTest`, `JornadaCostContractTest`, `LabourContractTest`, `LabourPaymentContractTest`, `RecollectionFlowContractTest`) construyen repositorios de Pesadas o Jornadas sin `WorkspaceRepository`. Es probable que fallen por la misma causa. **No se han medido**: están fuera del alcance de este gate y siguen fuera del CI de #808.
2. `confirmParentJornada` sustituye el envío real de la Jornada por su estado final (versión remota 1). El envío real ya lo cubren las pruebas Room y HTTPS de #808.
3. **A ↔ B:** pendiente de autorización.
