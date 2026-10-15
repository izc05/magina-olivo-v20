# Cuenta: configuración y pruebas DEV

Esta entrega separa Auth de Room, fotografías, propietarios y outbox. No aplica migraciones de #743 ni usa Data API, RLS o Storage. Las fincas existentes no se vinculan al usuario Auth.

## Estado real

El protocolo HTTPS, la custodia de credenciales, la pantalla y la navegación están implementados. Sin configuración DEV, Cuenta indica que el servicio no está configurado y deshabilita las operaciones reales. En DEV/debug se puede entrar voluntariamente en una simulación, identificada permanentemente; no crea usuarios, envía correos ni valida contraseñas con Supabase. Sus sesiones son ficticias y solo viven en memoria.

Las pruebas JVM usan respuestas sintéticas. Las instrumentadas ejecutan navegación, recreación de Activity, custodia Keystore y conservación de datos sintéticos en Android; el proveedor remoto sigue simulado. Registro, correo, recuperación, refresh y revocación reales requieren verificación contra DEV antes de sacar la PR de borrador.

## Proyecto independiente verificado (15/10/2026)

El propietario ha creado y autorizado exclusivamente `magina-olivo-dev`, referencia `zubpicdibqbmewxeurhm`, URL `https://zubpicdibqbmewxeurhm.supabase.co`. La conexión confirma ACTIVE_HEALTHY en la organización elegida y región eu-west-1. Se obtuvo únicamente la clave pública publishable habilitada y se guardó en `local.properties` ignorado, solo en las propiedades DEV. STAGING y Production no se configuraron. No se ha obtenido ninguna clave privada ni usado credenciales administrativas en Android.

La API pública Auth DEV responde con email/password habilitado, registro permitido, confirmación de correo requerida y usuarios anónimos deshabilitados. La prueba instrumentada opt-in ejecuta HTTPS y rechaza credenciales inexistentes sin importar una sesión. No crea usuarios ni envía correos.

El propietario ha autorizado el buzón de pruebas y ha guardado el callback DEV. Su autorización efectiva se comprobó con GET /auth/v1/verify usando un hash sintético no emitido, sin seguir la redirección: antes devolvía localhost; después HTTP303 con destino exacto `com.isivoltpro.maginaolivo.dev://auth/callback`. No confirma ningún usuario ni sustituye la prueba del correo. La conexión Gmail disponible pertenece a otro buzón, por lo que los enlaces deberán abrirse o facilitarse por el propietario. No desactivar la confirmación ni crear usuarios administrativamente para simular estos pasos.

No usar `magina-olivo` ni `magina-olivo-aventura`. Las comprobaciones de cuota/creación del 14/10 quedan como historia de preparación; el proyecto DEV ya lo creó el propietario.

## Configuración Android

Configurar fuera de Git (propiedades locales ignoradas o entorno):

```
MAGINA_AUTH_DEV_URL=https://REFERENCIA_DEV.supabase.co
MAGINA_AUTH_DEV_PUBLIC_KEY=CLAVE_PUBLICA_DEV
```

STAGING utiliza `MAGINA_AUTH_STAGING_URL` y `MAGINA_AUTH_STAGING_PUBLIC_KEY`. Production mantiene Auth desconfigurado. Las referencias de los dos proyectos existentes se rechazan; se rechazan URL inseguras y claves privadas. Nunca incluir tokens, contraseñas o claves reales en informes, capturas o comentarios.

En Auth DEV, habilitar email/password y confirmación de correo. Añadir la URL permitida exacta:

```
com.isivoltpro.maginaolivo.dev://auth/callback
```

STAGING requiere su esquema de applicationId propio. Se usa PKCE S256 y solo un código; no se aceptan enlaces con tokens en el fragmento. El esquema personalizado es exclusivo de pruebas; la futura activación de producción deberá evaluar App Links verificados.

## Gate de verificación real pendiente

Con buzón y usuarios sintéticos autorizados: registro con confirmación, retorno a la app, login correcto/incorrecto, reapertura, refresh, sesión offline y logout; recuperación, cambio de contraseña, rechazo de la contraseña antigua; enlaces ajenos, caducados y repetidos; dos cuentas distintas con Room/fotografías/owner/outbox intactos. No activar sincronización ni aplicar tablas o políticas agrícolas.

El intercambio del enlace pertenece al repositorio de proceso y continúa al rotar la Activity. Si Android mata el proceso durante un intercambio incierto, solicitar un enlace nuevo: no se promete recuperar automáticamente ese código. Cerrar sesión offline elimina las credenciales locales y explica que la revocación remota no se ha confirmado.

No generar APK candidata ni fusionar hasta la autorización correspondiente.
## Journey manual opt-in sin secretos en CI

`AccountAuthDevJourneyTest` prepara seis fases reales en Android: REGISTER → correo/callback → CONFIRMED → LOGIN → RESTORE_OFFLINE_LOGOUT → RECOVER → correo/callback → RESET_LOGIN_LOGOUT. Los callbacks pasan por MainActivity, sin reutilizar el código. Confirmación y recuperación deben iniciarse y terminar en el mismo emulador para conservar PKCE.

El test solo se activa con `realDevAuthProject=zubpicdibqbmewxeurhm`, build DEV/debug/QEMU y entrada local privada `no_backup/qa-account-e2e-input.json`. En CI se omite; no hay contraseña, token ni buzón en argumentos del runner. La entrada se suministra por stdin a run-as con umask077 y se elimina antes de parsearla; sus errores nunca incluyen JSON ni valores secretos. Las contraseñas sintéticas del host están fuera de Git y cifradas con DPAPI para el usuario Windows. Nunca copiar esa entrada a evidencia ni adjuntar logcat de sesiones reales.

Cada fase compara todas las tablas SQLite mediante hashes lógicos OPEN_READONLY y las bytes de los adjuntos originales de `files/attachments`; no restaura ni cambia datos agrícolas. Las miniaturas regenerables se excluyen. Una referencia persistida de hashes compara también los intervalos entre fases/procesos. El logout exige rechazo del refresh token anterior; la recuperación exige rechazo de la contraseña antigua y login con la nueva. Solo registrar fases como realizadas tras su ejecución y recepción de correo reales.