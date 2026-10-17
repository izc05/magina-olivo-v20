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
