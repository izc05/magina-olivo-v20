# G2 — Contrato de arquitectura y seguridad Android-Supabase-web

**Estado: documento de diseño. No autoriza implementacion.** Ninguna fase descrita aqui se ejecuta sin autorizacion expresa y por separado. A fecha de este documento no existe conexion remota en la aplicacion: `ReservationRpcTransport` es un adaptador en borrador que declara no instalar implementacion HTTP ni endpoint, y las tres clases de G1c (`RemoteReservationContract`, `DurableReservationJournal`, `ReservationRpcTransport`) no estan conectadas a ningun camino de ejecucion de la app.

Base: main `8dc4f142` (G1b integrada) y la rama de G1c `0ebb10a1`.

## 0. Principio rector

El cuaderno de campo del olivarero es su registro de trabajo, no un dato de la plataforma. De ahi tres reglas que ninguna fase puede romper:

1. **Lo local es la verdad.** El dispositivo funciona completo sin red. La nube es una copia coordinada, nunca el origen de la verdad del cuaderno.
2. **Nada sale sin consentimiento explicito y revocable.** G1b ya establecio que la seleccion de fincas delimita la futura vinculacion remota y que el consentimiento protege el conjunto local completo. G2 hereda ese contrato, no lo reinterpreta.
3. **El servidor no confia en el cliente.** Toda regla de acceso se valida en Supabase con RLS; Android es una interfaz, no un guardian.

## 1. Que puede salir del movil y que permanece siempre local

### 1.1 Puede salir, solo con consentimiento explicito por finca

| Dato | Por que puede salir | Condicion |
|---|---|---|
| Identificador de finca y nombre dado por el usuario | Es la unidad de colaboracion y de permisos | Finca incluida en el consentimiento vigente |
| Referencia catastral y recinto SIGPAC | Dato publico de origen oficial | Idem |
| Superficie declarada y poligono/parcela | Necesario para reparto de trabajo y cuaderno oficial | Idem |
| Campañas, jornadas, pesadas, jornales, gastos, trabajos y sus detalles tipados | Es el cuaderno compartido con quien trabaja la finca | Idem, y solo las campañas marcadas como compartidas |
| Avisos y planificados | Coordinacion de equipo | Idem |
| Estado de recoleccion y entregas | Trazabilidad con almazara y transportista | Idem, y solo los campos que cada rol necesita |

### 1.2 No sale nunca del dispositivo

- **Fotografias y adjuntos de parcela** mientras no exista una decision explicita y separada sobre almacenamiento de imagenes. El almacenamiento de objetos queda fuera de G2.
- **Coordenadas GPS crudas de trayectoria** y cualquier rastro de ubicacion del usuario. Solo salen geometrias de parcela, que son dato catastral, no posicion de una persona.
- **Precios y tarifas privadas** del titular, salvo los importes del cuaderno que la propia finca comparte con quien tiene permiso de costes.
- **Datos personales de jornaleros** mas alla del identificador interno y el nombre: ni DNI, ni telefono, ni direccion, ni datos bancarios. El pago se sigue gestionando fuera de la plataforma.
- **La sesion cifrada de cuenta y el ledger de consentimientos** (`noBackupFilesDir`): son estado del dispositivo y no se replican.
- **Fincas no seleccionadas**, incluidas sus campañas, trabajos y cifras. Siguen protegidas por la cuenta original, como fija G1b.
- **El contenido del cuaderno anterior a la vinculacion**, hasta que el titular lo vincule explicitamente (punto 7).

### 1.3 Regla de borde

Lo que no aparece en 1.1 **no sale**. El contrato es una lista blanca, no una lista negra: un campo nuevo no viaja hasta que este documento lo autorice.

## 2. Identidad, titularidad y permisos por finca

### 2.1 Identidad

Una cuenta Supabase por persona. El dispositivo guarda su sesion cifrada; la identidad del proceso ya se aisla entre cuentas y entre ejecuciones (G1b, `IsolatedAppStateRule` y el reinicio de `AccountRuntime` cubren esto en pruebas).

