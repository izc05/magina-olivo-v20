# V20 Empresas · Experiencias y reservas

## Objetivo
Convertir el directorio de Empresas en una superficie de descubrimiento y captación para experiencias reales de Sierra Mágina: catas de AOVE, visitas a almazaras, gastronomía, naturaleza, cultura, talleres, bienestar y actividades familiares.

## Superficies
- Público: `/experiencias`.
- Plataforma: `/admin/empresas/experiencias`.
- Empresa verificada: `/mi-negocio/experiencias`.

## Modelo
`business_experiences` define el producto/actividad. `business_experience_slots` define sesiones con aforo. `business_experience_bookings` guarda la solicitud y su estado. Cada solicitud crea además un `business_lead` de tipo `booking` y un evento first-party `experience_booking_submit`.

## Capacidad
Una solicitud no bloquea plazas hasta ser confirmada. La confirmación se ejecuta en transacción, bloquea la reserva y la sesión, verifica `confirmed_count + party_size <= capacity` y solo entonces consume aforo. Si una reserva confirmada se cancela/declina, libera aforo. Esto evita sobrevender una sesión aunque existan varias solicitudes pendientes simultáneamente.

## Roles
- Plataforma: puede administrar todas las experiencias y reservas.
- `owner/manager`: crean experiencias/sesiones y deciden reservas.
- `editor`: crea contenido y sesiones, no decide reservas.
- `analyst`: solo consulta.

La empresa nunca puede autoasignarse plan comercial, patrocinio, prioridad ni verificación.

## Estados de reserva
`pending -> confirmed -> completed` es el flujo positivo. También existen `declined`, `cancelled` y `no_show`. Confirmar cualifica el lead; completar lo marca ganado; declinar/cancelar/no-show lo marca perdido.

## Fuera de alcance deliberado
- cobro online y settlement;
- comisión automática;
- integración directa con Rutas/Eventos mientras sus ramas sigan independientes;
- inventar disponibilidad cuando no existen sesiones publicadas.

## Próxima integración
Rutas, pueblos y eventos podrán enlazar experiencias mediante el directorio público y pasar `sourceContext/sourceKey` para atribución. El modelo ya acepta esa procedencia sin acoplar los módulos.
