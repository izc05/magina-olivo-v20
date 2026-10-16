# G1b — evidencia local y límites

Implementación: selección explícita de fincas en Cuenta, resumen y casilla de consentimiento. Ledger privado AtomicFile con identidad issuer+UUID, selección y versiones exactas, fecha/texto, checkpoint y hash del manifiesto. Estado PENDING_REMOTE_RESERVATION; no representa titularidad ni permiso servidor. Room v26, UUID, propietarios históricos, registros y adjuntos no se modifican.

## Verificación local

- Compilación DEV y pruebas Android: SUCCESS. 677 JVM, cero fallos; incluye tres pruebas de espera/cancelación/reintento de la corrección #475 al recrear Activity.
- 48 instrumentadas: ledger 5, repositorio 5, UI G1b 3, migración Room 27, Auth storage 3, UI Cuenta 3, MainActivity 2. Todas pasan sobre las mismas fuentes compiladas. Fingerprint de todas las tablas antes/después del consentimiento y bytes de adjuntos comparados.
- Dos procesos reales: persist 1/1, force-stop sin pm clear, recover 1/1. Selección exacta 2/3 fincas, sesión cacheada cifrada/Keystore, reapertura offline, logout/B bloqueados y retorno A; propietarios históricos y archivos intactos. Harness DEV y emulador exclusivamente, con UUID y claves sintéticos aislados.
- Revisión estática independiente completada sin bloqueantes. Se corrigió cancelación de logout al destruir ViewModel, carreras de notificaciones con primer consentimiento/logout, sesión corrupta en receiver y espera de corrección #475.
- Capturas sintéticas: selected, pending, other-account-locked, restored-offline. Panel capturado en host de prueba; el producto usa el espaciado y navegación existentes de Cuenta.
- Lint final y CI remoto pendientes al preparar este registro; no extrapolar los resultados locales a checks de otro SHA.

## Límites y recuperación

Los recursos compartidos del workspace obligan a proteger el conjunto local completo bajo la cuenta elegida; solo las fincas seleccionadas quedan preparadas para reserva. La pantalla lo explica antes de consentir. No se admite todavía repartir ese conjunto entre cuentas ni desvincularlo. Logout y otra cuenta conservan acceso a Cuenta para volver a identificarse, no a agricultura ni notificaciones privadas.

El ledger sobrevive logout/reinicio y está excluido de Android Backup. Una corrupción bloquea acceso y nunca equivale a ausencia de consentimiento. El checksum detecta corrupción accidental, no protege frente a root. No borrar datos de aplicación ni ledger como solución. Una futura exportación/restauración del conjunto deberá incluir y validar este ledger; no hay restauración automática en esta fase.

Copia G1a antes del consentimiento; fallo de lectura/adjunto inexistente/selección obsoleta impide registro. Una copia completada sin commit de ledger puede quedar huérfana: no concede asociación y el retry toma una copia nueva. No hay GC automático ni pruebas de corte eléctrico. Consentimiento nuevo requiere sesión real vigente; identidad cacheada, incluso expirada, permite seguir usando agricultura offline con la cuenta ya elegida. Recovery tokens y cuentas simuladas no conceden identidad local.

G1c está preparado como contrato en docs/07-plans/G1C-REMOTE-RESERVATION-CONTRACT-2026-10-16.md, sin RPC ni SQL desplegado. G1 completa sigue pendiente de reserva/acuse/recuperación con servidor aislado. G2 no iniciada. Sin producción, migraciones persistentes, fusión ni APK candidata.

## Revalidación sobre G1a actualizada

Código G1b 2d1eb355c9d751fbda8daec90af86db25fb82348, base G1a 12420c978e8286688b3dce047690c88a7093d605. Recompilación/JVM SUCCESS (parent-build.log); 24 instrumentadas conjuntas (G1a 9 + G1b 13 + MainActivity 2), cero fallos (parent-native.log). Harness de dos procesos repetido con esa base: ambas fases 1/1 y todos los PASS (parent-process.log). Las tres clases G1b se incorporan al tramo offline de Gate 3 en CI. El lint final se ejecuta por separado tras cerrar el emulador para reducir consumo de memoria; su resultado y los checks remotos se registrarán en la PR sin extrapolarlos a otro SHA.

## Corrección de cierre — controles de 94c876f2

Los controles GitHub de ese HEAD fallaron en dos baterías independientes por tres regresiones tras recrear Activity: AppNavigationTest.globalAddKeepsTheVisibleNotebookFarmAfterCancellingAnotherFarm (A se sustituía por B), AppNavigationTest.cuadernoJornalKeepsGeneralAndCampaignCostsSeparate y MainMapNavigationTest.actualNavigationSwitchesFarmAndRecreatesOnlyItsOwnParcel. Runs 38905439052 (Evidence) y 38905439224 (Android CI). No se clasifican como intermitentes ni se relajan sus aserciones.

Causa: el ViewModelStore hijo del gate se creaba con remember y se borraba en onDispose, incluso durante configuración. Navigation elimina el estado saveable de sus entradas al limpiar esos ViewModels; por eso se recuperaba la ruta, pero se perdían las selecciones. Solución: holder ViewModel retenido por Activity con un único store hijo para la frontera (UUID de cuenta, permiso). Recreación conserva ese store; logout, otra cuenta, cambio de permiso y cierre real lo limpian. No hay caché de stores de usuarios anteriores.

El consentimiento nuevo usa texto versión2. Antes de confirmar muestra: «Las fincas no seleccionadas también quedarán protegidas en este móvil. Todas se abrirán únicamente con esta cuenta. Si cierras sesión, tendrás que entrar con ella para volver a verlas. Con la sesión guardada podrás seguir trabajando sin conexión.» La casilla acepta expresamente asociar las fincas seleccionadas Y proteger todas las fincas del móvil con esa cuenta. Los registros de versión1 se conservan sin reescribirlos ni atribuirles aceptación de versión2; versiones desconocidas se rechazan.

La limitación permanece: no hay reparto de este conjunto entre cuentas ni desvinculación. No cambia titularidad, propietarios, UUID, registros ni adjuntos. Las pruebas de navegación existentes se mantienen intactas, se añaden pruebas JVM de retención/limpieza de frontera y UI/ledger de consentimiento y compatibilidad de texto. La corrección requiere nuevos controles sobre su nuevo HEAD; 94c876f2 no queda acreditado por estos cambios.

Fuentes finales de la corrección: 681 JVM (0 fallos/errores), incluidos 4 del store retenido; compileDevDebugAndroidTestKotlin SUCCESS. No se ejecutó assemble/package ni se generó APK local. Log retained-store-compile.log. El banner de protección total se mantiene visible tras guardar, también para consentimientos v1. Las capturas anteriores de esta carpeta corresponden al HEAD antiguo y no acreditan el nuevo texto v2. Los tests UI/ledger nuevos y las tres regresiones originales esperan controles GitHub sobre el nuevo SHA.

Se añade harness bash de dos procesos a Gate3 offline: fases explícitas persist/recover, force-stop sin pm clear, identidad Keystore cacheada y 2/3 fincas, bytes/históricos intactos y cuenta B denegada. Usa UUID aleatorio y la guarda del emulador desechable antes de cualquier mutación. Self-test con adb falso PASS: secuencia completa sin clear, rechazo de fallo en cualquiera de las fases y rechazo de dispositivo físico. No equivale a ejecución real del harness nuevo: esa evidencia se recogerá en evidence/g1b-process-recovery.txt del run GitHub.
