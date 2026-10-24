# G2.1 ↔ G2.4 — matriz de integración Android / Supabase

**Estado 2026-10-24:** propuesta para revisión en #765 y #773. Ninguna RPC G1c/G2.1 está desplegada en un Supabase persistente ni conectada al emisor Android. Gate 21 físico sigue abierto. Esta matriz no autoriza HTTP agrícola, un worker, despliegue ni merge.

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

## Casos para la integración conjunta con Claude

| Caso | Backend efímero | Android G2.4 | Gate conjunto pendiente |
| --- | --- | --- | --- |
| A offline → servidor → B | RPC y RLS por actor probadas con JWT GoTrue; reserva usa issuer sintético | Room/outbox/lease/ACK preparados sin transporte | Dos dispositivos con misma cuenta y datos sintéticos, sin pérdida ni duplicado. |
| Respuesta perdida / proceso muerto | Replay y recibo histórico por UUID; HEAD distingue versión actual | Diario y retención de ACK preparados | Matar proceso en cada checkpoint y recuperar el mismo UUID/payload. |
| Dos ediciones / CAS | Conflicto 409 y recibo de la versión aplicada | Rebase local y bloqueo existen como piezas separadas | Conflicto real, resolución explícita y kg/importes intactos. |
| Cambio de cuenta o revocación | RLS y RPC deniegan actor ajeno; gasto cross-farm denegado | Consentimiento y lease verifican identidad local | Revocación durante envío, token vencido, logout y cambio de cuenta. |
| Binarios | Ningún recibo de metadatos acredita el fichero | Fotos locales deben sobrevivir | Subida privada, hash/tamaño, interrupción y recuperación; contrato G2d pendiente. |

## Dependencias que bloquean activación

- Integrar y revisar G0/#772 y G1c/G2.1 en orden; #765 y #773 siguen DRAFT. Las propuestas se cargan en un Supabase **efímero** durante CI, no en DEV persistente.
- Revalidar el issuer HTTPS y la firma de JWT contra Auth DEV real; el gateway sintético nativo no lo sustituye.
- Claude debe conectar sesión verificada, consentimiento vigente, captura Room/diario, transporte, lease, ACK y aplicación transaccional. Este documento no modifica su rama Android.
- Falta sincronización completa de hijos Room, pull/cursor, borrados y adjuntos. No marcar una cuenta o finca como «sincronizada» por la sola creación remota.
- Gate 21 físico y Gate G2 extremo a extremo siguen pendientes; producción permanece intacta.
