# Sincronización manual 1.0 — cierre y traspaso para la integración

Estado: **Draft PR #852** (`codex/g26-808-manual-sync`), apilada sobre #849 → #846. Sin fusionar con `main`. Sin SQL desplegado.
Ningún cambio en Supabase DEV/PRODUCTION ni en los emuladores históricos.

## Qué está verificado

Prueba de aceptación solo con la APK DEV debug normal (pantalla de acceso, pantallas de la app y botón «Sincronizar ahora»),
dos emuladores desechables A2/B2 y una pila Supabase local desechable (`supabase/tests/g26-ab-apk/start.sh`):

- A2: inicio de sesión, finca, parcela, campaña creada y activada, consentimiento de la finca, pesada → «Completado».
- B2 (vacío, misma cuenta): primera pulsación «Pendiente: confirma la finca»; tras el consentimiento, «Completado»; ve finca,
  parcela, campaña activa y pesada en sus pantallas.
- B2 → A2: una pesada nueva de B2 llega a A2.
- Repeticiones: «0 enviados, 0 recibidos». Servidor y ambos móviles: 1 finca, 1 parcela, 1 campaña, 1 jornada, 2 pesadas,
  2.034.000 g. Sin duplicados.
- Estados visibles: sincronizando, completado, pendiente (con motivos), error. Sin sincronización automática (test de arquitectura).

## Funcionamiento

- Solo el botón inicia una ejecución (`ManualSync.requestSync`, único llamador `ManualSyncPanel`).
- Envío en orden causal con los emisores existentes: espacio → finca → parcela (+ cambios) → CREATE de campaña → cambios de
  campaña (CAS) → jornada → pesada v3 + HEAD → borrados de pesada. Recepción (solo espacios propios): padres → campañas
  (`list_own_campaigns_v1`) → jornadas con pesadas de las fincas consentidas en ese móvil.
- Solo el sabor **dev** construye el transporte, la ruta v3 de pesadas y la verificación de propietario (`app/src/dev/.../SyncWiring.kt`).
  Staging y production: ruta v2, sin verificación, sin sincronización; el panel dice «no disponible».
- La APK dev **debug** confía en CA de usuario (`debug-overrides`, solo debug) para la prueba local. La release no cambia.

## Limitaciones conocidas de 1.0 (documentadas, no se amplían)

- **Fincas ya sincronizadas**: sus ediciones posteriores no se envían; quedan en el móvil y el estado lo indica.
- **Colaboradores**: solo se reciben espacios de los que la cuenta es propietaria; los compartidos no.
- **Gastos, labores, documentos** (y otros tipos no listados): no se sincronizan; el panel lo indica como nota.
- **Parcelas de campañas confirmadas**: cambiar sus parcelas se rechaza (`confirmed_campaign_parcels_locked`), sin pérdida de datos.
- **Consentimiento por móvil**: cada móvil debe confirmar sus fincas una vez (G1b); sin él no se envía ni recibe nada de esa finca.
- Una pesada registrada **antes** de consentir la finca queda en v2 sin cuenta y no viaja como jornada a otro móvil.
- El espacio inicial vacío que la app crea sola no se publica.

## Para el integrador (Codex)

- Commit exacto: ver el informe de cierre de la PR #852 (último commit de `codex/g26-808-manual-sync` con CI verde).
- Cadena: #846 (`0ef39531`) → #849 → #852. Integrar #852 trae #849 y #846.
- **#850 ya está incorporada** (merge `a5b1815f3` de `f1018a269`): no volver a aplicarla.
- Incluye además una corrección fuera de `sync/`: `LocalWorkspaceRepository.ensureLocalWorkspace` usa el refresco con límite
  de 60 s (antes provocaba ~27 peticiones/s en un segundo móvil). Si otra rama toca ese archivo, conservar esta línea.
- Precauciones:
  - `ArchitectureBoundaryTest` permite el cableado solo en dos archivos dev (`SyncWiring.kt`, `ManualSyncCoordinator.kt`);
    no mover ese código a `main`, staging ni production.
  - Toda nueva variante necesita su propio `SyncWiring` (staging/production devuelven null/v2).
  - `AccountRoute(..., manualSync=...)` en `AppNavigation`; el `AccountRoute` del gate de acceso no lleva sincronización.
  - Runner Room: `OK (271 tests)`.
  - Una APK para DEV real necesita `MAGINA_AUTH_DEV_URL` y `MAGINA_AUTH_DEV_PUBLIC_KEY` reales (no versionados).
    La APK de prueba local lleva la clave demo de la pila local y **no** sirve contra DEV.

