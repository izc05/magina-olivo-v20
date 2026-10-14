# Cuenta: configuración y pruebas DEV

Esta entrega separa Auth de Room, fotografías, propietarios y outbox. No aplica migraciones de #743 ni usa Data API, RLS o Storage. Las fincas existentes no se vinculan al usuario Auth.

## Estado real

El protocolo HTTPS, la custodia de credenciales, la pantalla y la navegación están implementados. Sin configuración DEV, Cuenta indica que el servicio no está configurado y deshabilita las operaciones reales. En DEV/debug se puede entrar voluntariamente en una simulación, identificada permanentemente; no crea usuarios, envía correos ni valida contraseñas con Supabase. Sus sesiones son ficticias y solo viven en memoria.

Las pruebas JVM usan respuestas sintéticas. Las instrumentadas ejecutan navegación, recreación de Activity, custodia Keystore y conservación de datos sintéticos en Android; el proveedor remoto sigue simulado. Registro, correo, recuperación, refresh y revocación reales requieren verificación contra DEV antes de sacar la PR de borrador.

## Proyecto independiente

No usar `magina-olivo` ni `magina-olivo-aventura`. La organización elegida es «Magina olivo aventura», plan Free. La conexión enumera un proyecto activo y uno inactivo, pero la consulta de coste no está disponible y el navegador no arranca: la cuota efectiva y el formulario de creación siguen sin verificarse. No se ha creado un proyecto ni autorizado coste alguno.

En el panel Supabase: seleccionar esa organización, abrir New project, nombre `magina-olivo-dev`, región Paris y contraseña de base de datos conservada por el propietario. Comprobar disponibilidad y Free/0 € antes de crear. Si requiere pago, detenerse. La creación requiere autorización previa del propietario; la selección de organización no la sustituye.

Después de crear, obtener únicamente Project URL y clave pública publishable/anon desde Connect o Settings → API Keys. Nunca usar ni solicitar service_role/secret ni compartir la contraseña de base de datos.

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