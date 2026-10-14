# Cuenta: configuración y pruebas DEV

Esta entrega separa Auth de Room, fotografías, propietarios y outbox. No aplica migraciones de #743 ni usa Data API, RLS o Storage. Las fincas existentes no se vinculan al usuario Auth.

## Estado real

El protocolo HTTPS, la custodia de credenciales, la pantalla y la navegación están implementados. Sin configuración DEV, Cuenta indica que el servicio no está configurado y deshabilita las operaciones reales. En DEV/debug se puede entrar voluntariamente en una simulación, identificada permanentemente; no crea usuarios, envía correos ni valida contraseñas con Supabase. Sus sesiones son ficticias y solo viven en memoria.

Las pruebas JVM usan respuestas sintéticas. Las instrumentadas ejecutan navegación, recreación de Activity, custodia Keystore y conservación de datos sintéticos en Android; el proveedor remoto sigue simulado. Registro, correo, recuperación, refresh y revocación reales requieren verificación contra DEV antes de sacar la PR de borrador.

## Proyecto independiente verificado (15/10/2026)

El propietario ha creado y autorizado exclusivamente `magina-olivo-dev`, referencia `zubpicdibqbmewxeurhm`, URL `https://zubpicdibqbmewxeurhm.supabase.co`. La conexión confirma ACTIVE_HEALTHY en la organización elegida y región eu-west-1. Se obtuvo únicamente la clave pública publishable habilitada y se guardó en `local.properties` ignorado, solo en las propiedades DEV. STAGING y Production no se configuraron. No se ha obtenido ninguna clave privada ni usado credenciales administrativas en Android.

La API pública Auth DEV responde con email/password habilitado, registro permitido, confirmación de correo requerida y usuarios anónimos deshabilitados. La prueba instrumentada opt-in ejecuta HTTPS y rechaza credenciales inexistentes sin importar una sesión. No crea usuarios ni envía correos.

Para completar E2E aún se necesitan un buzón de pruebas autorizado accesible y confirmar la allowlist exacta `com.isivoltpro.maginaolivo.dev://auth/callback` en Authentication → URL Configuration. Ambas entradas se han solicitado al propietario. No desactivar la confirmación ni crear usuarios administrativamente para simular estos pasos.

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