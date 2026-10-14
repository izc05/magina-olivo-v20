# Cuenta DEV — continuación 15/10/2026

Proyecto autorizado por el propietario: `magina-olivo-dev`, referencia `zubpicdibqbmewxeurhm`. La conexión confirma ACTIVE_HEALTHY. URL y clave pública publishable solo en configuración local DEV ignorada; no se configuró STAGING/Production ni se obtuvo una clave privada.

## Pruebas reales

- Consulta HTTPS de `/auth/v1/settings` con clave pública: email habilitado, signup permitido, mailer_autoconfirm=false, anonymous_users=false.
- Android API35, emulator-5614: `AccountAuthDevConnectivityTest`, opt-in explícito del proyecto exacto, 1/1 correcto. Usa `SupabaseAuthApi` y `HttpsAuthTransport` reales para unas credenciales inexistentes; recibe CREDENTIALS, estado SIGNED_OUT, ningún token/sesión importado y simulated=false. No crea usuarios ni envía correos. Log adjunto.

Esto prueba conectividad/clave/errores, no registro, confirmación, sesión real o recuperación. Esos flujos esperan buzón autorizado y allowlist de `com.isivoltpro.maginaolivo.dev://auth/callback`, solicitados al propietario. No se deshabilita la confirmación ni se crean usuarios con credenciales administrativas para sustituir el correo.

## Pruebas simuladas y custodia Android

La suite existente usa un proveedor Auth simulado explícito para login/signup/recovery/offline/logout. Se amplía el caso de conservación con registros no vacíos de finca, parcela, campaña, pesada y jornal; compara todas las tablas, owner/outbox y bytes de una fotografía sintética. Keystore/cifrado y Room se ejecutan realmente en Android, pero la identidad de esos casos es ficticia. Los resultados de la repetición se añadirán a la entrega por SHA; no equivalen a E2E remoto.

## Corrección CI

HEAD anterior `080af87ada3c7f13926015bc1e5637f2d2aab2d2`: foundation y gate3-emulator SUCCESS. gate3-evidence run38628110744 ejecutó **793 instrumentadas correctas**, luego falló por `adb: device offline` al pasar a verify-map-device, no por un test de Cuenta.

Se fija ANDROID_SERIAL después del preflight del emulador desechable y se espera el mismo transporte/QEMU hasta seis intentos, con comandos limitados a tres segundos y pausas de dos segundos. No reinicia ADB, cambia dispositivo ni borra datos; si no se recupera, falla. Cuatro pruebas de stub verifican recuperación transitoria, fallo permanente, rechazo no-QEMU y rechazo físico antes de ADB. Ambos workflows ejecutan esas guardas.

El nuevo resultado remoto se documentará con el SHA definitivo en la PR #753 y #744; todavía no se declara verificado. Main sigue `193923a851629dcfb5b1dc54dfc9969fbc19c63d`.

PR en DRAFT. Sin producción, sincronización, merge, APK candidata ni cambios Web V3.
Repetición local completada: 663 JVM, cero fallos/errores; lint DEV y compilación app/test correctos. Instrumentadas 7/7: un probe real Auth DEV, tres pantallas con Auth simulado y tres de Keystore. El caso de datos agrícolas no vacíos pasó con comparación de todas las tablas y fotografía tras login/offline/recreación/logout simulados. Revisión estática sin nuevos P1/P2. Registro/correo/recuperación y sesión real continúan pendientes; no se declaran probados.
