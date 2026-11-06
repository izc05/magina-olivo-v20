# G2.6: contrato remoto de autorización de workspaces

Base: PR #779, `958953be081fa01484496fbeac503b24c4f5a227`. Rama aislada: `codex/g26-remote-workspace-auth`. El SQL de esta rama **no se ha desplegado en DEV ni producción**.

## Migración exacta

Archivo: `supabase/migrations/20261029044901_g26_workspace_authorization.sql`

SHA-256 de los bytes UTF-8/LF versionados: `19131ffdbd864e6f3416742d31a4b14451eb0c700d6835af4986fd8672ab1fd3`.

La migración crea `workspace_memberships`, dos RPC de solo lectura, políticas RLS y permisos mínimos. No cambia ni borra filas agrícolas. Está fijada al issuer **DEV** `https://zubpicdibqbmewxeurhm.supabase.co/auth/v1`; una futura migración de producción requerirá su propio issuer y revisión separada. No hay backfill desde `owner_user_id` local ni desde recibos G2.5. Un permiso de finca tampoco se convierte en membresía de workspace.

`list_authorized_workspaces_v1()` devuelve contrato versión 1 con issuer, subject, instante de verificación, vencimiento del JWT y workspaces con rol, permisos, versión y caducidad individual. `verify_workspace_access_v1(workspace_id, permission)` responde autorizado/no autorizado sin revelar metadatos de un workspace ajeno. PostgREST verifica la firma del JWT; las RPC comprueban explícitamente issuer, subject y expiración. El propietario tiene `read`, `write` y `manage_members`. Una membresía solo cuenta si está `ACTIVE`, no revocada, dentro de su intervalo de validez y contiene `read`; la RPC de verificación exige además el permiso solicitado. La tabla de membresías no admite escritura directa desde `authenticated`.

## Política Android y offline

Room 31→32 añade solo `workspace_authorization_cache`, con clave `(issuer, account_id)`; conserva selección, workspaces, fincas, outbox, PREPARE, recibos y archivos. La respuesta de servidor se valida y guarda por cuenta. El acceso protegido exige sesión Auth no caducada, coincidencia exacta de issuer y subject, workspace presente en Room y autorización con `read` aún válida. El recibo G2.5 deja de conceder acceso.

La caché vence como máximo a los **15 minutos** desde la verificación del servidor, antes si vence el JWT o la membresía. Android usa un límite local conservador de 13 minutos para tolerar hasta dos minutos de diferencia de reloj. Sin conexión se permite acceso solo dentro de ese límite; después se bloquea, sin elegir otro workspace. Con conexión, una consulta de autorización se intenta al entrar en Cuenta, al seleccionar, y como máximo cada 60 segundos durante acceso protegido. Una revocación se refleja en la siguiente respuesta correcta; durante una desconexión puede tardar como máximo la vigencia restante de la caché. Al volver la conexión, Cuenta solicita una lista nueva. El selector identifica esos workspaces como «autorización en caché», no como titularidad inferida.

Cambiar de sesión o workspace no borra Room, outbox ni archivos y no crea UUID ni operaciones de sincronización. La consulta RPC es de solo lectura. Una respuesta inválida o un conflicto de identidad bloquea el acceso protegido. Los workspaces remotos sin fila local se mantienen inaccesibles hasta un bootstrap autorizado y separado.

## Pruebas reproducibles

- SQL/Auth/RLS: `bash supabase/tests/g26-native/run.sh` en un host con Docker y Supabase CLI 2.120.0; crea y destruye una pila local exclusiva. Comprueba A con varios workspaces, C con membresía de solo lectura, denegación de escritura y selección ajena, issuer distinto, token caducado, anon, revocación, fin de vigencia, workspace borrado y recuperación.
- Android: `gradle testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest`; después `.github/scripts/g26-workspace-selector.sh` en emulador desechable API 35. Repite las pruebas de #779 porque cambian la fuente de autorización y el esquema Room. Añade 31→32, revocación frente a recibo histórico y recuperación.
- CI: `.github/workflows/g26-remote-workspace-auth.yml` hace checkout del SHA de la PR y no publica APK ni artefactos binarios.

## Plan de respaldo antes de solicitar despliegue DEV

1. Confirmar por separado la autorización de despliegue y el SHA-256 anterior. Comparar la estructura real de DEV (`workspaces`, políticas RLS y funciones) con la base usada por la pila efímera. No ejecutar si hay divergencias o políticas desconocidas.
2. Tomar un backup cifrado y de acceso restringido de esquema y datos DEV con `pg_dump -Fc`, más un punto de recuperación verificable. Registrar hora, tamaño, hash y ubicación controlada; no incluir credenciales en logs de CI.
3. Restaurar ese backup en otra base efímera y comprobar conteos y claves de `workspaces`, fincas, entregas, grants y outbox remoto antes de cualquier cambio DEV.
4. Aplicar únicamente el archivo cuyo SHA-256 coincida, dentro de una transacción, con una ventana de mantenimiento aprobada. Validar funciones, RLS negativas, revocación y conteos antes/después. No activar el cliente Android frente a DEV antes de ese cierre.
5. Si falla, detener el despliegue. La recuperación preferida es el punto respaldado/PITR; no borrar automáticamente `workspace_memberships`, porque podría contener datos nuevos. Cualquier rollback de funciones, políticas o tabla necesita revisión de los datos creados tras el despliegue.

## Estado de decisión

El GO/NO-GO se cerrará sobre el SHA final cuando los jobs SQL efímero y Android API 35 estén verdes. La autorización de desplegar en DEV sigue pendiente y se solicitará por separado.
