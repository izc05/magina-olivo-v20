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
