# Cuenta DEV — pruebas y gate por SHA

Proyecto autorizado: magina-olivo-dev, zubpicdibqbmewxeurhm. Solo clave pública publishable local DEV; ninguna credencial administrativa en Android. STAGING/Production sin configuración Auth. Sin migraciones, Data API/Storage ni sincronización agrícola.

## Red real y recorrido de correo

La API pública confirma registro/email habilitados y confirmación obligatoria. El probe Android HTTPS real rechaza credenciales inexistentes y conserva SIGNED_OUT: 1/1. No crea usuarios ni prueba correo.

El propietario ha autorizado el buzón y guardado el callback. La comprobación efectiva con un hash sintético no emitido devuelve HTTP303 a `com.isivoltpro.maginaolivo.dev://auth/callback`; antes devolvía localhost. Véase callback-authorization.json: no verifica ningún usuario ni revela un token real.

`AccountAuthDevJourneyTest` prepara REGISTER → correo/callback → CONFIRMED → LOGIN → RESTORE_OFFLINE_LOGOUT → RECOVER → correo/callback → RESET_LOGIN_LOGOUT. Utiliza runtime/Keystore/API reales, guarda PKCE y compara hashes de todas las tablas SQLite OPEN_READONLY y adjuntos originales entre fases y procesos. La señal offline se inyecta al repositorio; no se presenta como una desconexión física del teléfono. El logout exige rechazo del refresh anterior; el reset, rechazo de la contraseña antigua. **Los resultados de cada fase real y su SHA se registran en la entrega de PR #753; compilar o saltar el test en CI no equivale a ejecutarlo.**

Credenciales sintéticas generadas y cifradas con DPAPI fuera del repositorio. Entrada transitoria privada por stdin/run-as, permisos0600, retirada antes de parsear incluso con error. Sin valores secretos en argumentos del runner, informes ni logcat publicado. CI omite ambos tests remotos salvo opt-in explícito; no envía correos.

## Simulación, custodia y conservación local

663 JVM, sin fallos. UI y protocolo simulados se identifican como tales; Keystore/AES-GCM y Room sí se ejecutan en Android. El caso de conservación contiene finca/parcela/campaña/pesada/jornal no vacíos, owner/outbox y foto sintética; 7 instrumentadas verdes en el bloque40d9aad9 (1 probe real, 3 UI/Auth simulado, 3 custodia). Matriz360/390dp fuente1.3 del bloque anterior documentada aparte.

## Defecto de rotación y regresión

CI40d9aad9: foundation SUCCESS; ambos controles de emulador fallaron por el formulario de recuperación ausente tras recreate; uno registró además SnapshotStateObserver multihilo en ayuda/privacidad.

La reproducción local, ya con espera explícita de UI y reglas Compose v2, terminó con33 casos:31 navegación correctos, entrada normal a Cuenta correcta y solo callback/recreación fallido. RECOVERY persistía en el repositorio, pero se perdía openAccount antes de que navegación consumiese la acción. No bastaba esperar.

MainActivity guarda/restaura únicamente el booleano de navegación pendiente. No guarda URI/código ni credenciales en Bundle. La corrección pasa4/4 dirigidas: callback con recreación, Cuenta/back, onboarding restaurado y ayuda/privacidad. Logs RED/GREEN adjuntos. Las dos clases usan el dispatcher estándar de [Compose test v2](https://developer.android.com/develop/ui/compose/testing/migrate-v2); revisión estática sin nuevos P1/P2.

La espera acotada de ADB continúa comprobando el mismo serial/QEMU, sin reiniciar ni borrar datos; cuatro guardas previas verificadas. El resultado remoto vigente se consulta por HEAD exacto en PR #753 y #744, sin trasladar resultados de un SHA anterior.

Main base193923a851629dcfb5b1dc54dfc9969fbc19c63d. Sin merge/APK candidata/Web V3. Configuración y protocolo manual: docs/07-plans/ACCOUNT-AUTH-DEV-SETUP.md.