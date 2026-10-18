# G1b — clasificación del guardado perdido en MainReminderPersistenceTest

Rama `fix/g1b-identity-rekey-save-loss`, base #757 `12909d0a71054f5be338d1a8cd3e8043f20943fe`.
Sólo `app/src/androidTest` y este documento. Ningún cambio productivo, de Auth, de Room ni de cálculo agrícola.

## Qué estaba mal entendido

El fallo original de #757 (run 38958634772, artifact `evidence/reminder-persistence/later.log`) es
`ComposeTimeoutException: Condition still not satisfied after 15000 ms` en
`MainReminderPersistenceTest.kt:108`, es decir la aserción de Room posterior al toque de Guardar.

En ese mismo run la suite completa dio `OK (827 tests)` **incluyendo esta prueba**. No es un flake
aleatorio de la prueba: la suite y el sondeo recorren caminos distintos.

- Dentro de la suite completa POST_NOTIFICATIONS está **concedido**
  (`evidence/reminder-persistence/permission-before.txt` = `granted=true`), luego `denied == false`
  y la prueba sólo hace `recreate()`.
- El sondeo posterior lo lanza `.github/scripts/gate3-emulator-evidence.sh:407` mediante
  `verify-context-notifications.sh … later`, que **revoca el permiso** y limpia sus flags antes de
  cada `am instrument`. Allí `denied == true` y se recorre el camino del diálogo
  (`waitFor` diálogo → `recreate()` → `waitFor` diálogo → «Ahora no»).

Diferencia respecto al experimento frío de #760: ese script hace `adb shell pm clear` antes de cada
iteración; el sondeo original **no**, de modo que arrastra los datos de aplicación de los 827 tests
anteriores. Las 25 iteraciones verdes de #760 (10 originales + 5 IME en frío, y 10 en warm) no
reprodujeron el fallo porque el estado de partida no es el mismo.

## Reproducción local intentada (no reproduce en este equipo)

AVD `MaginaOlivo_Claude_API35` (API 35, x86_64), animaciones a 0 como en CI, APK dev-debug del HEAD
de #757, **sin `pm clear`** y replicando las etapas que preceden al sondeo
(`ContextNotificationPermissionTest` y `FirstReminderPermissionTest#firstSaved…` en sus cuatro modos):

- 6/6 sondeos `later` PASS.
- 8/8 sondeos `later` PASS con los 16 núcleos del equipo saturados.

Total 14/14 verdes. **No se declara fallo de infraestructura**: sólo se constata que en este equipo
no se reproduce. El emulador de CI rinde de forma muy distinta (resumen gfx del run 39008184881:
100 % de frames con jank, p50 500 ms, p90 950 ms), y la ventana sensible al tiempo está ahí.

## Qué aporta esta rama

Un clasificador de pruebas que convierte el timeout desnudo en una causa con nombre. No cambia el
toque, ni el límite de 15 s, ni una sola aserción, ni la excepción: el `Throwable` original se
relanza con el informe adjunto como `suppressed`.

Estados distinguidos: `FORM_GONE` (el subárbol del editor ya no existe; el toque se lo comió un
árbol reconstruido), `SAVE_DISABLED` (el botón existe deshabilitado), `EDIT_LOST` (el campo ya no
contiene el texto sustituido) y `SAVE_NOT_PERSISTED` (formulario, texto y botón intactos). Se
registran además la fila de Room con su `updatedAt`, el estado habilitado del botón antes del toque,
y la presencia del diálogo de permisos, de `farm-link-locked` y de `activity-detail-root`.

`farm-link-locked` está en la lista por una razón concreta: `FarmLinkAccessHost` envuelve toda la UI
protegida en `key(identity.userId, allowed)`, de forma que una publicación tardía de identidad o un
cambio de acceso destruye y reconstruye NavHost, back stack y el `editor` del formulario. Es una de
las formas posibles de perder el toque sin excepción, y el informe la confirma o la descarta.

## Autocomprobación del clasificador

Con un disparador temporal `CREATE TRIGGER qa_block_save BEFORE UPDATE ON activities … RAISE(ABORT…)`
inyectado antes del toque (parche local, no versionado), el sondeo falló y el informe salió:

```
androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 15000 ms
	Suppressed: java.lang.AssertionError: lost-save=TAP_LOST room="Aviso conservado"
	field="Aviso editado sin duplicados" save=present saveEnabled=true
	saveEnabledBeforeClick=true permissionDialog=absent farmLinkLocked=absent
	detailRoot=present plannedBanner=absent
```

Es decir: dispara, conserva la excepción original y dice la verdad (Room con el valor viejo y el
campo con el nuevo). Tras esa comprobación se renombró ese estado a `SAVE_NOT_PERSISTED` y se añadió
`roomUpdatedAt`, porque «toque perdido» y «guardado ejecutado cuya escritura no cuajó» no deben
confundirse. Revertido el parche, el sondeo vuelve a `OK (1 test)`.

## Pendiente

Ejecutar la tubería warm completa sobre el HEAD de esta rama para que la próxima aparición del fallo
venga ya clasificada. Nada de esto autoriza READY de #757 ni ninguna fusión.

