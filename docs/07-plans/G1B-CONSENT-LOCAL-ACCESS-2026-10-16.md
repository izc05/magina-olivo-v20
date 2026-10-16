# G1b — selección, consentimiento y protección local

Encargo propietario 16/10, continuación de #755. Rama separada con base G1a;
PR apilada hasta que el propietario autorice integrar la dependencia.

## Decisiones

No cambiar owner, UUID ni relaciones Room. La selección identifica fincas locales
con nombre/versión y pide una casilla explícita antes de preparar su asociación.
Guardar consentimiento, cuenta (issuer + UUID, nunca email como identidad),
selección exacta, fecha, versión del texto y copia/verificación G1a en un ledger
privado AtomicFile fuera del logout y Android Backup. Estado único inicial:
`PENDING_REMOTE_RESERVATION`; no mostrar «sincronizada» o «vinculada en nube».

El ledger es metadata de dispositivo, no un segundo modelo agrícola: Room v26
conserva las entidades y su verdad operativa. La copia se ejecuta bajo una
transacción de lectura Room que permanece abierta hasta guardar consentimiento;
Auth mantiene su mutex durante la operación para impedir cambios de identidad.
Reintento sin acuse: las fincas ya registradas no se duplican. Fallo antes del
commit atómico deja la versión anterior; copia huérfana no concede vinculación.

Hay personas, maquinaria y documentos compartidos por workspace. Hasta disponer
de partición validada por agregado, **la protección de acceso abarca el conjunto
local**, aunque solo las fincas elegidas quedan preparadas para la reserva.
Explicarlo antes del consentimiento. Otra cuenta o logout accede a Cuenta para
volver a identificarse, pero no a los registros agrícolas de ese conjunto.
No reasignar automáticamente fincas no elegidas, no borrar y no transferir datos.
Esta fase no permite repartir ese conjunto entre dos cuentas del mismo móvil.
La corrupción del ledger bloquea acceso; nunca se interpreta como «sin vincular».

## Plan de implementación y aceptación

- [ ] Ledger AtomicFile validado, merge inmutable por identidad y finca; tests de
  reinicio, corrupción, escritura fallida, selección vacía y dos cuentas.
- [ ] Repositorio G1b, inventario mínimo, comparación de selección y consentimiento,
  copia real G1a previa, sin escritura agrícola/outbox/red; pruebas offline/retry.
- [ ] Panel en Cuenta con componentes actuales, lista/casilla/resumen/confirmación,
  errores claros y estado pendiente honesto. Sin nuevas pestañas ni formularios agrícolas.
- [ ] Proteger AppRoot, descartar navegación/ViewModels anteriores al cambiar de
  identidad y evitar notificaciones agrícolas al estar bloqueado. Restaurar sesión
  cacheada sin red antes de decidir acceso; no filtrar por email ni recovery token.
- [ ] Android UI y Room sintéticos: A selecciona dos de tres fincas; B y logout
  no acceden; A vuelve offline tras reinicio; cancelar y errores no alteran datos.
- [ ] Preparar G1c (contrato RPC, idempotencia/validación y recuperación), sin activar
  servidor ni G2. Revisión independiente, evidencia, PR y #744/#727.

G1 seguirá incompleta hasta G1c y pruebas E2E del acuse. No producción, despliegue
persistente, merge ni APK candidata.
