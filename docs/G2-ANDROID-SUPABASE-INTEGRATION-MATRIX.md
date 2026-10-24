# G2.1 ↔ G2.4 — matriz de integración Android / Supabase

**Contrato de integración 2026-10-24:** #773 (HEAD `696d5d22`) está apilada sobre #765. Esta es la única matriz para acordar el adaptador G2.4 con Claude; cualquier discrepancia se registra aquí y se resuelve antes de conectar componentes. Ninguna RPC G1c/G2.1 está desplegada en un Supabase persistente ni conectada al emisor Android. Gate 21 físico sigue abierto. Esta matriz no autoriza HTTP agrícola, un worker, despliegue ni merge.

## Identidad y límite de autoridad

- El cliente envía `apikey` pública y `Authorization: Bearer <access_token>` de la sesión verificada. El servidor toma la cuenta de `auth.uid()` y los claims verificados por el gateway; ningún UUID, email, finca, `actorId` o `issuer` enviado en JSON concede acceso. Una sesión anónima, caducada, cambiada o sin consentimiento vigente bloquea el envío y la aplicación local del ACK.
- El sujeto del diario Android es el par exacto `issuer|userId`. La reserva G1c recibe `p_request` con `payloadVersion=1`, `operationId`, `farmId`, `localWorkspaceId`, `consentId`, `consentTextVersion`, `manifestSha256` y `aggregateVersion`; `p_remote_workspace` es el UUID del espacio remoto ya creado. La respuesta tiene `issuer`, `actorId`, `operationId`, `farmId`, `payloadHash`, `remoteWorkspaceId` y `receiptId`. El hash usa los campos UTF-8 prefijados por su longitud de 4 bytes según `ReservationRequest.payloadHash()`.
- El consentimiento y la reserva no son una autorización general para importar datos. Android comprueba el consentimiento actual por finca antes de cada envío, tras cada espera de red y antes de aplicar un recibo. Logout o cambio de cuenta dejan la operación pendiente para su sujeto original.

## Secuencia remota ya contratada

Todas las llamadas van por HTTPS a `/rest/v1/rpc/<nombre>` con la clave pública del proyecto y `Authorization: Bearer <JWT de la cuenta>`; el servidor deriva el actor de Auth. El cliente conserva sus UUID, el JSON exacto y el ID de operación antes del primer envío. Las operaciones de creación son pasos separados: el recibo de uno no autoriza saltar los siguientes.

| Paso | RPC y argumentos JSON | Resultado que debe guardar Android | Prueba efímera / límite |
| --- | --- | --- | --- |
| Espacio | `create_own_workspace(p_operation_id,p_workspace)` | UUID, versión y recibo de la misma operación | Creación/replay, colisión y actor ajeno; no adopta un UUID remoto existente. |
| Reserva G1c | `reserve_local_farm_g1c(p_request,p_remote_workspace)` | `receiptId`, operación, actor, finca, issuer y hash del manifiesto | La suite nativa verifica firma JWT del gateway; el issuer HTTPS de la reserva es **sintético** porque GoTrue local emite HTTP. Falta prueba con issuer real de DEV antes de activación. |
| Finca | `create_reserved_farm(p_operation_id,p_reservation_operation_id,p_reservation_receipt_id,p_farm)` | UUID y recibo de creación; nunca inferir titularidad por nombre, email o Catastro | Exige reserva de la misma cuenta/operación y conserva campos locales. |
| Parcela y pertenencias | `create_own_parcel(p_operation_id,p_parcel)`; lectura `get_own_parcel(p_parcel_id)` | Recibo y agregado completo, incluidos intervalos exactos y pertenencias históricas | Rechaza otras fincas/workspaces, solapes y colisiones sin escrituras parciales. |
| Campaña | `create_own_campaign(p_operation_id,p_campaign)`; lectura `get_own_campaign(p_campaign_id)` | Recibo y snapshots de parcela completos e inmutables | No sustituir nombres, superficie o geometría histórica por el estado actual. |
| Pesada | `sync_own_delivery_v2(p_operation_id,p_delivery)` | `operation_id`, `entity_id`, `remote_version`, `payload_version=2`; el hash autorizado se recupera por operación | La pesada y sus parcelas se escriben atómicamente. `weightGrams:null` significa kg no repartidos, nunca cero inventado. |
| Gasto básico | `sync_own_expense(p_operation_id,p_expense)` | UUID, versión y recibo; el importe sigue perteneciendo al ledger Expense | #773 exige pertenencia activa de la parcela a **esa** finca en `expenseDate`, además del workspace. No cubre todavía todos los hijos Room de gasto/compra. |
| ACK tras timeout | `check_sync_operations(p_operation_ids)` en lotes de hasta 200 | `confirmed_receipts[]` con `operation_id`, `entity_id`, `entity_type`, `remote_version`, `payload_version`, `payload_hash` | Es el recibo **histórico de esa operación**, aunque el agregado tenga una versión posterior. Otra cuenta recibe lista vacía. Ausencia de recibo no prueba fallo del envío. |
| HEAD actual | `get_own_delivery_v2(p_delivery_id)` | `delivery.version` y `head_payload_hash` junto al agregado/hijos | El hash procede solo de una operación `DELIVERY` del mismo actor y de la versión actual. Es `null` si no existe una coincidencia única: Android debe mantener conflicto/bloqueo, no adivinar un ACK. |

