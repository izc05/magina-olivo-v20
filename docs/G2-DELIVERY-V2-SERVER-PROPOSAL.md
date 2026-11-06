# Contrato remoto candidato de Pesada v2
Fecha: 2026-10-23. Solo propuesta y pruebas efímeras. Sin migración persistente, despliegue ni transporte Android.

## Modelo
delivery_parcels es el hijo canónico remoto de deliveries: UUID de fila, workspace, pesada, parcela, referencia histórica campaignParcelId, nombre histórico, gramos nullable y EXACT/UNALLOCATED. FK compuestas a pesada/workspace y parcela/workspace; unicidad pesada/parcela. RLS permite lectura autorizada; solo RPC puede escribir.
deliveries.snapshot_format impide actualizar v2 desde el contrato antiguo. La misma operación guarda cabecera, destino por UUID/etiqueta y todos los hijos, incrementando una sola vez la versión de la cabecera.
campaignParcelId y destinationOrganizationId se conservan como referencias históricas; sus entidades/dependencias todavía necesitan bootstrap coherente antes de conectar Android. No se ha creado otro servicio de organizaciones.

## API
sync_own_delivery(UUID,JSONB): v1; rechaza campos completos que no puede representar.
sync_own_delivery_v2(UUID,JSONB): exige parcelas y valida tipos, pesos, pertenencia finca/workspace, duplicados e historial.
get_own_delivery_v2(UUID): devuelve cabecera y parcelas desde una sola instantánea SQL, solo al titular autorizado.
check_sync_operations conserva compatibilidad y añade payload_version del intento inmutable. Un reintento conserva la versión histórica y no vuelve a aplicar hijos.

## Concurrencia y límites
Orden de bloqueo: workspace, finca, campaña, parcelas/membresías y pesada. Se revalida workspace de finca tras la consulta inicial. CAS, bloqueo por operación y recibos existentes se reutilizan.
Pesos desconocidos siguen null. Un reparto exacto debe conciliar; no admite ceros, negativos o valores de tipo incorrecto.
Cambios de identidad, nombre histórico o vínculo de una parcela ya presente se rechazan. Los envíos/lecturas multirrol quedan fuera de este contrato de datos propios.

## Evidencia requerida
Nueva suite nativa cubre vuelta completa, reparto cambiado, reintento antiguo, recibos versionados, aislamiento A/B, downgrade v1, campos inválidos, rollback e historial.
Sintaxis JavaScript y SQL/PLpgSQL comprobada localmente; revisión estática independiente realizada. Ejecución nativa del nuevo SHA pendiente.
No habilitar worker v2 hasta: CI nativo verde, hashes Kotlin/PostgreSQL con arrays/null, recibos Android versionados, bootstrap de dependencias, sesión/consentimiento revalidados al enviar y pruebas de caída/reinicio/restore entre instalaciones.
