# Mágina Olivo 1.0 — asistente opcional de fincas y parcelas

**Estado:** implementación autorizada en rama Draft independiente, basada en #859 `b869695b5` (descendiente de `0c1178f66`).  
**Fecha:** 2026-11-07.  
**Ámbito:** aplicación Android local. No modifica Supabase, SQL remoto ni sincronización.

## 1. Objetivo y límites

El asistente ofrece un recorrido opcional para crear una finca y asociar parcelas. Se accede a él desde Mi Campo y desde la ficha de finca. Las pantallas actuales de creación, edición, mapa, Catastro y ficha de parcela siguen disponibles. No se cambian sus rutas ni se rediseñan como condición del asistente.

La finca se crea con **nombre obligatorio solamente**. Se pueden añadir varias parcelas y completar superficie gestionada, variedad, árboles, datos agrícolas y riego más tarde. Los datos confirmados se guardan en los repositorios actuales con sus UUID canónicos; el asistente no mantiene copias de datos agrícolas.

## 2. Pantallas y navegación

1. **Entrada:** «Crear finca con ayuda» como opción adicional, junto a «Nueva finca» actual. Si existe un recorrido pausado de la cuenta y workspace activos, ofrecer «Continuar» o «Empezar otra finca».
2. **Nombre:** introducir nombre, confirmar y crear la finca. No se crea ninguna parcela vacía. La respuesta de creación proporciona `farmId` y lleva al resumen de esa finca.
3. **Elegir método:** tres acciones principales: «Seleccionar en el mapa», «Buscar parcela» y «Estoy en el terreno». Buscar ofrece dos subopciones: referencia catastral, o municipio/polígono/parcela (con provincia cuando se necesite para desambiguar).
4. **Mapa o resultados:** reutilizar B3 y Catastro. Permitir multiselección, mostrar referencias, fuente, geometría y superficie catastral. GPS preciso centra y consulta candidatas; GPS aproximado solo centra. Ningún resultado se guarda al tocarlo.
5. **Revisión y confirmación:** mostrar finca de destino y cada candidata. Confirmación explícita antes de incorporar. El resultado de cada referencia es «incorporada», «ya existente» o «fallida»; los fallos pueden reintentarse sin repetir éxitos.
6. **Resumen de la finca:** listar parcelas efectivamente guardadas; acciones «Añadir otra», «Completar datos» y «Terminar por ahora». Datos opcionales pendientes se señalan sin bloquear la salida.
7. **Datos de parcela:** reutilizar el editor actual, sobre el mismo `parcelId`. Alias y validaciones actuales; superficie gestionada, número de olivos, variedad, información agrícola y riego pueden quedar vacíos.

Las rutas del asistente contienen el `farmId`; no infieren destino desde el selector global de finca. La navegación de vuelta lleva al resumen contextual. Salir del asistente no archiva ni elimina finca o parcelas.

## 3. Fuentes y modelo canónico

- **Finca:** `FarmRepository` y UUID de finca en el workspace activo.
- **Parcela de trabajo:** `ParcelRepository`, UUID de parcela y pertenencia canónica a finca. Una referencia catastral activa se consulta y valida en el workspace antes de crear; el repositorio conserva la comprobación transaccional final.
- **Catastro:** referencia, geometría, superficie catastral, fuente y fecha de importación. La cartografía identifica una candidata; no acredita titularidad.
- **SIGPAC:** su capa actual es una referencia visual de recintos. No equivale a una parcela de trabajo ni sustituye geometría o superficie catastral. No se persisten asociaciones de recintos en este asistente 1.0; ello exigiría una decisión y modelo propios.
- **Superficie gestionada:** declaración editable del usuario, separada de la superficie de Catastro. Nunca se rellena o sobrescribe silenciosamente con ella.
- **GPS:** posición y precisión transitorias. No se guardan como prueba de propiedad ni seleccionan una parcela automáticamente.

Catastro requiere red para nuevas consultas. Sin conexión se puede continuar con finca y parcelas ya guardadas y editar sus datos locales; la búsqueda oficial informa de su indisponibilidad sin borrar el progreso.

## 4. Estado y recuperación

Estados del recorrido: `SIN_FINCA`, `CREANDO_FINCA`, `FINCA_CREADA`, `BUSCANDO`, `REVISANDO`, `IMPORTANDO`, `IMPORTACION_PARCIAL`, `DATOS_PENDIENTES`, `FINALIZADO`.

Solo los datos **confirmados** sobreviven obligatoriamente al cierre de proceso. Una selección de candidatas aún no confirmada puede perderse; al volver se reconstruye el resumen desde los repositorios y se permite repetir la búsqueda. Si hubo importación parcial, las parcelas guardadas aparecen; las referencias fallidas deberán buscarse de nuevo si se cerró la aplicación. En la sesión activa, el resultado por referencia se mantiene visible y se reintentan solo las fallidas.

### Checkpoint local mínimo

Un único registro local por cuenta/workspace activos guarda el `reservedFarmId`. La clave del registro contiene la identidad de cuenta (cuando Auth está configurado) y el `workspaceId`. No almacena nombre, geometría, referencias, coordenadas GPS ni campos agrícolas. Se elimina al finalizar voluntariamente el recorrido; la finca y parcelas permanecen. Al cambiar cuenta o workspace se oculta el recorrido anterior.

