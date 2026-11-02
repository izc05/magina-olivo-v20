# G2 — Primer paso remoto recuperable: crear workspace

Propuesta sin despliegue. `create_own_workspace(p_operation_id uuid, p_workspace jsonb)` crea un workspace nuevo y su recibo inmutable en una sola transacción. Es el primer paso de la preparación; no completa fincas, campañas o parcelas ni conecta Android.

## Contrato

El cliente debe guardar la operación y el payload antes de llamar. El objeto contiene exactamente seis cadenas: `id`, `name`, `countryCode`, `timezone`, `locale`, `currency`. El propietario se deriva de Auth; el cuerpo no admite `ownerUserId`. El ID remoto es nuevo y debe conservarse al reintentar; no se deduce del nombre o del correo ni se atribuye un workspace local a Auth automáticamente.

La solicitud exige una cuenta autenticada no anónima y claims vigentes. Valida nombre no vacío de hasta 200 caracteres, país de dos letras mayúsculas, moneda de tres, etiqueta de idioma y zona horaria conocida por PostgreSQL. Estos son límites de esta RPC, sin transformar los valores enviados.

La clave de idempotencia es `(cuenta, operación)`, compartida con pesadas y gastos. Repetir exactamente la operación devuelve el recibo original y su versión, incluso si otro editor ha cambiado después el nombre del workspace. No reescribe esos cambios. Reutilizar el ID de operación para otro payload o entidad devuelve 409.

Un UUID ya existente produce un conflicto genérico 409, sea propio, ajeno, antiguo o borrado. Nunca se adopta ni actualiza esa fila. Dos operaciones diferentes que compiten por crear el mismo workspace producen una creación y un conflicto. Dos peticiones de la misma operación devuelven un solo recibo persistido. Fila y recibo se confirman o revierten juntos.

El recibo se consulta mediante `check_sync_operations`, con `entity_type=WORKSPACE`, ID, hash del JSONB y versión histórica. La ruta de creación revalida que el workspace sigue vivo y pertenece a la cuenta antes de aceptar un replay. No restaura permisos tras borrado o cambio de propietario.

## Pruebas

- Contratos PostgreSQL en PGlite con claims SQL sintéticos: creación/replay, inmutabilidad, rechazo de UUID existente, solicitudes malformadas y sesiones no elegibles. No prueban firmas JWT.
- Suite nativa desechable: tres peticiones concurrentes de la misma operación, dos operaciones sobre el mismo UUID, edición posterior conservada, recuperación del recibo, aislamiento de cuentas, UUID antiguo propio, sesión anónima real GoTrue y ausencia de filas/recibos parciales.
- El recorrido G1c → G2 utiliza esta creación antes de reservar la finca. Su issuer HTTPS de reserva continúa siendo un fixture sintético, documentado en `G2-CLIENT-BOOTSTRAP-CONTRACT.md`.

La garantía atómica corresponde a esta RPC. Los permisos REST de creación directa preexistentes siguen disponibles; la futura consumidora recuperable deberá utilizar la RPC y su diario, no interpretar una inserción directa como un recibo.

Falta persistir el mapeo y los checkpoints Android y dar el mismo contrato recuperable a los siguientes pasos. No hay migración nueva, despliegue persistente, activación de red agrícola, fusión ni APK candidata.