### 2.2 Titularidad

La titularidad es **de la finca**, no de la cuenta: una fila de `farm_ownership` con `farm_id`, `owner_account_id`, `since`, `until` y el consentimiento que la origino. El historico no se borra; un cambio de propietario cierra el periodo anterior y abre el nuevo (punto 7).

### 2.3 Roles y alcance exacto

| Rol | Puede leer | Puede escribir | No puede |
|---|---|---|---|
| **Propietario** | Todo lo compartido de sus fincas, incluidos costes | Todo; concede y revoca permisos | Ver fincas de otras cuentas |
| **Arrendatario** | Cuaderno completo de la finca arrendada y sus costes | Campañas, trabajos, pesadas, jornales y gastos del periodo de arrendamiento | Cambiar titularidad, conceder permisos a terceros, ver periodos ajenos al arrendamiento |
| **Encargado** | Cuaderno completo de las fincas asignadas, con costes si el propietario lo habilita | Trabajos, jornadas, pesadas, jornales | Cambiar titularidad ni permisos; ver otras fincas del propietario que no le fueron asignadas |
| **Jornalero** | Solo sus propias jornadas y las tareas asignadas a el | Sus propias jornadas y pesadas, en el dia de trabajo | Ver importes de otros, ver el cuaderno completo, ver otras fincas |
| **Transportista** | Solo las entregas asignadas: finca, cantidad, destino y ventana | Confirmacion de recogida y entrega | Ver campañas, costes, jornales ni ninguna otra parte del cuaderno |

Dos invariantes: **ningun rol salvo propietario concede permisos**, y **ningun rol ve una finca sobre la que no tiene una concesion vigente**.

### 2.4 Concesiones

`farm_grants`: `farm_id`, `grantee_account_id`, `role`, `granted_by`, `granted_at`, `revoked_at`, `scope` (costes si/no, campañas concretas). Una concesion revocada no se borra: se cierra con `revoked_at`, para que el historico explique quien vio que y cuando.

## 3. Seguridad en servidor: RLS, aislamiento y ausencia de credenciales de administracion

### 3.1 Punto de partida ya existente

`supabase/migrations/` contiene las migraciones G0 de workspaces, esquema de sincronizacion agricola, contratos y RLS de almacenamiento, endurecimiento de parcelas legacy y guardas de identidad agregada. G2 se construye sobre ellas y no las reescribe.

### 3.2 Reglas obligatorias

1. **RLS activado en toda tabla con datos agricolas**, sin excepciones y sin politicas `USING (true)`.
2. **Toda politica se expresa contra una concesion vigente**, no contra la pertenencia a un workspace: `exists (select 1 from farm_grants g where g.farm_id = t.farm_id and g.grantee_account_id = auth.uid() and g.revoked_at is null and <alcance del rol>)`.
3. **El alcance de columnas se implementa con vistas por rol**, no con filtros en el cliente: el jornalero consulta una vista que no contiene importes ajenos, y el transportista una que no contiene el cuaderno.
4. **La escritura pasa por funciones RPC** con `security invoker` y validacion explicita de rol, periodo y pertenencia. Nada de `insert` directo del cliente sobre tablas de cuaderno.
5. **La app solo porta la clave anonima**. Ninguna clave `service_role`, ningun secreto de administracion y ninguna credencial de despliegue entra en el APK, ni en recursos, ni en `BuildConfig`, ni en el almacen cifrado. La administracion se hace desde la web autenticada o desde CI con secretos de repositorio.
6. **Cada politica nace con su prueba negativa**: por cada regla que concede, una prueba que demuestra que otra cuenta recibe cero filas. El patron ya esta en el repositorio (`supabase/tests/`, con las variantes legacy y clean que el job `g0-security` ejecuta en pila desechable).
7. **Sin confianza en el reloj del cliente**: `created_at` y `updated_at` los pone el servidor; la hora del dispositivo solo viaja como dato declarado del trabajo.

### 3.3 Aislamiento entre cuentas

