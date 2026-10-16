# G1c preparado — reserva remota y recuperación

Dependencias: #755 y PR G1b. Este documento no crea RPC, tablas ni migraciones,
ni acredita vínculo remoto o activa G2. G1b solo guarda consentimiento local
`PENDING_REMOTE_RESERVATION`; el bloqueo de acceso local no es un grant servidor.

## Solicitud y autoridad

Por cada finca elegida, operation_id estable derivado de consentimiento+farm UUID
o persistido antes del primer envío. Payload versionado: operation_id, farm UUID
local, workspace local (referencia, nunca autoridad), consent UUID/textVersion,
manifiesto SHA-256 de copia y versión del agregado local. JWT fresco de issuer
configurado; el servidor obtiene actor de auth.uid(), nunca del cuerpo/email.
La copia incluye datos de otras fincas por seguridad local, pero el payload y
futura sincronización solo pueden incluir agregados seleccionados y dependencias
autorizadas. Nunca subir el directorio completo de copia ni sesiones/ledger.

## Reserva y conflictos

- Transacción servidor: recibo inmutable unique(actor,operation_id), solicitud
  hash canónico, reserva única de farm UUID. Repetir exactamente la solicitud
  devuelve el mismo recibo; mismo ID y otro payload se rechaza.
- Farm UUID cloud ya existente: comprobar permiso efectivo y workspace del actor.
  Legacy sin workspace o datos de otra identidad no se reclaman: conflicto genérico
  sin nombres/parcelas/metadata ajena. Nombre, Catastro y correo nunca prueban propiedad.
- Dos cuentas reservan mismo UUID: solo una reserva; la otra no sobrescribe ni
  transfiere. Evitar vías de inserción directa que eludan las reservas.
- No confundir reserva con importación ni con titularidad legal. No modificar
  grants/owner históricos de una finca existente como efecto del primer login.

## Acuse local y recuperación

Persistir acuse y mapa local→Auth→workspace remoto con transacción idempotente y
estado separado `RESERVED_REMOTE`, sin cambiar IDs ni datos agrícolas originales.
G1b usa AtomicFile privado para su ledger; G1c debe versionarlo y mantener los
consentimientos anteriores verificables. Una respuesta tardía se aplica solo a
su actor y operación, aunque la cuenta visible haya cambiado; no abre acceso ni
activa red de otra cuenta. Si la identidad cambió, conservar recibo privado para
reanudar con la cuenta correcta. Ante timeout tras commit consultar/repetir la
misma operación; nunca inventar un nuevo UUID o borrar los datos locales.

## Matriz del siguiente bloque

1. Supabase exclusivamente efímero clean/legacy: dos actores, JWT caducado,
   replay idéntico/alterado, operación ajena ilegible, UUID ajeno/legacy ocupado,
   carreras y rutas directas. Sin despliegue persistente.
2. Android: matar proceso antes/después de petición y antes/después de persistir
   acuse, errores de disco, logout/cambio cuenta durante respuesta, reapertura
   offline. Exactamente un vínculo, consentimiento y manifiesto conservados.
3. Reconciliar conjuntos de IDs, cantidades, históricos y bytes antes/después;
   no solo conteos o URI. El inventario debe incluir recursos compartidos del
   workspace sin exponer fincas no elegidas.
4. Solo después declarar G1 validada y proponer G2. El ledger local no habilita
   push/pull, scheduler, web agrícola ni Storage de adjuntos por sí mismo.

Pendiente adicional explícito: restauración soportada debe recuperar tanto Room
y binarios como el ledger de protección. No ofrecer restaurar un snapshot crudo
saltándose el binding. Limpieza de staging/orphans y partición local entre dos
cuentas requieren contrato y pruebas propios antes de permitir transferencias.
