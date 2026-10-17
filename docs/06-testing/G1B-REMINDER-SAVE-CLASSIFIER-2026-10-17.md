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