## Corrección: la hipótesis del re-key de identidad queda descartada

Al comprobarla en lugar de arrastrarla, se cae. En el APK con el que se construye tanto en local
como en los jobs de emulador de CI, `MAGINA_AUTH_DEV_URL` no está definida, de modo que
`BuildConfig.AUTH_PROJECT_URL` queda vacío y `SupabaseAuthApi.forEnvironment` devuelve `null`
(`url.isBlank()`). Con ello `AccountRuntime.get()` construye `AccountRepository(null)`:

- `resume(online)` con `api == null` sólo copia `online`; nunca publica `userId`. `identity.userId`
  es permanentemente `null`.
- `LocalFarmLinkRepository.state` se inicializa con `load()` en el constructor y sólo cambia dentro
  de `reloadAndRequire`, al que únicamente llegan `choices()` y `consent()`, que exigen
  `withLocalIdentity` → `requireApi()` → excepción con `api == null`.

Es decir: **ninguno de los dos componentes de `key(identity.userId, allowed)` puede cambiar** ni en
este equipo ni en CI, así que el árbol protegido no se reconstruye por esa vía y no puede ser la
causa del toque perdido. El informe conserva `farmLinkLocked` porque cuesta cero y cierra la
pregunta con un dato en vez de con una suposición, pero la hipótesis no se sostiene.

Quedan en pie, y el clasificador las separa: `FORM_GONE` (el editor se cerró por otra vía),
`SAVE_NOT_PERSISTED` con el botón habilitado (el toque no actuó: desplazamiento de la ventana o del
IME entre el `performScrollTo` y el `performClick`) y `SAVE_NOT_PERSISTED` con el botón
deshabilitado (`isSaving` activo: el toque sí actuó y la escritura no había cuajado).

## Riesgo de aislamiento detectado, no corregido aquí

`IsolatedAppStateRule` aísla la base de datos y las SharedPreferences, pero no el estado de
`noBackupFilesDir`: ni `farm-link-consents-v1.json` (`AndroidFarmLinkLedger.forContext`) ni
`account-auth-*.enc` (`AndroidAccountSessionStore`). Como el sondeo no hace `pm clear`, cualquier
prueba previa que deje un ledger en ese directorio haría que la siguiente arrancase con
`ledger != null` y `userId == null`, es decir `allowed == false`, y toda la aplicación quedaría
sustituida por la pantalla `farm-link-locked`. En el fallo analizado no ocurrió —la prueba superó
las líneas 75 a 101, que exigen la UI real—, por lo que **no se declara causa** y no se toca la
regla: es un hueco que conviene cerrar en #757 con revisión, no de paso aquí.

## Medición: el botón Guardar queda pegado al borde del teclado, con margen cero

Con una instrumentación temporal (parche local, no versionado) se registraron los límites del nodo
`save-activity` y el estado de la ventana en el mismo punto en que la prueba original pulsa Guardar.
Resultado idéntico en cuatro iteraciones a fuente 1.0 y en una a fuente 1.3, AVD 1080x2400:

```
beforeScroll      = Rect(0.0, 0.0, 0.0, 0.0)
afterScroll       = Rect(53.0, 1391.0, 1027.0, 1517.0)
afterScrollAgain  = Rect(53.0, 1391.0, 1027.0, 1517.0)
window rootH=2400 imeBottom=883 imeVisible=true visibleFrame=[0,128][1080,1517]
afterClick        = none   (el editor se cierra: el guardado sí ocurrió en este equipo)
```

Es decir: el teclado ocupa de y=1517 a y=2400 (`imeBottom=883`), el área visible de la aplicación
termina exactamente en y=1517, y `performScrollTo` deja el botón Guardar con **bottom = 1517**, el
mismísimo borde. Su centro —el punto donde Compose inyecta el toque— cae en y≈1454, a **63 px** del
teclado, y la mitad inferior del botón está a ras de la frontera. El margen de seguridad es cero.

Dos consecuencias:

1. **No es sólo un problema de pruebas.** Un agricultor que escribe la descripción y pulsa Guardar
   depende de que el borde inferior del botón no se solape con el teclado. Con márgenes de 0 px, un
   teclado más alto, otro IME o un redimensionado que llegue tarde dejan el punto de toque dentro de
   la ventana del teclado, y el toque se lo queda el teclado: sin excepción, sin error y sin
   escritura en Room. Es exactamente el síntoma del fallo.
2. **Explica por qué `Espresso.closeSoftKeyboard()` daba 5/5** sin demostrar nada: elimina la
   condición en lugar de probarla. Codex hizo bien en no aceptarlo como corrección.

En este equipo el orden siempre sale bien (14/14 verdes). En el emulador de CI, con frames de 500 a
950 ms, el desplazamiento y la llegada del inset del IME se resuelven en instantes distintos, y ese
margen cero es todo el presupuesto disponible.

