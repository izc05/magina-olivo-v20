# G2 — Comprobación de preparación remota con una cuenta cliente

## Qué comprueba

`supabase/tests/g2-native/sync_own_data.test.mjs` carga ahora juntas las propuestas G1c y G2 dentro del backend desechable. Las dos pruebas nuevas comprueban su compatibilidad; no añaden un trabajador Android ni activan sincronización.

La prueba de recorrido crea mediante REST autenticado el workspace (RPC idempotente `create_own_workspace`, con replay), reserva de finca, finca (RPC idempotente `create_reserved_farm`, con replay), parcela/pertenencias (RPC `create_own_parcel`, con replay y lectura conjunta) y campaña completa (RPC `create_own_campaign`, con replay y lectura de snapshots). Envía una pesada v2 con kilos sin repartir y referencia al snapshot real, repite la operación, consulta el agregado y recupera su recibo histórico. Otra cuenta no puede leer las filas creadas ni recuperar la pesada. Ninguna de esas filas agrícolas se inserta mediante SQL administrativo o service role.

La prueba de colisión reserva primero una finca para A. B intenta reservarla y crearla en su propio workspace: ambas peticiones deben devolver 409, sin crear la finca. A puede materializar después su reserva. Así se comprueba el disparador G1c junto con los permisos reales de inserción.

## Datos de prueba y límites

- GoTrue crea dos cuentas reales en la instancia efímera. La creación de usuarios y la instalación del esquema son preparación administrativa del arnés.
- El Auth local emite un issuer HTTP. Como en la suite nativa G1c, solo para estas pruebas se sustituye por un issuer HTTPS sintético y se firma con la clave del gateway desechable. Esto verifica firma, autorización SQL y encadenamiento de componentes; **no demuestra emisión HTTPS real de Auth ni conexión Android**.
- El consentimiento y el hash de copia de seguridad son datos sintéticos. No se prueba aquí el ledger ni la copia local Android.
- Las peticiones de preparación son pasos separados, sin atomicidad conjunta. Workspace, reserva, finca, parcela/pertenencias y campaña completa ya disponen de recibos idempotentes. Este recorrido todavía no implementa diario durable Android, reanudación de cada paso tras caída, adopción de filas existentes, ni detección de cambios locales durante la preparación.
- No se sincronizan organizaciones, snapshots de campaña-parcela, adjuntos o borrados. No se cambia producción ni DEV persistente.

## Verificación

Ejecutar `bash supabase/tests/g2-native/run.sh` en un entorno con Docker y Supabase CLI. El arnés acepta únicamente su instancia local recién creada y la destruye al terminar. La comprobación sintáctica `node --check supabase/tests/g2-native/sync_own_data.test.mjs` no sustituye la ejecución nativa.

Antes de conectar Android siguen siendo necesarios el mapeo remoto duradero, recuperación de cada paso de preparación, validación de sesión/consentimiento al enviar, vinculación explícita del intento a la cuenta y pruebas de interrupción y restauración entre instalaciones.
