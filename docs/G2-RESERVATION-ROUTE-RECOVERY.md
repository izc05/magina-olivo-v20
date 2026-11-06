# G2 — Destino remoto duradero de la reserva

## Defecto corregido

El diario guardaba cuenta, issuer y solicitud antes del envío, pero el workspace remoto solo se conocía al recibir un recibo. Un adaptador reconstruido con otro workspace podía enviar de nuevo una operación cuyo resultado anterior era desconocido. La regresión `journalWithoutRecordedDestinationCannotUseRpcAdapter` reprodujo ese envío antes de la corrección.

## Contrato

- `DurableReservationJournal.prepare(scope, request, remoteWorkspaceId)` guarda la solicitud y el destino en el mismo reemplazo atómico. Preparar otra vez exige solicitud y destino idénticos; ni siquiera permite completar después el destino de una operación antigua.
- `ReservationRpcTransport.remoteWorkspaceId` es inmutable. El estado de recuperación compara su destino con el persistido antes de invocar el adaptador. Una diferencia conserva el intento pendiente y no envía.
- Un recibo de otro workspace produce `INVALID_RECEIPT`; no se convierte en reserva confirmada. Cambiar de cuenta sigue deteniendo el intento.
- El formato 2 del diario incorpora el destino nullable por registro y conserva checksum, límites y escritura atómica. Lee el formato 1 con destino nulo. Ese registro sigue siendo legible, pero no se envía mediante un adaptador RPC ni recibe una ruta por deducción. Un lector antiguo rechaza el formato nuevo; no hay conversión inversa.
- El destino nulo del contrato de transporte se conserva exclusivamente para los dobles locales de pruebas. Una futura implementación RPC debe declarar su destino. No representa una ruta de envío válida para registros heredados.

## Verificación y límites

La comprobación JVM dirigida reproduce primero el fallo y comprueba después la recuperación de ACK perdido, reintento hacia otro destino sin llamadas, lectura del formato 1 sin modificar bytes y cuarentena de recibo incompatible. La prueba que termina por fuerza un proceso JVM comprueba también que el destino sobrevive en los límites de preparación, antes del renombrado y después del commit.

La ejecución local usa compilador Kotlin standalone 2.2.21, JUnit 4.13.2 y serialization 1.8.1, sin modificar dependencias del proyecto. No sustituye foundation/Gradle con Kotlin 2.4.20 ni las pruebas Android del SHA publicado.

No cambia Room ni el cuerpo/hash del RPC G1c. No se ha conectado una fábrica, trabajador o HTTP a la aplicación. La futura consumidora debe pasar siempre por el diario: llamar directamente al adaptador no proporciona durabilidad. Continúan pendientes la reanudación de creación de dependencias agrícolas y la validación de consentimiento al enviar.