La prueba de aceptacion es la misma que G1b exige en local, trasladada al servidor: dos cuentas, la misma finca vinculada por la primera, y la segunda recibiendo **cero filas** en toda consulta y **error de permiso** en toda escritura, incluidos los caminos indirectos (agregados, vistas, funciones, almacenamiento y busquedas).

## 4. Offline-first

### 4.1 Modelo

Room sigue siendo el origen de la verdad del dispositivo (version de esquema 26 en la base actual). La sincronizacion es un proceso aparte que lee y escribe una **cola de salida** y una **marca de agua de entrada**, y que nunca bloquea la interfaz.

### 4.2 Cola de salida

Una tabla local de operaciones pendientes con: `operation_id` (UUID generado en el dispositivo), `entity`, `entity_id`, `payload`, `created_at`, `attempts`, `last_error`, `state` (`pending`, `in_flight`, `confirmed`, `rejected`). La operacion se encola **en la misma transaccion** que el cambio local: si el cuaderno lo registro, la cola lo tiene.

El diario durable de G1c (`DurableReservationJournal`) es el precedente de este mecanismo y el patron a reutilizar, incluida su prueba de interrupcion de proceso (`ReservationProcessInterruptionTest`).

### 4.3 Reintentos

Retroceso exponencial con tope y jitter, sin limite de intentos para errores de red y con parada inmediata para errores de permiso (`403`) o de contrato (`422`), que pasan a `rejected` y se muestran al usuario. Ninguna operacion se descarta en silencio.

### 4.4 Recuperacion tras cierre inesperado

Al arrancar, toda operacion en `in_flight` se reevalua contra el servidor por su `operation_id` antes de reintentarse: si el servidor ya la aplico, se marca `confirmed`; si no, vuelve a `pending`. Esto es lo que impide el duplicado clasico del "se fue la bateria entre el envio y la confirmacion".

### 4.5 Ausencia de perdida

Tres invariantes comprobables: la cola solo se vacia con confirmacion del servidor o decision explicita del usuario; un `pm clear` del lado servidor no existe (el dispositivo no acepta ordenes de borrado remoto en G2); y el dato local nunca se elimina como efecto de una sincronizacion.

## 5. Idempotencia extremo a extremo

### 5.1 Clave de idempotencia

Cada operacion lleva un `operation_id` UUID generado en el dispositivo, que viaja a la RPC y queda en una tabla `sync_operations` con unicidad sobre `(account_id, operation_id)`. La RPC es **idempotente por construccion**: si el `operation_id` ya existe, devuelve el resultado anterior sin aplicar nada.

### 5.2 Identidad estable de las entidades

Fincas, campañas, jornadas, pesadas, jornales y gastos se crean con UUID del dispositivo, no con autoincremento del servidor. Asi el reintento no puede crear un segundo registro, y la misma pesada enviada dos veces es la misma fila.

### 5.3 Entidades con riesgo de duplicado semantico

Para pesadas y jornales, ademas de la clave tecnica, una **restriccion de unicidad natural** por `(farm_id, campaign_id, worker_id, work_date, sequence)`. Si el usuario registra legitimamente dos pesadas identicas, `sequence` las distingue; si es un reintento, la unicidad lo frena.

### 5.4 Prueba de aceptacion

Enviar cada tipo de operacion tres veces —envio normal, reintento tras timeout y reintento tras reinicio de proceso— y comprobar que el recuento final en servidor es exactamente uno.

## 6. Conflictos entre dos dispositivos

### 6.1 Regla general: no sobrescribir en silencio

Cada fila del servidor lleva `updated_at` y `updated_by`. Toda escritura envia el `updated_at` que el dispositivo creia vigente. Si no coincide, el servidor **rechaza** con `409` y devuelve la version actual. El dispositivo no fuerza nada.

### 6.2 Tres comportamientos segun el dato

