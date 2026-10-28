# G2 — Campaña completa con histórico de parcelas

Propuesta sin despliegue. `create_own_campaign(p_operation_id uuid,p_campaign jsonb)` crea cabecera, snapshots de parcelas y recibo CAMPAIGN en una transacción. La finca, las parcelas y sus asociaciones deben existir previamente. `get_own_campaign` devuelve cabecera y snapshots en una única consulta coherente.

## Datos preservados

La entrada contiene exactamente `id`, `workspaceId`, `farmId`, `name`, `startDate`, `endDate`, `status`, `notes`, `parcels`. Fechas ISO reales, con final nullable y nunca anterior al inicio. Conserva los estados Room PREPARATION, ACTIVE, HARVEST heredado y CLOSED; solo PREPARATION puede tener cero parcelas. Nombre hasta 200 caracteres, notas hasta 10.000; hasta 1.000 snapshots y 4 MiB de JSONB.

Cada snapshot contiene exactamente `id`, `parcelId`, `farmIdAtStart`, `farmNameAtStart`, `parcelNameAtStart`, `managedAreaM2AtStart`, `cadastralReferenceAtStart`, `geometryGeoJsonSnapshot`. Conserva nombres históricos, superficie Double nullable no negativa, referencia catastral nullable y geometría textual nullable. No sustituye esos campos por datos actuales ni inventa geometría o superficie. Los nombres tienen límite de 200 caracteres y la referencia catastral de 200. Un dato fuera del contrato se rechaza; no se trunca ni se confirma parcialmente.

El snapshot mantiene la identidad de la finca de la campaña. Se exige que parcela y asociación con esa finca existan en el mismo workspace. Se permite una asociación histórica ya terminada o borrada lógicamente: importar una campaña pasada no exige que la parcela siga perteneciendo hoy a la finca. Esto no deduce fechas históricas ni modifica la asociación. Las principales de acceso proceden de Auth y de las filas actuales autorizadas, nunca de los nombres históricos.

## Integridad y recuperación

Cuenta no anónima, claims vigentes, workspace propio y finca propia vivos. Los bloqueos siguen operación → workspace → finca → parcelas/asociaciones. El bloqueo exclusivo de finca serializa las creaciones por esta RPC y evita que dos peticiones creen simultáneamente campañas ACTIVE/HARVEST diferentes para la misma finca. Los permisos REST preexistentes no se modifican; esto no declara un invariante global frente a escrituras por otros caminos.

La misma operación devuelve la versión original del recibo, sin reescribir cambios posteriores ni volver a validar como actuales las asociaciones históricas. Cambiar cualquier snapshot cambia el hash y produce conflicto. Operaciones nuevas no adoptan cabeceras o IDs de snapshot existentes. Cualquier error, incluido un ID hijo ya ocupado, revierte cabecera, hijos y recibo.

`campaigns.snapshot_format=0` identifica las cabeceras anteriores sin prueba de snapshot completo. La nueva RPC escribe formato 1. La lectura completa y el replay de la nueva RPC no convierten cabeceras de formato 0 en campañas completas.

`campaign_parcels` tiene RLS, FKs compuestas de workspace y acceso SELECT solo para el titular autorizado. Las escrituras directas de clientes están revocadas. En una pesada v2 vinculada a campaña de formato 1, un `campaignParcelId` no nulo debe identificar el snapshot de esa campaña y parcela; no se acepta un UUID arbitrario. Se conserva null cuando el dato local no tiene ese enlace opcional; no se inventa una referencia. Las campañas legadas de formato 0 conservan el contrato anterior de referencia opaca, sin declararlas restauradas por completo.

## Pruebas y límites

Contratos aislados: roundtrip de nombre, superficie decimal, geometría y nulos; replay; fechas y snapshots inválidos sin filas ni recibos parciales. Los claims son fixtures SQL. Suite nativa: recorrido cliente completo, reintentos concurrentes, cambios actuales que no alteran el histórico, lectura coherente, aislamiento, rechazo de escritura directa, colisiones de hijos con rollback, carrera ACTIVE/HARVEST, borrador sin parcelas, cabecera legada incompleta y vínculo de pesada a snapshot incorrecto.

Las parcelas y asociaciones del recorrido nativo aún se crean por REST sin recibo recuperable. Faltan esas RPC, el mapeo y diario Android, la sincronización de modificaciones y borrados, adjuntos y la restauración real entre instalaciones. No se activa HTTP agrícola, se despliega, se fusiona ni se genera APK candidata.
