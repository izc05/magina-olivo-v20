# G2 — Creación recuperable de la finca reservada

Propuesta sin despliegue. Requiere G1c y G2 cargados en ese orden dentro del entorno desechable.

## Entrada y autorización

`create_reserved_farm(p_operation_id uuid, p_reservation_operation_id uuid, p_reservation_receipt_id uuid, p_farm jsonb)` materializa una finca cuya reserva G1c ya pertenece a la cuenta actual. El cliente conserva el nuevo ID de operación y el payload antes de llamar.

La RPC deriva propietario y creador de Auth y exige claims vigentes de una cuenta no anónima. El issuer HTTPS del token debe coincidir con el almacenado en la reserva. No basta presentar un UUID de recibo: se comprueban conjuntamente cuenta, operación G1c, recibo, finca, workspace e issuer. El workspace debe seguir vivo y pertenecer a la cuenta. No se infiere propiedad jurídica ni se comprueba aquí el consentimiento local Android.

El objeto contiene exactamente los nueve campos de la finca: `id`, `workspaceId`, `name`, `description`, `municipality`, `province`, `notes`, `status`, `coverDocumentId`. Son obligatorios y no nulos `id`, `workspaceId`, `name` y `status`; los cinco restantes admiten null explícito. Estado ACTIVE o ARCHIVED; nombre hasta 200 caracteres no vacío, municipio/provincia hasta 200 y descripción/notas hasta 10.000. No admite identificadores de propietario o creador enviados por el cliente.

`cover_document_id` se conserva como referencia UUID nullable, sin URI del teléfono. No da permiso sobre un documento, no prueba que exista remotamente y **no confirma subida de la portada**; Storage y la sincronización de adjuntos continúan independientes.

## Reintentos y atomicidad

El hash incluye el objeto de finca y las dos referencias de reserva. La operación comparte el espacio de idempotencia con workspace, pesada y gasto. Cambiar cualquiera de esos datos en un reintento produce conflicto; una operación distinta sobre un UUID existente también se rechaza. No se adopta una finca creada previamente por otro camino.

Fila y recibo FARM se escriben en una única transacción. La misma operación concurrente produce una creación y replays del mismo recibo; operaciones distintas sobre el mismo UUID producen una creación y un conflicto 409. Los errores de autorización o validación no dejan fila o recibo parcial.

Se bloquea primero la operación, luego el workspace y después la finca con la misma clave advisory que G1c y el trigger de inserción. El replay verifica que la finca sigue viva y conserva sus principales de acceso; devuelve la versión original sin sobrescribir ediciones posteriores. `check_sync_operations` permite recuperar el recibo histórico por cuenta.

## Verificación y límites

Las pruebas PostgreSQL aisladas comprueban campos, recibo, replay y rechazo de reserva/cuenta/issuer/payload incorrectos. Los claims de esas pruebas son sintéticos. Las nativas comprueban el camino autenticado, carreras de reintentos, UUID y operaciones cruzados, aislamiento, errores sin persistencia parcial y rechazo del replay después de borrar la finca.

El issuer HTTPS de las pruebas nativas de reserva sigue siendo el fixture firmado por el gateway local, descrito en `G2-CLIENT-BOOTSTRAP-CONTRACT.md`; no acredita HTTPS real de producción. Los permisos de creación REST preexistentes siguen disponibles: esta garantía recuperable corresponde a la nueva RPC.

Faltan creación recuperable de campaña, parcelas y relaciones, diario/mapeo Android, consentimiento verificado al enviar, adjuntos y restauración entre instalaciones. No hay activación de sincronización, despliegue persistente, fusión o APK candidata.