## Regla de recuperación G2.4

1. El diario Android conserva antes de HTTP actor, issuer, consentimiento, finca, operación, versión base, payload exacto y hash. Un timeout no cambia el UUID de operación ni el payload.
2. Tras reinicio o respuesta perdida, consultar `check_sync_operations`. Si hay recibo, comprobar actor de la sesión, entidad, versión y hash contra la operación local. Si no aparece, consultar el HEAD y reintentar **la misma** operación solo bajo autorización vigente; no crear otra operación para ocultar incertidumbre.
3. Para un ACK retenido después de revocación, el agricultor debe confirmar la reconciliación, renovar consentimiento y mantener `DeliveryDispatchLease` durante consulta y aplicación. Comparar el recibo histórico con `delivery.version` y `head_payload_hash`; `null` o discrepancia son conflicto, no sincronización confirmada.
4. `23505` se traduce en conflicto HTTP 409; `42501` deniega acceso (403 con JWT válido); `22023` rechaza payload (400). Una respuesta HTTP por sí sola no reemplaza la escritura duradera del recibo local.

**Adaptación exacta para la primera Pesada:** `RemoteDeliveryState.operationReceipt` proviene exclusivamente de `check_sync_operations([operationId]).confirmed_receipts`; `headVersion` proviene de `get_own_delivery_v2(deliveryId).delivery.version` y `headPayloadHash` de `head_payload_hash`. El recibo incluye `payload_version=2` y SHA-256 del `p_delivery::text` de PostgreSQL JSONB, que Android reproduce con `deliveryPayloadHash`; no se calcula sobre el JSON de lectura. Si el HEAD es `null`, tiene otra versión/hash, o el actor/issuer/consentimiento difieren, Android mantiene bloqueo/conflicto y no marca la pesada como sincronizada.

**Primer ensayo conjunto:** dos instalaciones DEV con la misma cuenta de pruebas y una pesada identificable, sin adjunto. A crea offline finca/parcela/campaña/pesada, confirma reserva y envía en orden; se corta la respuesta de la pesada tras commit; A recupera el recibo por el mismo UUID, compara HEAD y conserva kilos/parcelas; B lee el agregado usando el UUID conocido del guion de prueba, sin duplicados. Repetir con otra cuenta debe negar lectura y ACK. Fotografías y pull general quedan como gates distintos: el RPC de lectura por UUID permite este ensayo dirigido, pero aún no constituye un mecanismo de descubrimiento o sincronización bidireccional completa.

## Casos para la integración conjunta con Claude

| Caso | Backend efímero | Android G2.4 | Gate conjunto pendiente |
| --- | --- | --- | --- |
| A offline → servidor → B | RPC y RLS por actor probadas con JWT GoTrue; reserva usa issuer sintético | Room/outbox/lease/ACK preparados sin transporte | Dos dispositivos con misma cuenta y datos sintéticos, sin pérdida ni duplicado. |
| Respuesta perdida / proceso muerto | Replay y recibo histórico por UUID; HEAD distingue versión actual | Diario y retención de ACK preparados | Matar proceso en cada checkpoint y recuperar el mismo UUID/payload. |
| Dos ediciones / CAS | Conflicto 409 y recibo de la versión aplicada | Rebase local y bloqueo existen como piezas separadas | Conflicto real, resolución explícita y kg/importes intactos. |
| Cambio de cuenta o revocación | RLS y RPC deniegan actor ajeno; gasto cross-farm denegado | Consentimiento y lease verifican identidad local | Revocación durante envío, token vencido, logout y cambio de cuenta. |
| Binarios | Ningún recibo de metadatos acredita el fichero | Fotos locales deben sobrevivir | Subida privada, hash/tamaño, interrupción y recuperación; contrato G2d pendiente. |

## Dependencias que bloquean activación

- #772 endurece G0 (seis funciones dejan de ejecutarse como `anon` y cuatro triggers fijan `search_path`) y ya se aplicó a `magina-olivo-dev` con autorización separada. Su PR sigue DRAFT; hay que incluir ese endurecimiento en la base aprobada antes de G1c/G2.1 y repetir permisos/orden de migraciones. #765 y #773 también siguen DRAFT. Las propuestas G1c/G2.1 se cargan solo en Supabase **efímero** durante CI, no en DEV persistente.
- Antes de la primera llamada Android a la reserva, contrastar el `iss` literal del JWT de Auth DEV firmado por el gateway con el issuer HTTPS que devuelve G1c y con el sujeto persistido en Android; comprobar firma, expiración, cambio de cuenta y rechazo del token de otro proyecto mediante la ruta HTTP real. El issuer HTTPS de la suite nativa es sintético y no acredita esta validación. Una discrepancia impide conectar; no se normaliza silenciosamente en el cliente.
- Claude debe conectar sesión verificada, consentimiento vigente, captura Room/diario, transporte, lease, ACK y aplicación transaccional. Este documento no modifica su rama Android.
- Falta sincronización completa de hijos Room, pull/cursor, borrados y adjuntos. No marcar una cuenta o finca como «sincronizada» por la sola creación remota.
- Gate 21 físico y Gate G2 extremo a extremo siguen pendientes; producción permanece intacta.