**Petición de autorización, no cambio unilateral.** La corrección razonable es que la acción
primaria del formulario no descanse pegada al borde del inset (margen inferior a prueba de teclado
en el contenedor con scroll), lo que toca `app/src/main` del área de #757 y afecta a una pantalla
agrícola. No se toca sin autorización expresa del propietario. Mientras tanto, el clasificador de
esta rama dejará nombrada la causa en la próxima aparición: `SAVE_NOT_PERSISTED` con el botón
habilitado y la fila de Room intacta es la firma de un toque que no actuó.

## El fallo se reprodujo en CI y el clasificador lo nombró

Android CI run [39022667093](https://github.com/izc05/magina-olivo-v20/actions/runs/39022667093), job
`gate3-emulator`, sobre `538195e55b69154e28322e3ba42144de3dd17ccd`. La suite completa dio
`OK (827 tests)` —la prueba incluida— y el sondeo posterior de la línea 399 de
`gate3-emulator-evidence.sh` falló. Por primera vez el fallo viene con causa:

```
ComposeTimeoutException: Condition still not satisfied after 15000 ms
  at MainReminderPersistenceTest.failedPlanRetryAndRecreationPreserveReminder(MainReminderPersistenceTest.kt:114)
  Suppressed: AssertionError: lost-save=SAVE_NOT_PERSISTED room="Aviso conservado"
  roomUpdatedAt=2026-10-17T10:22:13.209Z field="Aviso editado sin duplicados" save=present
  saveEnabled=true saveEnabledBeforeClick=true permissionDialog=absent farmLinkLocked=absent
  detailRoot=present plannedBanner=absent
```

Qué dice, punto por punto:

- El formulario sigue **intacto**: el campo conserva el texto sustituido y el botón Guardar existe.
- El botón estaba **habilitado antes del toque y sigue habilitado quince segundos después**. Si el
  toque hubiese llegado al botón, `isSaving` lo habría deshabilitado mientras guardaba. **No hubo
  ningún guardado en curso.**
- Room conserva la descripción vieja, y su `updatedAt` es el de la planificación previa
  (10:22:13.209Z), no una escritura posterior.
- Ni diálogo de permisos, ni pantalla `farm-link-locked`, ni árbol reconstruido.

Es decir: **el toque no actuó sobre el botón**. Quedan descartados por los propios datos el editor
cerrado, el botón deshabilitado, la edición perdida y la escritura lenta de Room. Encaja con el
margen cero medido contra el teclado.

Queda una alternativa que estos datos todavía no separan: que el toque sí llegara y uno de los
guardas del formulario (`readMachines`, `readPlanning`, `readTypedDetail`, `readParcelAreas`)
rechazara el guardado en silencio, lo que también deja el editor abierto y Room intacta. Por eso el
informe incorpora ahora `formErrors`, que enumera los campos que el formulario considera erróneos:
vacío significa que nada rechazó el guardado. La próxima aparición distingue las dos.

## Corrección aplicada, con autorización expresa del propietario

`ActivityEditor`: el hueco inferior pasa a estar **fuera** del área con scroll
(`Modifier.fillMaxWidth().padding(bottom = MoSpacing.sm).verticalScroll(...)`), de modo que el
contenido nunca puede descansar sobre el borde del teclado. Medición antes y después, mismo AVD:

| | bottom del botón | borde del teclado | margen |
|---|---|---|---|
| antes | 1517 | 1517 | **0 px** |
| después | 1485 | 1517 | **32 px (12 dp)** |

No cambia ningún campo, color, tipografía ni orden. La captura con el teclado abierto muestra el
hueco: antes el botón tocaba la barra de sugerencias.

Además, la prueba original comprueba ahora la precondición que antes daba por supuesta: que el punto
de toque esté libre del teclado. Si algún día no lo está, falla ahí nombrando el solapamiento en vez
de como un timeout mudo. No relaja nada.

### Verificación local sobre la corrección

- `ActivityEditorCompactTest` `OK (20 tests)`
- `AppNavigationTest` `OK (31 tests)`
- `FirstReminderPermissionTest` 4/4 modos OK
- `MainReminderPersistenceTest` sondeo `later` 3/3 OK
- `testDevDebugUnitTest` y `assembleDevDebug` verdes en el mismo build

## Segundo fallo intermitente de la misma prueba, al principio y no en el guardado

Gate 3 Evidence [39027814286](https://github.com/izc05/magina-olivo-v20/actions/runs/39027814286),
sobre `d7a70e74`, falló **dentro de la suite completa** (`Tests run: 827, Failures: 1`), esta vez en
`MainReminderPersistenceTest.kt:75`, es decir en el primer `waitFor("plan-activity")`:

```
java.lang.IllegalStateException: No compose hierarchies found in the app.
  at MainReminderPersistenceTest.waitFor$lambda$0(MainReminderPersistenceTest.kt:180)
  at MainReminderPersistenceTest.failedPlanRetryAndRecreationPreserveReminder(MainReminderPersistenceTest.kt:75)
```

No es el guardado: es el arranque. La línea anterior relanza MainActivity con el enlace profundo
(`FLAG_ACTIVITY_SINGLE_TOP` más el id del trabajo), y entre que la jerarquía anterior desaparece y
la nueva llama a `setContent` hay un instante sin ninguna jerarquía Compose. En ese instante
`fetchSemanticsNodes` **lanza** en vez de devolver vacío, y la condición del `waitUntil` revienta en
lugar de seguir esperando. En un emulador con frames de medio segundo ese instante se alcanza.

Corrección: la condición trata ese momento como «todavía no», no como «roto». **El límite sigue
siendo el mismo de 15 s y la etiqueta sigue decidiendo**, así que una pantalla que no llegue nunca
sigue fallando ahí, por timeout, sin relajar nada ni añadir reintentos. Codex ya había visto este
mismo arranque fallar en `0987e712` (su línea 74) sin explicación; ésta es la explicación.

## Tercer modo: el diálogo compuesto en un hilo de E/S de Room

Android CI [39030158618](https://github.com/izc05/magina-olivo-v20/actions/runs/39030158618) sobre
`2536b014`: suite completa `OK (827 tests)` y el sondeo `later` fallando con algo nuevo:

```
java.lang.RuntimeException: Can't create handler inside thread Thread[arch_disk_io_3,5,main]
that has not called Looper.prepare()
  at android.app.Dialog.<init> … DialogWrapper.<init> … BasicAlertDialog
  at …NotificationPermissionHostKt.NotificationPermissionHost(NotificationPermissionHost.kt:109)
  at androidx.compose.runtime.Recomposer.performRecompose(Recomposer.kt:1318)
  at androidx.compose.ui.test.TestMonotonicFrameClock.performFrame(…)
```

La recomposición que crea el diálogo de permisos se ejecutó en un hilo de E/S de Room, donde no hay
Looper y no se puede construir un `Dialog`. En la traza no aparece ninguna línea de la prueba: nace
dentro de la recomposición de la aplicación.

Causa: la prueba consulta Room con `runBlocking` desde el hilo de prueba, y bajo
`TestMonotonicFrameClock` el reloj de frames puede acabar bombeado por el hilo que resuelve esas
consultas. Sólo muerde en el camino con permiso revocado, el del sondeo, que es el único donde se
compone ese diálogo.

Corrección (autorizada por el propietario): las lecturas de Room del cuerpo de la prueba pasan por
un hilo propio, `qa-room-read`, de modo que ninguna continuación de Room puede bombear los
despachadores de Compose. **Las consultas, las aserciones, el recorrido y los límites de 15 s son
los mismos.** Las reglas `seed` y `@After` se dejan como estaban: se ejecutan fuera de la vida de la
composición.

Verificación local tras este cambio: `ActivityEditorCompactTest` 20/20, `AppNavigationTest` 31/31,
`FirstReminderPermissionTest` 4/4 modos y el sondeo `later` 3/3.

## Aislamiento de la sesión y del ledger privado

`IsolatedAppStateRule` aislaba la base de datos y las SharedPreferences, pero no el estado de
`noBackupFilesDir`, donde viven tres cosas privadas: `farm-link-consents-v1.json` (el ledger de
consentimiento de fincas), `account-auth-<ref>.enc` (la sesión de cuenta) y `account-link-backups/`.
Como el sondeo no hace `pm clear`, cualquier prueba previa que dejara ahí un ledger o una sesión la
heredaba la siguiente. Además el dueño de Auth del proceso **cachea el registro en memoria**, así que
vaciar el fichero no bastaba.

La regla ahora aparta todo el contenido de `noBackupFilesDir` a una bóveda en `cacheDir` mientras
dura la prueba, descarta lo que la prueba haya escrito allí, devuelve los originales a su sitio y
suelta el singleton de `AccountRuntime` antes y después, de modo que la siguiente consulta lea el
fichero que corresponde. No hay contaminación entre cuentas ni entre ejecuciones.

Comprobado en rojo antes de corregir. Con el aislamiento desactivado a propósito, la nueva prueba
`theAccountSessionAndTheLinkLedgerAreIsolatedAndRestored` falla así:

```
java.lang.AssertionError: The previous ledger must not reach the test
expected null, but was:<{"device":"original"}>
```

Con la corrección, `IsolatedAppStateRuleTest` da `OK (2 tests)`. La regla la usan ocho clases
instrumentadas; se comprobaron además `AccountAuthMainActivityTest` 2/2 y
`ContextNotificationPermissionTest` 1/1.

## MyProfileScreenTest: independiente del parche, misma familia de causa

El fallo de `MyProfileScreenTest.theCooperativeIsChosenFromTheFarmersOwnListOrAdded:85` (run
39033705914) **no depende de este parche**:

- Usa `createComposeRule()` y `setContent` sobre `MyProfileSection`. No instancia MainActivity, no
  toca Room y no hace ninguna referencia a `ActivityEditor`, `activity-description` ni
  `save-activity`: cero coincidencias en el fichero.
- El cambio productivo de esta rama se limita al contenedor con scroll de `ActivityEditor`.
- Localmente pasa 7/7 sobre el HEAD actual.

Pero es la **misma familia de causa**. La secuencia de la prueba es `performTextInput` en
`profile-new-cooperative` —que abre el teclado—, luego `performScrollTo` y `performClick` sobre
`profile-create-cooperative`. El propio comentario que Codex dejó en la línea 79 ya lo dice: «tapping
it earlier is lost on a slow emulator». Y `MyProfile.kt` aparece en el inventario de abajo con cuatro
campos de texto, tres acciones primarias y ningún hueco inferior.

No se toca aquí: es otra pantalla y corresponde a la PR de formularios.

## Inventario de formularios con el mismo riesgo (sin modificar)

Ficheros con contenedor de scroll, campos de texto y acción primaria. `hueco` indica si ya existe
algún `padding(bottom …)` en el fichero; no equivale a que el contenedor correcto lo tenga.

| Fichero | Campos | Acciones primarias | Hueco inferior |
| --- | --- | --- | --- |
| DeliveryScreens.kt | 14 | 5 | no |
| ParcelScreens.kt | 13 | 4 | parcial |
| MachineryScreens.kt | 11 | 5 | no |
| ExpenseForms.kt | 10 | 2 | no |
| LabourForms.kt | 9 | 3 | no |
| DocumentReviewScreens.kt | 7 | 3 | no |
| FarmScreens.kt | 7 | 4 | no |
| CostSection.kt | 6 | 3 | no |
| CadastreImportScreen.kt | 5 | 3 | no |
| AgronomicPeopleScreens.kt | 5 | 4 | no |
| HarvestScreens.kt | 4 | 4 | no |
| MyProfile.kt | 4 | 3 | no |
| CampaignScreens.kt | 3 | 5 | parcial |
| EquipmentSection.kt | 3 | 2 | no |
| AccountScreen.kt | 2 | 4 | no |

`ActivityScreens.kt` queda fuera de la lista: es el único corregido. Ninguno de los demás se modifica
en esta PR.

## Orden de integración seguro

`fix/g1b-identity-rekey-save-loss` sale del HEAD de #757 y su PR apunta a `feat/g1b-farm-consent`, de
modo que **no puede integrarse antes que #757**. El orden que no deja huecos:

1. #758 se autoriza y fusiona por separado: su base es `main`, no depende de nada de esto.
2. Validar el CI del main integrado tras esa fusión, sin dar por válidos los controles de PR.
3. #763 se revisa y se fusiona **dentro de #757**, no en `main`.
4. #757 pasa a READY sólo después, con sus controles verdes sobre el HEAD resultante.
5. #759 se actualiza sobre ese #757 ya corregido; después #762 sobre #759.
6. G2 permanece inactivo hasta que G1 esté cerrado y verificado sobre el main integrado.

Ninguna fusión sin autorización expresa del propietario.

## FarmLinkScreenTest: el mismo hilo de Room, ahora en el arnés del consentimiento

Android CI 39042261057 sobre `fc702347` falló en la etapa de Room offline:

```
FarmLinkScreenTest.onlySelectedFarmsArePreparedButConsentProtectsTheEntireLocalDataset
ViewRootImpl$CalledFromWrongThreadException: Expected: main Calling: arch_disk_io_0
  at AndroidComposeView.scheduleMeasureAndLayout(...)
  at …ChangeList.executeAndFlushAllPendingChanges(...)
```

La interacción, pieza a pieza:

1. `LocalFarmLinkRepository.consent()` escribe su `StateFlow` en la línea 59, **dentro de
   `database.withTransaction { }`**: la emisión sale de un hilo `arch_disk_io_*`.
2. La prueba compone `FarmLinkAccessHost`, que colecta ese flujo, y ejecuta su cuerpo dentro de
   `runBlocking` sobre el hilo de prueba.
3. `createAndroidComposeRule` sin `effectContext` usa un despachador **no confinado** —lo dice el
   propio aviso de obsolescencia de nuestra compilación: «The v2 APIs use StandardTestDispatcher
   instead of UnconfinedTestDispatcher»—, de modo que las continuaciones del colector y del
   recompositor se reanudan en el hilo que emitió.
4. Con el hilo de prueba bloqueado y la composición viva, esa reanudación aplica cambios sobre
   `AndroidComposeView` fuera del hilo principal, y salta la excepción.

Corrección, una línea en el arnés: la regla recibe `effectContext = Dispatchers.Main`, que es
justo lo que hace la aplicación real, donde el colector corre sobre el despachador de la
composición. La prueba pasa a observar el modelo de hilos de producción en lugar del del arnés.
**No se toca `LocalFarmLinkRepository` ni ningún código productivo**, ni una aserción, ni un
límite de tiempo, ni ninguno de los cuatro escenarios.

### Límite honesto de esta corrección

No hay rojo→verde local: el fallo **no se reproduce en este equipo** —`FarmLinkScreenTest` 6/6
PASS antes de tocar nada, y la repetición del mismo SHA en CI (run 39045217041) terminó en
SUCCESS con `OK (828 tests)`, `OK (220 tests)` y el sondeo del recordatorio `OK (1 test)`. La
corrección se sostiene en el mecanismo demostrado por la traza, no en una reproducción. Un intento
de instrumentar el hilo del colector en local devolvió siempre `main`, es decir, tampoco expuso el
camino malo en esta máquina; queda registrado como lo que es, un intento sin resultado
concluyente.

Verificación tras la corrección: `FarmLinkScreenTest` 3/3 ejecuciones con sus `OK (4 tests)`,
los cuatro escenarios intactos.

### Un posible defecto productivo, documentado y no tocado

Escribir estado de interfaz desde dentro de una transacción de Room es, como mínimo, discutible:
`LocalFarmLinkRepository.kt:59` publica el `StateFlow` mientras la transacción sigue abierta. En
producción no está demostrado que cause daño, porque el colector de la aplicación corre sobre el
despachador principal, pero conviene revisarlo: publicar **después** de cerrar la transacción
sería más seguro y no cambiaría la semántica del consentimiento. **No se modifica aquí**; queda
anotado para que el propietario decida si autoriza esa revisión en #757.

## El margen no bastó: el modo 1 vuelve con la geometría verificada

Android CI 39062267904 sobre `270a7413`: suite completa `OK (828 tests)`, Room offline
`OK (220 tests)` —`FarmLinkScreenTest` pasó, la corrección del arnés aguantó— y el sondeo del
recordatorio otra vez en rojo:

```
lost-save=SAVE_NOT_PERSISTED room="Aviso conservado"
field="Aviso editado sin duplicados" save=present saveEnabled=true
saveEnabledBeforeClick=true formErrors=none
```

Dos lecturas, una a favor y otra en contra de lo que veníamos diciendo:

- **`formErrors=none` cierra la última alternativa.** Ningún guarda del formulario rechazó el
  guardado en silencio. Con el botón habilitado antes y quince segundos después, no hubo ningún
  guardado en curso: el toque no actuó.
- **La precondición pasó.** El fallo está en la línea del `waitUntil`, no en la del
  `assertSaveTapClearOfKeyboard`. En el momento de comprobarlo, el punto de toque estaba libre del
  teclado, con el margen de 12 dp ya aplicado. **El solapamiento con el teclado no explica esta
  aparición, y el margen no ha eliminado el modo 1.** La medición del margen cero sigue siendo
  cierta y el margen sigue siendo bueno para el usuario, pero no era la causa completa.

### Instrumentación añadida, sólo lectura

El informe de fallo incorpora ahora cuatro datos más, leídos sin tocar el toque, los tiempos ni
ninguna aserción:

- `boundsBefore` y `boundsNow`: los límites del botón justo antes del toque y en el momento del
  informe. Si difieren, el nodo resuelto se movió entre la resolución y la inyección y el toque
  cayó donde el botón ya no estaba.
- `scrollBefore` y `scrollNow`: el desplazamiento de **todos** los contenedores con scroll en
  pantalla, en orden de árbol. Si alguno se movió mientras se inyectaba el toque, el contenedor se
  quedó el gesto como desplazamiento en vez de como pulsación — el candidato más probable, porque
  `performScrollTo` deja el desplazamiento asentándose y la secuencia down/up siguiente llega
  encima.

Autocomprobado con el disparador temporal `qa_block_save`: el informe dispara y escribe los cuatro
valores (`boundsBefore` y `boundsNow` idénticos, `scrollBefore`/`scrollNow` presentes),
conservando la excepción original. Revertido el disparador, el sondeo vuelve a `OK (1 test)`.

La próxima aparición dirá cuál de los tres candidatos es: gesto comido por el scroll, nodo movido,
o ventana del IME mayor que su inset declarado.

## SHA `602e476d` â€” la instrumentacion se instala y el modo 1 no vuelve

Commit: `602e476ddd696cf1e07ac93e717aacbdeb75a89e`, *test(qa): record the button's bounds and every scroller around the lost tap*.

La adicion es de solo lectura y vive unicamente en la ruta de error: `lostSaveReport` publica ahora `boundsBefore`/`boundsNow` (los limites del boton Guardar antes del toque y en el momento del informe) y `scrollBefore`/`scrollNow` (el desplazamiento de **todos** los contenedores verticales, en orden de arbol, unidos por `/`). La captura ocurre inmediatamente antes del `performClick()` original, que no se ha tocado, igual que no se han tocado los tiempos ni las aserciones.

Autoprueba local antes de subir: con un `CREATE TRIGGER qa_block_save` temporal que impide la escritura, el informe imprimio
`boundsBefore=Rect.fromLTRB(53.0, 2179.0, 1027.0, 2305.0) boundsNow=<identico> scrollBefore=0.0 scrollNow=0.0 formErrors=none`
conservando la `ComposeTimeoutException` original. Retirado el trigger: `OK (1 test)`.

### Resultados en CI sobre `602e476d`

| Control | Run | Resultado |
|---|---|---|
| Android CI (pasada 1) | `39069238393` | SUCCESS â€” `foundation` + `gate3-emulator` |
| Gate 3 Evidence (pasada 1) | `39069240522` | FAILURE â€” 1 fallo de 828, **no** el guardado de avisos |
| Android CI (pasada 2) | `39071623701` | SUCCESS â€” `foundation` + `gate3-emulator` |
| Gate 3 Evidence (pasada 2) | `39071625522` | SUCCESS â€” `OK (828 tests)` |

**El modo 1 no se reprodujo en ninguna de las dos pasadas.** La sonda aislada de `MainReminderPersistenceTest` (permiso POST_NOTIFICATIONS revocado, sin `pm clear`) termino `OK (1 test)` dos veces en cada ejecucion. La instrumentacion no imprimio nada porque no hubo fallo que clasificar; queda instalada para la proxima aparicion.

### El fallo de la pasada 1 fue otro, y no se reprodujo

```
1) centralAddIsAButtonAndCancelsOnEveryRootWithoutNavigation(com.isivoltpro.maginaolivo.AppNavigationTest)
androidx.test.espresso.base.RootViewPicker$RootViewWithoutFocusException:
Waited for the root of the view hierarchy to have window focus and not request layout for 10 seconds.
Root{... has-window-focus=false ...}
at androidx.test.espresso.Espresso.pressBack(Espresso.java:237)
at AppNavigationTest.centralAddIsAButtonAndCancelsOnEveryRootWithoutNavigation(AppNavigationTest.kt:75)
```

Hechos, sin interpretacion forzada:

- `AppNavigationTest` no esta tocado por ningun commit de esta rama; su ultimo cambio es `b35f5c22` (#753, ya fusionada).
- La linea 75 es el `pressBack()` posterior al `composeRule.activityRule.scenario.recreate()` que la prueba ejecuta solo en la iteracion "Cuaderno".
- El mismo workflow termino SUCCESS en los cuatro SHA anteriores de la rama (`270a7413`, `fc702347`, `92a7baff`, `2536b014`) y volvio a terminar SUCCESS en la repeticion sobre el mismo `602e476d`, con `AppNavigationTest` entero en verde (27 pruebas).

**Conclusion registrada: intermitente de una sola aparicion, sin causa demostrada.** No se le atribuye infraestructura, carrera de foco ni defecto productivo; no se ha modificado la prueba, ni sus aserciones, ni sus tiempos. Si reaparece, se investiga como fallo reproducible y se pide autorizacion antes de cualquier cambio.


## `AppNavigationTest` — de "aparicion unica" a fallo reproducible, y correccion de dos afirmaciones mias

### Lo que cambio respecto a la seccion anterior

La repeticion sobre el SHA de documentacion `56ee08e6` (codigo identico a `602e476d`) **volvio a fallar igual**: Gate 3 Evidence `39074477847`, `AppNavigationTest.centralAddIsAButtonAndCancelsOnEveryRootWithoutNavigation`, `RootViewWithoutFocusException` en `Espresso.pressBack()` (`AppNavigationTest.kt:75`), `has-window-focus=false`, caida en la 8a de las 28 pruebas de la clase (`AppNavigationTest:.......`). Android CI `39074475569` sobre el mismo SHA quedo VERDE, con la clase completa (27 puntos) y `OK (828 tests)`.

**Correccion 1.** Queda retirada la conclusion "intermitente de una sola aparicion, sin causa demostrada" de la seccion anterior. El recuento real sobre los dos SHA es **2 fallos en 7 ejecuciones completas del suite**, siempre en la misma linea: fallo reproducible no determinista.

**Correccion 2.** Queda retirada la afirmacion "`AppNavigationTest` no esta tocado por ninguno de mis commits". Es cierta del fichero de la prueba y enganosa en lo que importa: la clase monta `RuleChain.outerRule(IsolatedAppStateRule())`, y el commit `fc702347` de esta rama modifica ese `IsolatedAppStateRule` (+43/-13: traslado de `noBackupFilesDir` y reinicio del singleton `AccountRuntime`). La prueba esta dentro del radio de alcance del cambio. No se afirma causalidad; se deja de presentarla como ajena.

### Hipotesis descartada con datos: no hay diferencia de entorno

Comparados `android-ci.yml` (job `gate3-emulator`) y `gate3-emulator-evidence.yml` (job `gate3-evidence`): misma accion `ReactiveCircus/android-emulator-runner@a421e438`, `api-level: 35`, `target: google_apis`, `arch: x86_64`, `profile: pixel_6`, `disable-animations: true`, identicas `emulator-options`, mismo `script: gate3-emulator-evidence.sh`, y **ambos** fijan `GATE3_ALLOW_APP_DATA_RESET=1` (CI mediante `env:` del paso, Evidence mediante prefijo del comando). El trabajo verde y el que falla corren el mismo codigo en el mismo entorno.

### Lo que la evidencia no permite concluir

El artefacto `magina-olivo-gate3-emulator-evidence` (57 MB) del run fallido **no contiene logcat ni volcado de ventanas**; solo `instrumentation-all.txt`. Sin eso no se puede identificar que ventana retuvo el foco, de modo que la causa no queda nombrada.

### Linea base en main

| Codigo | Ejecuciones completas del suite | Fallos de `AppNavigationTest` |
|---|---|---|
| Rama #763 (`602e476d`, `56ee08e6`) | 7 | 2 |
| `main` `8151e469` | 1 (`39077107096` SUCCESS) | 0 |

Una ejecucion verde en main no discrimina: la propia rama produce verde 5 de 7 veces. Segunda ejecucion de main en curso: `39079743034`.

### Siguiente paso propuesto, pendiente de autorizacion

Comparacion causal A/B con rama desechable (`qa/g1b-isolation-ab`): la rama de #763 con **unicamente** el cambio de `IsolatedAppStateRule` revertido, y dos ejecuciones de Gate 3 Evidence sobre ella. Solo arnes de pruebas, sin fusionar, sin tocar produccion y sin APK. #763 permanece en DRAFT y no se presenta para fusion hasta cerrar este punto.


## Informe A/B — el aislamiento de `IsolatedAppStateRule` no queda demostrado como causa del fallo de foco

Rama de diagnostico `qa/g1b-isolation-ab`, SHA `6112db1018de71514505a2c7060eca469c3cf732`, creada desde `820cdbf6b29f6e04ce8071fc294f20a1952c763e`. Revierte `IsolatedAppStateRule.kt` y su propia prueba al estado de `main`; ningun otro cambio de #763 se toca. **No se fusionara.**

### Resultados

| Version | SHA | Run | Resultado |
|---|---|---|---|
| A — aislamiento revertido | `6112db10` | `39108847974` | SUCCESS |
| A — aislamiento revertido | `6112db10` | `39111185147` | **NO CONCLUYENTE** — `error: device offline`; el emulador murio tras la primera fase (`OK (14 tests)`), antes del suite completo |
| A — aislamiento revertido | `6112db10` | `39111805004` | SUCCESS |
| B — #763 integra | `820cdbf6` | `39108879690` | FAILURE — 1 de 828: `MapSearchScreenTest.darkSearchKeepsErrorsAndCollapsesOnlyAfterResolution` |
| B — #763 integra | `820cdbf6` | `39111196125` | SUCCESS |

Acumulado sobre el mismo codigo:

| Codigo | Ejecuciones completas del suite | Fallos |
|---|---|---|
| #763 (`602e476d`, `56ee08e6`, `820cdbf6`) | 9 | 3 — `AppNavigationTest` x2, `MapSearchScreenTest` x1 |
| `main` `8151e469` | 3 | 0 |
| A, aislamiento revertido | 2 comparables (+1 abortada por el emulador) | 0 |

### La evidencia que descarta mi propia hipotesis

El tercer fallo es el que decide. `MapSearchScreenTest` **no usa `IsolatedAppStateRule`**: monta `createComposeRule()`, sin actividad y sin estado de proceso. Solo ocho clases usan la regla (`AccountAuthMainActivityTest`, `AccountAuthScreenTest`, `AppNavigationTest`, `ContextNotificationPermissionTest`, `FirstReminderPermissionTest`, `IsolatedAppStateRuleTest`, `MainMapNavigationTest`, `MainReminderPersistenceTest`) y esa no esta entre ellas. Un fallo fuera del radio de alcance del cambio no puede explicarse por el cambio.

A eso se suma que una de las ejecuciones de la rama A murio con `error: device offline`: el emulador del runner se cayo a mitad del trabajo. El entorno de este workflow no es estable por si mismo.

### Conclusion sustentada

Los tres fallos comparten una sola forma: **una espera de 10 s que no se cumple en un suite de 828 pruebas instrumentadas, ~22 min sobre un unico emulador**, con victima variable — dos veces el `pressBack()` posterior a `scenario.recreate()` en `AppNavigationTest` (foco de ventana nunca recuperado), una vez el colapso del buscador en `MapSearchScreenTest`. La tasa observada es de aproximadamente 1 de cada 3 ejecuciones de evidencia. Los 3 verdes de `main` y los 2 de la rama A son perfectamente compatibles con el azar a esa tasa (p ≈ 0,3 por ejecucion), de modo que **no demuestran lo contrario** y no se presentan como prueba.

Por tanto: **el cambio del aislamiento no queda demostrado como causa**, y tampoco queda demostrado que sea inocente con solo 2 ejecuciones verdes. Lo que si queda demostrado es que el fenomeno excede el radio de alcance del cambio.

### Correccion minima recomendada

Independiente del origen del fallo intermitente, el commit `fc702347` introdujo una regresion concreta de robustez al sustituir el objeto `IsolatedDatabaseBinding` por un acceso directo al campo: se perdio el `synchronized(MaginaOlivoDatabase.Companion)` que coordinaba el intercambio del singleton de base de datos con el inicializador de produccion. Recomendacion: **restaurar `IsolatedDatabaseBinding` (y su `synchronized`) conservando el nuevo aislamiento de `noBackupFilesDir` y el reinicio de `AccountRuntime`.** Es un cambio de arnes, pequeño, sin efecto sobre produccion, y correcto con independencia de la causa del intermitente. No se aplica sin autorizacion.

Para el intermitente en si, lo que haria falta no es mas repeticion ciega sino evidencia: capturar logcat y volcado de ventanas (`dumpsys window`) en el fallo, lo que exige tocar el script de evidencia (infraestructura compartida). Queda propuesto, no hecho.

### Limitacion declarada

No se recogio logcat, estado de foco ni datos de recreacion de Activity con instrumentacion identica en ambas versiones: hacerlo exigiria modificar el script de evidencia compartido o la propia #763, y #763 no debia tocarse. Se deja anotado como limitacion del experimento, no como dato obtenido.

