# #697-MA01: estado real de avisos

Base: main `88129d04c74de9c707097625b94299bc75fd8780`, tras integrar #704 con sus tres checks correctos. Rama: `codex/697-reminder-notification-state`.

## Problema y cambio

Con POST_NOTIFICATIONS revocado, Perfil mostraba «Desactivadas» y «Suenan en este teléfono.» simultáneamente. La preferencia local no representa el permiso ni el canal de Android.

Perfil y Avisos consultan permiso, bloqueo de la app y canal. El texto explica el bloqueo y ofrece ajustes, conservando la preferencia guardada; con acceso permitido no garantiza sonido. Al reanudar se refresca el estado. El canal bloqueado se abre directamente; la solicitud de permiso existente en Avisos se conserva. El publicador devuelve NOTIFICATIONS_OFF cuando el canal está bloqueado. No cambia la política de markFired, horarios, Room, workspace, cantidades ni gastos.

## Evidencia ejecutada

- Regresión inicial: compilación correcta; un test rojo por encontrar la afirmación «Suenan en este teléfono.» (9.657 segundos), antes de editar producción.
- `assembleDevDebug assembleDevDebugAndroidTest lintDevDebug testDevDebugUnitTest`: BUILD SUCCESSFUL, 10m29s. XML: 524 unitarios, cero fallos, errores o ignorados.
- API35, AVD desechable5580: MyProfileScreenTest, NotificationAccessTest, AgendaReminderContractTest, ProfileSettingsContractTest y ReminderPreferencesContractTest: PASS34, 48.981 segundos. Incluyen la regresión, canal bloqueado con permiso concedido, canal permitido/ausente, CTA, preferencias y emisión de aviso. Canales fixture únicos, eliminados al finalizar; no modifican el canal del usuario.
- UI real del mismo AVD: permiso revocado → texto de bloqueo; CTA abre ajustes de app; activar allí y volver → estado permitido. Con permiso concedido y canal Trabajos planificados apagado → bloqueo; CTA abre directamente ese canal. Restaurarlo y volver → estado permitido. Permiso y canal quedaron restaurados.
- Revisión independiente del código: sin bloqueadores. `git diff --check`: sin errores.

Logs y XML locales en `artifacts/696-e2e/697-*`. El AVD retenido5566 y sus fotos/datos no se usaron para estas pruebas. Tres checks del HEAD final pendientes antes de integrar. Sonido, permisos y aceptación físicos de Gate21 siguen pendientes; estos resultados no cierran el Gate.