| Tipo de dato | Resolucion |
|---|---|
| **Registro de hecho** (pesada, jornada, gasto, entrega) | No hay conflicto real: son filas distintas de distintos autores. Se conservan ambas. |
| **Atributo descriptivo** (nombre de finca, superficie declarada, notas) | Ultima escritura gana **solo si** el autor tenia la version vigente; si no, conflicto explicito y el usuario elige, con las dos versiones a la vista. |
| **Estado de maquina** (campaña abierta/cerrada, recoleccion en curso) | Nunca automatico. El servidor aplica la transicion valida y rechaza la invalida; el usuario ve por que. |

### 6.3 Lo que no se hace

Sin "last write wins" global, sin fusion automatica de textos, y sin resolucion silenciosa de estados. Un conflicto no resuelto queda visible en el dispositivo y no bloquea el resto de la cola.

## 7. Vinculacion inicial, revocacion, cambio de propietario y recuperacion de cuenta

### 7.1 Vinculacion de datos existentes

El cuaderno que ya existe en el movil **no se atribuye automaticamente** a la cuenta que inicia sesion. Ese es el principio que G1b ya protege en local y que G2 no relaja. La vinculacion es un acto explicito, por finca, con el texto de consentimiento versionado que ya registra el ledger (`AndroidFarmLinkLedger`, con su prueba de que un texto nuevo no reescribe el aceptado).

Secuencia: el titular selecciona fincas -> acepta el consentimiento vigente -> el dispositivo crea la finca en servidor con su UUID local -> y solo entonces la cola empieza a publicar el cuaderno de esas fincas. Las no seleccionadas siguen intactas y protegidas.

### 7.2 Revocacion

Revocar una concesion cierra `revoked_at`: el beneficiario deja de leer y escribir en el siguiente token refrescado, y pierde el acceso a lo ya sincronizado. Lo que ese beneficiario tenga descargado en su dispositivo se borra en el siguiente arranque con red; no se puede garantizar el borrado de un dispositivo que nunca vuelva a conectarse, y el documento lo dice en lugar de prometer lo contrario.

Revocar el consentimiento de una finca completa detiene la publicacion y marca la finca como no compartida. **No borra el cuaderno local**, que sigue siendo del olivarero.

### 7.3 Cambio de propietario

Nunca es una reasignacion silenciosa de datos. Requiere: aceptacion del propietario saliente, aceptacion del entrante, y cierre del periodo anterior en `farm_ownership`. El cuaderno historico **permanece atribuido al periodo en que se registro**: el nuevo propietario ve lo que la finca produjo bajo su titularidad, y el anterior conserva lo suyo. Sin fusion de historicos.

### 7.4 Recuperacion de cuenta

La recuperacion restituye el acceso a **las concesiones vigentes**, no a los datos de un dispositivo. Tras recuperar, la app parte de cero local y descarga solo lo que las concesiones permiten. Una cuenta recuperada no hereda automaticamente el cuaderno que hubiera en otro movil.

## 8. Compatibilidad con la administracion web y la expansion

- **Un unico contrato**: la web consume las mismas RPC y las mismas vistas por rol que Android. Nada de endpoints privilegiados para la web.
- **La administracion de plataforma es una tabla aparte** (`platform_admins`) y sus politicas se escriben una por una; no hereda permisos de las fincas ni los concede.
- **Multiples fincas y multiples cuentas por persona** desde el primer esquema: `farm_grants` ya lo permite sin cambios de modelo.
- **La expansion a otras provincias o cultivos** no toca el modelo de permisos; solo añade catalogos. Si algun dia hay cooperativa o almazara como actor, entra como un rol nuevo en la tabla del punto 2.3, con su propia vista y sus pruebas negativas.

## 9. Plan de pruebas antes de activar G2

Cuatro bloques, todos con evidencia por SHA y run-id, y todos en pila desechable:

