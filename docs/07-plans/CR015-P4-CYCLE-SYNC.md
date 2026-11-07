# CR-015 P4 — Sincronización de ciclos agrícolas

Base: P3 integrada en #859. Aplica las reglas aprobadas en P2 §4: CAS por finca, recibos idempotentes,
dependencias padre-hijo, conflicto visible y sin reasignar históricos.

## Alcance v1

- Se sincronizan las cabeceras de ciclo (rótulo, inicio, cierre efectivo, `closedAt`, «sin recolección»)
  y los vínculos ciclo–recolección. Jornadas y pesadas siguen a su recolección.
- Los vínculos de trabajos y gastos siguen locales: esos registros no se sincronizan todavía en G2.6.
- Solo el propietario del workspace seleccionado (mismo criterio que la recepción G2.6) y solo fincas
  vinculadas a la cuenta.
- Entrada: «Sincronizar ahora» de la APK DEV, después de campañas (envío) y de jornadas (recepción).

## Contrato (propuesta, sin desplegar)

`supabase/proposals/cr015-p4-cycle-sync-v1.sql`:

- `put_own_farm_cycles_v1(operation, farm, expected_revision, state)`: estado completo de la finca con la
  revisión esperada. Recibo en `sync_operations` (`FARM_CYCLES`); repetir la misma operación devuelve el
  mismo resultado.
- `get_own_farm_cycles_v1(farm)`: cabecera actual y revisión.
- El servidor no admite reabrir ni cambiar un ciclo cerrado, borrar ciclos o vínculos, sacar una
  recolección de un ciclo cerrado ni cerrar con recolecciones abiertas. Exige que finca y recolecciones
  existan antes (`PARENT_NOT_SYNCED`).
- Tablas sin acceso directo; solo las dos RPC, con comprobación de JWT y propietario.

## Android

- Room 35→36: tabla vacía `agricultural_cycle_sync_state` (sin tocar ciclos, vínculos ni operaciones).
- El cuerpo exacto se congela antes de enviar y se reenvía sin cambios tras una respuesta perdida.
- Revisión obsoleta o historia divergente: estado `CONFLICT` visible en el panel; no se sobrescribe ni el
  teléfono ni el servidor. La recepción queda registrada en `agricultural_cycle_operations`.
- El panel muestra: guardada, sincronizada, pendiente, esperando datos previos, conflicto o sin permiso.

## Pruebas

- SQL en PostgreSQL embebido (PGlite): `supabase/tests/cr015_p4_cycle_sync.test.mjs` (5).
- Room con dos teléfonos y servidor simulado: `AgriculturalCycleSyncRoomTest` (6); migración 35→36.
- JVM: contenido exacto del envío, parser estricto y textos de estado.

## Pendiente y bloqueos

- Desplegar el SQL en Supabase DEV requiere autorización expresa; sin él no hay prueba real A↔B.
- Resolver un conflicto (elegir versión del servidor o del teléfono) requiere decisión de producto: hoy
  queda retenido y visible, sin pérdida.
- Sin sincronización de ciclos en staging/producción: la entrada manual solo existe en DEV, como el resto
  de G2.6.
