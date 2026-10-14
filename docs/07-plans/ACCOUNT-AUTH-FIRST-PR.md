# Primera PR de Cuenta — alcance autorizado 14/10/2026

**Objetivo:** Cuenta Android en Perfil con registro, login, logout y recuperación por Supabase Auth; conservar íntegro el trabajo agrícola local.

**Base:** main `193923a851629dcfb5b1dc54dfc9969fbc19c63d`, integración #751. Verificar su CI antes de implementar. Worktree propio, rama `codex/issue725-account-auth`. El propietario autorizó esta PR funcional; no su fusión ni producción. Después pidió proveedor simulado explícito mientras se prepara `magina-olivo-dev`, organización «Magina olivo aventura», solo Free/0 € y creación con autorización previa.

**Diseño y límites:** ruta Cuenta anidada en Perfil, mismos componentes/colores; no login obligatorio en esta entrega, porque la transición de datos/ownership no está validada. Repo Auth independiente sin dependencia Room/outbox/adjuntos. Ninguna finca se reclama ni se asigna al UUID Auth. No tablas agrícolas, RLS, RPC, Storage, roles, QR, contratos o sync. Production no tiene configuración Auth ni proveedor simulado. Web V3 permanece pausada. Solo builds DEV/test necesarios para pruebas, nunca APK candidata.

## Seguridad y flujo

- Transporte Auth HTTPS a un proyecto DEV/STAGING explícito, clave publishable o anon exclusivamente; prohibir secret/service_role y el proyecto existente `zzelvbcuxsboafibfxch`. Sin redirects HTTP automáticos, respuestas acotadas y timeouts; errores humanos sin volcar passwords/tokens/payloads.
- Endpoints documentados GoTrue `/signup`, `/token` (password, refresh_token, pkce), `/recover`, `/user`, `/logout?scope=local`. Sin SDK Data/Storage ni sesiones de Web legacy.
- Confirmación y recuperación: PKCE S256, verifier aleatorio cifrado persistido antes de enviar; callback con esquema de la variante y host/ruta exactos, solo código (no tokens de URL). Comprobar flujo pendiente, antigüedad, correo esperado y uso único. En recuperación, sesión restringida al cambio de contraseña; no presentarla como login normal.
- Sesión, refresh token y PKCE cifrados AES/GCM en Android Keystore + archivo atómico `noBackupFilesDir`, separado de Room. Contraseñas solo en memoria y nunca en estado guardado de Compose/Bundle. La identidad Auth es informativa: no concede acceso a fincas.
- Restaurar sesión online valida usuario/renueva token; offline muestra identidad cacheada y caducidad sin prometer validación remota. Cerrar sesión elimina credenciales locales antes del intento de revocación; un fallo offline se explica, no borra datos ni promete logout global.
- Simulación solo DEV/debug y opt-in, banner permanente; usuarios/tokens ficticios en memoria, ningún correo/red/proyecto real. Se prueba el mismo repo/VM/UI con transporte simulado. Producción no ofrece la simulación.

## Bloques de implementación y pruebas

1. **Núcleo Auth y protocolo**: `feature/account/AccountSession.kt`, `AccountRepository.kt`, `SupabaseAuthApi.kt`; tests JVM de validación, respuestas, expiración, renovación, errores, sesión offline, logout fallido, concurrencia y callbacks PKCE ajenos/repetidos. Test rojo antes del código; luego verde y commit.
2. **Custodia y configuración**: `AndroidAccountSessionStore.kt`, `AccountRuntime.kt`, configuración por flavor en Gradle; tests de cifrado/reapertura/tampering y ausencia de secretos/producción. No solicitar claves privadas. Commit tras verificación.
3. **UI y navegación**: `AccountScreen.kt`, `AccountViewModel.kt`, Perfil/AppNavigation/AppDestination y recepción de callback en MainActivity/AppRoot. Password oculto/no guardado, carga/error/conexión/recovery, volver y navegación actual. Tests nativos con provider simulado y Room/archivos sintéticos intactos al entrar/salir/rotar; 360/390 dp y letra grande claro/oscuro. Commit tras verificación.
4. **Entrega**: JVM completa, instrumentadas, GitHub del mismo HEAD y revisión; PR **DRAFT** y #744 actualizado. Código/contratos prueban flujo local, no registro real hasta E2E en `magina-olivo-dev`.

## Entradas de riesgo que deben tener prueba

- Timeout/401 al renovar no convierte un registro local en perdido ni valida sesión caducada offline.
- Login/callback devuelve otro correo o usuario; rechazar y no persistir identidad equivocada.
- Enlace ajeno, expirado, sin verifier, repetido, con fragment de tokens o después de logout: no importar sesión.
- Dos operaciones concurrentes y recreación mientras llega respuesta: no reactivar sesión después de logout ni duplicar flows.
- Keystore/archivo dañado y logout offline: mensaje claro, conservar Room/archivos/owner/outbox.

## Dependencias y evidencia honesta

#743 `ffeec476a7af1345b58f31c6a7dbaddc5bb57678` no fusionada. #752 `84dfa4d481cc24d41a40874fb95b3b76c37e93b8` identifica buckets legacy públicos, política Storage permisiva y lectura de parcelas compartidas incorrecta: no utilizarlos ni aplicar sus migraciones en DEV para esta PR. Room v26/#721 no necesario para un almacén Auth separado; sigue necesario para futura vinculación/sync.

La conexión devuelve plan Free y un proyecto activo/un inactivo; la herramienta de coste falla (`get_cost not returned by tools/list`) y el navegador no arranca. No se crea proyecto ni se autoriza pago. El panel debe confirmar cuota/0 € antes de crear DEV con autorización. Una vez disponible: usar solo URL/clave pública, confirmar email/password y redirect allowlist exacta para DEV; usuarios sintéticos y buzón de pruebas autorizado. Verificar registro→correo→callback→login→restart→offline→logout, credenciales erróneas, recuperación→contraseña nueva→vieja rechazada, PKCE ajeno/replay y dos cuentas sin tocar datos agrícolas. No declarar esa ejecución hecha si solo se ejecutó simulación.

Fuentes primarias consultadas el 14/10/2026: [Auth OpenAPI](https://github.com/supabase/auth/blob/master/openapi.yaml), [PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow), [sesiones](https://supabase.com/docs/guides/auth/sessions), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [plan Free/cuota](https://supabase.com/docs/guides/platform/billing-on-supabase). Changelog: retirada futura de cifrados CBC TLS1.2; conservar HTTPS/TLS moderno del sistema, no excepciones SSL.