## SQL necesario en DEV (NO ejecutado)

Historial de DEV registrado el 29/10/2026 (`docs/g26-dev-backup-restore-2026-10-29.md`): diez versiones, de `20261011120000`
a `20261027155737`, sin G2.6. Hay que **volver a comprobarlo** antes de nada.

Orden (el mismo que la pila desechable aplicó en la prueba A2 ↔ B2):

| # | Archivo | SHA-256 |
|---|---|---|
| 1 | `supabase/migrations/20261029044901_g26_workspace_authorization.sql` | `19131ffdbd864e6f3416742d31a4b14451eb0c700d6835af4986fd8672ab1fd3` |
| 2 | `supabase/migrations/20261029104720_g26_campaign_cas.sql` | `7f771415720feab118eb50cd45704a113525ffc07366e8957997e6405f49b618` |
| 3 | `supabase/proposals/g26-campaign-head-v1.sql` | `ff9eb793be72d3e74a06748e3748bf9e4e6e26100ef1dc1aff2bdd9ba00a4ab5` |
| 4 | `supabase/proposals/g26-campaign-receive-v1.sql` | `53bdd074fa77d2a80d546b3bc2176008070c9419114b5771f8bed34f754935f2` |
| 5 | `supabase/proposals/g26-parent-receive-v1.sql` | `99872fce07bfc0064ac94a6db3981675168702c1bb7f4161564311ddbf4c48ad` |
| 6 | `supabase/proposals/g26-fpm-manage-hardening.sql` | `990201320b7bd55bd90cae15363c46390b5984ce7bda5a2b3e42ba73983484ce` |
| 7 | `supabase/proposals/g26-parcel-mutation-v1.sql` | `38f9ab8d21e41471e61fdc2e8e0dde21e732349f2117ae217788f1b1700cb561` |
| 8 | `supabase/proposals/g26-historical-writers-v1.sql` | `d06247725cb68d4b8c4722a23abda7e0ef26d6f6c8cc66e32e7af36824a1eeb0` |
| 9 | `supabase/proposals/g26-delivery-head-v3.sql` | `025cf051dc04ccc99eb12c15c792c1bbff7a45447db5a7086b741995bfd5b646` |
| 10 | `supabase/proposals/g26-harvest-writer-v1.sql` | `9743097c0ca7917598865642451ac60fc93e5989124602aeab5845f550a06848` |
| 11 | `supabase/proposals/g26-delivery-delete-v1.sql` | `bb9bb731f2a732140cc9a16a7a358963881f02d73e95364347fd679cbd99f399` |
| 12 | `supabase/proposals/g26-harvest-head-v2.sql` | `28d5f87656049c074a4ec6c6876c9c6d2304e8041ca5f5274e76bc24e64c6567` |
| 13 | `supabase/proposals/g26-harvest-receive-v1.sql` | `0fd7a9d45337637354ad758dd6147c36a7126cf39b0b7844837cda375b191098` |
| 14 | `supabase/proposals/g26-campaign-list-v1.sql` | `440324dfe5fc86fddd682bc7eecb5219e0f3e13624289dd3a8e5827544b8fb89` |

- 1 y 2 son migraciones; 3–14 son propuestas sin versión de migración: hay que decidir su versión y nombre antes de registrarlas.
- `g26-parent-receive-v2.sql` no lo usa la sincronización manual.
- La pila local también aplica `g1c-reservations.sql`, `g2-sync-own-data.sql` y `g2-parcel-bootstrap.sql`. Comprobar que DEV
  ya los contiene (presumiblemente las versiones `2026102520321x`) antes de aplicar nada; si no, NO-GO y revisar.
- El SHA de 1 coincide con el ensayado en el runbook del 29/10.

Requisitos antes de ejecutar: respaldo **actualizado** (el del 29/10 ya no basta) y restauración verificada, autorización
expresa, una transacción por archivo con precondiciones, sin `supabase db push`.