El checkpoint se persiste con `SharedPreferences.commit()` **antes** de crear la finca. El asistente reserva un UUID y llama al repositorio con `requestedId`. En una transacción Room, la operación comprueba el workspace activo, crea la finca con ese UUID si falta, o devuelve el mismo UUID si ya existe con el mismo nombre normalizado y contexto; un ID ocupado por otro registro devuelve conflicto. Si el proceso muere antes de la transacción, se repite con el mismo UUID; si muere después, se recupera la finca existente. Nunca se busca ni deduplica por nombre, pues dos fincas distintas pueden compartirlo.

### Justificación de cambios futuros

El `FarmRepository.create(NewFarm)` actual devuelve un UUID, pero el ViewModel de lista consume el resultado y solo publica un mensaje/contador. Además, el repositorio genera el UUID dentro de la operación; una caída después del commit y antes de recibirlo deja una ventana en la que repetir «Crear» podría crear otra finca. Exponer solo el UUID en navegación no cierra esa ventana.

Por ello se amplía **solo `NewFarm`** con `requestedId: UUID? = null`. `FarmRepository.create` y las pantallas actuales conservan su firma y comportamiento cuando el campo es nulo. Cuando no es nulo, el repositorio usa ese UUID en una transacción y devuelve el mismo al reintentar, sin repetir la escritura ni crear otra operación de outbox. No se cambia el contrato de sincronización remota.

**No se modifica Room:** la versión integrada es 35 y el checkpoint solo contiene un UUID de navegación. `SharedPreferences.commit()` garantiza que el UUID reservado quede persistido antes de solicitar la creación; la transacción Room hace idempotente el uso de ese UUID. No se necesita una tabla paralela ni migración. `SavedStateHandle` o `rememberSaveable` por sí solos no garantizan recuperación tras cierre de proceso.

## 5. Importaciones parciales y duplicados

La importación de mapa existente guarda cada candidata por separado. El asistente no promete atomicidad del lote. Muestra resultados individuales y termina en estado parcial si alguna falla. Antes de cada reintento consulta la referencia normalizada en el workspace; si ya está en la finca actual, enlaza a su `parcelId`; si está en otra finca, presenta conflicto explícito y no reasigna. La comprobación transaccional del repositorio sigue siendo la última barrera frente a carreras y doble pulsación. Los éxitos no se vuelven a enviar.

Al reabrir después de una importación parcial, se reconstruyen los éxitos desde Room. No se crea un segundo registro para recuperar una candidata cuyo resultado ya se guardó. El usuario vuelve a buscar cualquier referencia que falló antes de cerrar la aplicación.

## 6. Aislamiento y errores

Toda ruta, consulta, checkpoint y escritura se valida contra cuenta y workspace activos. El `farmId` de la ruta debe pertenecer al workspace actual; cada `parcelId` debe pertenecer a esa finca. Un cambio de cuenta, workspace o finca durante el recorrido invalida la pantalla contextual y exige volver a entrar desde el contexto autorizado. El modo DEV sin Auth conserva el alcance del workspace local; una sesión configurada nunca utiliza el checkpoint de otra cuenta.

Los errores de Catastro, permisos de ubicación, falta de GPS, referencia ambigua o red no eliminan datos confirmados. Una posición aproximada no lanza consulta automática. Se muestra la fuente de cada superficie y se exige revisión humana antes de guardar datos oficiales en la parcela de trabajo.

## 7. Fases de implementación, después de P3

1. Revisar el SHA integrado de P3, versión Room, rutas y contratos actuales; actualizar la documentación de arquitectura/modelo afectada.
2. Implementar checkpoint mínimo y creación idempotente con pruebas de caída/reintento; conservar creación y edición actuales.
3. Añadir entrada opcional, resumen y navegación contextual por `farmId`.
4. Conectar los tres métodos de búsqueda con B3/Catastro/GPS existentes y mostrar resultados individuales de importación.
5. Añadir acceso guiado al editor actual de datos agrícolas y riego; validar accesibilidad y pantallas compactas.
6. Ejecutar regresiones y revisión visual en una rama Draft independiente. No fusionar sin autorización.

## 8. Criterios de prueba

- Nombre único requisito; `farmId` devuelto coincide con el persistido y con la ruta abierta.
- Cierre de proceso antes/después del commit, recreación de Activity, doble pulsación y reintento: exactamente una finca y un checkpoint coherente.
- Multiselección e importación parcial: éxitos conservados, fallos visibles y reintentables, cero duplicados.
- Referencia repetida en misma finca y en otra; conflictos claros, sin reasignación.
- Búsqueda por referencia y municipio/polígono/parcela; Catastro caído y modo avión.
- GPS preciso, aproximado, permiso denegado y señal ausente; sin guardado automático.
- Datos agrícolas y riego editables posteriormente sin alterar fuente Catastro ni superficie gestionada.
- Salida y reentrada, cuenta/workspace cambiados y finca archivada: aislamiento y bloqueo de escritura fuera de contexto.
- Revisión de la versión Room 35 y de las migraciones existentes; este asistente no añade tablas ni altera datos históricos.
- Regresión de navegación P1, pantallas de creación/edición actuales, mapa B3, formularios y flujos offline.

## 9. Límite de integración

La implementación se mantiene en una rama Draft independiente. No se fusiona ni despliega sin autorización. No altera P3, `main`, Supabase, sincronización ni P4/P5.
