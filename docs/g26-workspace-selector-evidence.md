# G2.6: selección de workspace por cuenta

Base de integración: `5a2a2acc3f815310d5cb872456ada23807cc13fa` (`codex/g26-integration-temp`). La rama de este bloque es `codex/g26-account-workspace-selector`.

## Modelo de acceso

- **Identidad**: sesión Auth persistida por `AccountRepository`, vinculada al issuer configurado. Se admite sin red mientras el token no haya caducado. Una sesión caducada bloquea el acceso protegido.
- **Workspace local sin vincular**: fila Room anterior sin recibo de importación remoto y con estado local o una operación WORKSPACE pendiente. Su `owner_user_id` generado no prueba titularidad de ninguna cuenta. Se conserva en Room y solo se muestra en el ámbito local sin sesión autenticada.
- **Workspace remoto conocido**: fila Room cuyo recibo `delivery_import_receipts` procede de una recepción G2.5 verificada, con `issuer` y `account_id`. Si existen recibos incompatibles para el mismo workspace, se bloquea.
- **Selección activa**: fila nueva `workspace_selections` con clave `(issuer, account_id)` y `workspace_id`. La selección no crea UUID, outbox ni operación de red. Se valida nuevamente en cada resolución; una fila inexistente o no autorizada no causa sustitución automática.

`findFirstActive()` no participa en la decisión. El estado de Home y las listas de fincas se vacían cuando cambia la selección o se pierde el acceso. La migración 30→31 solo crea la tabla de selección; no altera ni borra las tablas con datos agrícolas, outbox, PREPARE, recibos o fotografías. El esquema exportado es `31.json`.

## Límite del contrato remoto

El recibo G2.5 prueba que una cuenta recibió un lote de un workspace en ese momento. **No constituye una lista remota actual de membresías ni demuestra que una autorización no haya sido revocada.** Workspaces remotos sin recibo G2.5 permanecen inaccesibles. Para ofrecerlos o verificar revocaciones hace falta un contrato remoto de listado/autorización por issuer, cuenta y workspace, con semántica offline y de caducidad definida. No se ha inventado una titularidad a partir de `owner_user_id` ni se ha modificado SQL remoto. Esta dependencia requiere autorización separada antes de cambiar el contrato.

## Reproducción

En API 35 desechable:

```sh
gradle testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest
gradle connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isivoltpro.maginaolivo.data.local.RoomMigrationTest,com.isivoltpro.maginaolivo.data.local.WorkspaceSelectionRoomTest
```

CI: `.github/workflows/g26-workspace-selector.yml` valida el SHA de la PR sobre `codex/g26-integration-temp`. Compila binarios debug temporales para el emulador, sin subir APK ni otros artefactos binarios. Los resultados SQL y G1c de la base validada se conservan; no se ejecutan de nuevo en este bloque.

## Veredicto

La implementación local y la migración tienen GO de prueba cuando CI confirme el mismo SHA. **G2.6 completo permanece NO-GO** para declarar autorización remota actual o asignar workspaces remotos sin recibo hasta disponer del contrato de membresía/autorización. No se ha modificado B ni D.