1. **Seguridad en servidor**: por cada politica, una prueba positiva y una negativa; dos cuentas y cero filas cruzadas; intento de escritura con rol insuficiente; intento de lectura de columna fuera de alcance; acceso tras revocacion. Extiende `supabase/tests/` y el job que ya corre en `g0-security.yml`.
2. **Integridad e idempotencia**: triple envio de cada tipo de operacion (normal, tras timeout, tras reinicio de proceso) con recuento final exacto; unicidad natural de pesadas y jornales; rechazo `409` con version desactualizada.
3. **Recuperacion**: force-stop en mitad de la cola, corte de red en vuelo, bateria agotada entre envio y confirmacion, y arranque posterior sin duplicados ni perdidas. El patron existe en `G1cJournalProcessTest` (#762) y en el arnes de proceso de G1b.
4. **Android extremo a extremo** contra una pila nativa desechable, nunca contra produccion, verificando que ninguna finca no seleccionada aparece en el trafico de salida.

Criterio de entrada a G2: los cuatro bloques en verde **dos veces consecutivas** sobre el mismo SHA, con el intermitente de Gate 3 Evidence distinguido y documentado, no escondido.

## 10. Secuencia de implementacion, en fases pequeñas y reversibles

Cada fase es una PR independiente, con su autorizacion propia, y cada una es reversible sin tocar las anteriores.

| Fase | Contenido | Reversible por | Autorizacion que requiere |
|---|---|---|---|
| **G2.0** | Este documento. Sin codigo. | Borrar el documento | Ya concedida |
| **G2.1** | Esquema y RLS en `supabase/proposals/`, con sus pruebas positivas y negativas en pila desechable. Sin migracion aplicada. | Borrar la propuesta | Autorizacion de diseño de esquema |
| **G2.2** | Promover la propuesta a `supabase/migrations/` en el proyecto **DEV**. Sin tocar produccion. | `down` de la migracion en DEV | **Autorizacion explicita de migracion**, por ser el primer cambio persistente |
| **G2.3** | Cola de salida local y diario, en Android, **sin red**: encolar, reintentar en seco, recuperar tras force-stop. | Revertir la PR; la cola no tiene consumidor | Autorizacion de codigo Android |
| **G2.4** | Transporte real contra la pila DEV, detras de una bandera apagada por defecto, con la clave anonima y solo lectura. | Apagar la bandera | **Autorizacion de conexion remota** |
| **G2.5** | Escritura idempotente de una sola entidad (la pesada), extremo a extremo en DEV. | Apagar la bandera | Autorizacion de escritura remota |
| **G2.6** | Resto de entidades, conflictos y revocacion. | Apagar la bandera | Autorizacion por entidad |
| **G2.7** | Administracion web sobre las mismas RPC. | Revertir la PR web | Autorizacion web |
| **G2.8** | Activacion para olivareros reales, con plan de vuelta atras escrito. | Apagar la bandera y conservar el cuaderno local | **Autorizacion de produccion** |

Ninguna fase posterior empieza sin que la anterior tenga evidencia en verde. Y ninguna fase borra datos del dispositivo: el cuaderno local sobrevive a cualquier vuelta atras.

## 11. Riesgos que este documento deja abiertos

1. **El intermitente de Gate 3 Evidence sigue sin causa demostrada** (6 apariciones en 17 ejecuciones completas, tres clases afectadas). No bloquea el diseño, pero contamina la señal de cualquier validacion larga que G2 necesite.
2. **Almacenamiento de imagenes sin decidir.** Fotografias y adjuntos quedan fuera de G2; cuando entren, necesitan su propio contrato de consentimiento y de RLS de objetos.
3. **Borrado en dispositivos de terceros no garantizable** si el dispositivo nunca vuelve a conectar (punto 7.2).
4. **Datos personales de jornaleros**: el documento los mantiene fuera, lo que limita funciones de nomina. Si el producto los necesitase, exige un analisis de proteccion de datos previo y separado.
5. **Reloj y zona horaria del dispositivo** en trabajos declarados en campo sin cobertura: la hora declarada puede diferir de la real y ninguna regla de servidor puede corregirla.
6. **Coste y cuota de Supabase** a escala de varias explotaciones: no estimado en este documento.

---

Documento preparado por Claude sobre main `8dc4f142` y la rama de G1c `0ebb10a1`. No modifica el SHA validado de #759 ni ningun workflow. Sin conexion remota, sin migraciones aplicadas, sin datos subidos y sin APK candidata.
