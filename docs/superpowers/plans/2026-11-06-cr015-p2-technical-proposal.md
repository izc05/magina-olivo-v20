# CR-015 — Propuesta técnica P2: ciclos agrícolas por finca

**Fecha:** 06/11/2026. **Estado:** propuesta para revisión; NO autoriza implementación.

**Objetivo:** definir ciclos por finca con cierres explícitos, conservando los registros canónicos, el histórico y el aislamiento entre cuentas.

**Arquitectura:** entidad de ciclo independiente de la recolección, vínculos explícitos a los registros actuales y operaciones transaccionales con revisión por finca. Las pantallas consultan proyecciones de esos registros, sin copiarlos a otra contabilidad.

**Tecnologías:** Kotlin, LocalDate, Room y contratos actuales de autorización/sincronización. Ninguna nueva dependencia propuesta.

**Fuentes:** [CR-015](https://github.com/izc05/magina-olivo-v20/blob/docs/campana-agricola-ciclo-finca-20261105/docs/00-master/CR-015-CAMPANA-AGRICOLA-CICLO-POR-FINCA.md), [#855](https://github.com/izc05/magina-olivo-v20/issues/855), plan P0 [#856](https://github.com/izc05/magina-olivo-v20/pull/856), decisiones posteriores del propietario y código P1 `cddf94ecf9e4231b2f8497d3ccd9412f3290b2bd`.

**Pendiente documental concreto:** el mensaje anuncia `MAGINA_OLIVO_AUDITORIA_ANTIGRAVITY_P2_Y_ECONOMIA.md`, pero el archivo y sus siete precisiones no están disponibles entre los adjuntos ni en las ubicaciones locales comprobadas. Esta propuesta no afirma haberlas contrastado. Antes de llamarla definitiva hay que leer ese apartado y resolver cualquier discrepancia con las decisiones aprobadas. No hace falta repetir la auditoría de código.

## 1. Límites y situación actual

- P1 mantiene la agrupación provisional septiembre–agosto. No hay ciclos flexibles implementados ni balance completo.
- `Campaign` sigue siendo **recolección**; no se cambia su identidad, tabla, UUID ni semántica para convertirla en ciclo agrícola.
- Room está en versión 34. P2 entrega diseño, no entidades, esquemas, migraciones ejecutables ni cambios de escritores.
- P3 requerirá autorización de implementación local; P4 requerirá autorización independiente de contratos y despliegues remotos. P5, ingresos, liquidaciones y ampliación de jornales generales con personas/horas/pagos, queda fuera.
- La sincronización manual actual es parcial: los flujos documentados cubren workspace, finca, parcela, recolección, jornada y pesada, incluyendo su borrado. No certifican recuperación completa de actividades, gastos generales, documentos ni fotos. No se ha consultado ni modificado el servidor para esta propuesta.

## 2. Modelo propuesto

| Pieza | Responsabilidad |
|---|---|
| `FarmAgriculturalCycle` | UUID, workspace, finca, rótulo, fecha inicial, cierre efectivo opcional, estado OPEN/CLOSED, declaración de cierre y metadatos de revisión/auditoría |
| Revisión de ciclos por finca | Serializar creación, cierre, reapertura y cambios de límites; impide dos ciclos abiertos distintos aunque cada uno tenga una versión válida |
| Vínculo ciclo–recolección | 0..N recolecciones por ciclo; cada recolección pertenece como máximo a uno, con finca/workspace coincidentes |
| Vínculos de actividades y gastos generales | Asignación explícita, con origen y motivo de corrección; conservan los IDs y datos canónicos |
| Operación de ciclo | UUID idempotente, revisión esperada, actor, fecha de registro y efecto confirmado; permite explicar y recuperar una corrección |

Se proponen tablas laterales aditivas para evitar alterar los DTO y hashes actuales de G2.6. Nombres y versión Room definitiva se fijarán al autorizar P3 contra el HEAD de ese momento, sin reservar ahora la versión 35.

La pertenencia de pesadas/jornadas sigue su recolección. El gasto vinculado a actividad o recolección conserva ese origen; no se añade una segunda atribución incompatible. Si dos vínculos discrepan, se exige revisión, en lugar de imponer una prioridad silenciosa. Parcelas compartidas no determinan por sí solas la finca propietaria del trabajo. Los gastos sin finca siguen visibles como «Sin asignar».

## 3. Fechas y cierre

1. Fechas civiles `LocalDate` según el contexto temporal del workspace; cierre inclusivo C y siguiente inicio a partir de C + 1 día, editable antes de confirmar. Sin solapamientos y como máximo un ciclo abierto por finca. Se admiten huecos declarados.
2. El rótulo «2026/27» es descriptivo; no identifica ni calcula el intervalo. Cambiarlo no reasigna registros.
3. Cerrar requiere confirmar el final de toda la recolección de esa finca y revisar la fecha efectiva. También se admite declaración expresa «Sin recolección», sin fabricar pesadas de cero ni ventas.
4. La previsualización muestra actividades y registros físicos que quedarían fuera del intervalo. Se bloquea el cierre hasta corregir individualmente las incompatibilidades; no hay reclasificación masiva.
5. Cerrar el ciclo no cierra silenciosamente las recolecciones actuales. Las recolecciones vinculadas deben estar finalizadas conforme a sus reglas existentes, o resolverse expresamente antes del cierre agrícola.
6. Los trabajos ordinarios posteriores pertenecen al siguiente ciclo. Si no existe, se solicita iniciar/asignar conservando el borrador. Una consulta de histórico nunca cambia automáticamente el destino de escritura.
7. Un trabajo histórico de un ciclo cerrado exige corrección explícita, motivo y autorización. Un trabajo con intervalo que cruza límites no se divide ni duplica automáticamente: queda pendiente de revisión.
8. Propuesta para reapertura: permitirla solo si no genera otro abierto ni invade ciclos vecinos. La rectificación de un ciclo anterior se puede auditar sin reabrirlo. Toda modificación de límites vuelve a revisar los registros afectados.
9. El cierre agrícola no es cierre económico. Rendimientos y ajustes tardíos conservan origen y fechas reales. Las futuras liquidaciones podrán referenciar el ciclo cerrado, pero P2 no incorpora su modelo ni elude restricciones de los escritores actuales usando vínculos nulos ficticios.

Ejemplo de aceptación: finca A termina el 25/01/2027 y B el 12/02/2027. La poda de A del 10/02/2027 corresponde al siguiente ciclo confirmado; B puede continuar en el anterior. La poda del 15/02/2026 y la recogida diciembre 2026–enero 2027 pueden pertenecer al mismo ciclo A 2026/27 por asignación confirmada, sin depender del mes ni del rótulo.

## 4. Operaciones, autorización y recuperación

La consulta y la previsualización son puras: no crean workspace, ciclo ni vínculos. La confirmación revalida sesión, workspace seleccionado, autorización, finca, revisiones y conjunto de registros afectados dentro de una transacción Room. El resultado es todo o nada. Doble pulsación o repetición del mismo UUID de operación no crea un segundo ciclo ni otro efecto.

El comando de confirmación incluirá identidad de operación, finca/workspace, revisión agregada esperada y propuesta de fechas/vínculos. La previsualización se invalida si cambia la cuenta, la autorización, un ciclo vecino o un registro relevante; el usuario debe revisarla de nuevo. El historial de correcciones conserva actor, motivo y fechas anteriores/nuevas, sin copiar datos personales innecesarios.

P3 podrá funcionar offline localmente, pero no prometerá coherencia de cierres entre dispositivos. No enviará tipos desconocidos por el outbox G2.6. La operación local quedará trazada para un transporte futuro expresamente autorizado.

P4 necesitará revisión CAS **por finca**, además de versiones por ciclo, recibos idempotentes, dependencias padre-hijo y autorización remota. Dos dispositivos offline que proponen cierres incompatibles producirán conflicto visible; no prevalecerá silenciosamente la última escritura. Repetir un envío tras perder el ACK debe devolver el mismo resultado. Clientes antiguos que escriban sin ciclo dejarán registros pendientes de atribución; no se puede garantizar bloqueo remoto de ciclos cerrados hasta definir esa compatibilidad y desplegarla con autorización.

## 5. Migración e históricos

La futura migración debe crear estructuras vacías e índices, sin modificar registros canónicos, importes, fechas, UUID, versiones, borrados, recibos o contenido del outbox. Las sugerencias históricas se calculan para una previsualización por finca; solo una confirmación explícita persiste ciclos y asociaciones. Las consultas anteriores siguen mostrando los registros sin atribuir.

No sustituir globalmente `OliveSeason`: mantenerlo para la lectura histórica provisional y añadir consultas por vínculos de ciclo. Comparativas entre fincas declaran los intervalos incluidos; fechas distintas no se presentan como un periodo homogéneo.

Costes: sumar una sola vez cada `Expense` canónico POSTED por moneda, sin añadir otra vez el coste de actividad/jornal que lo originó. No convertir monedas automáticamente. Kilos: conservar entregas canónicas y distinguir históricos manuales de agregados de jornada. Rendimiento ponderado con cobertura; desconocido no equivale a cero. Ingresos y balance completo siguen pendientes de P5.

Una migración aditiva de Room no garantiza que una APK antigua abra la nueva base. Antes de P3 se comprobarán copia recuperable y estrategia de corrección hacia delante; queda prohibido recurrir a migración destructiva o downgrade ciego.

## 6. Pruebas previstas antes de autorizar implementación

| Área | Evidencia exigida |
|---|---|
| Fechas por finca | Ejemplo A/B anterior, C y C+1, huecos, solapamientos, año bisiesto y cambios de hora; rótulo editable sin reasignación |
| Cierres | Sin recolección, pendientes, trabajo posterior al cierre propuesto, intervalo de trabajo que cruza ciclos; reapertura contra vecinos |
| Históricos | Cancelar previsualización no escribe; confirmar solo los vínculos seleccionados; corrección individual auditable; gastos sin finca y parcelas compartidas visibles |
| Room | Rutas de migración soportadas desde 1..34 hasta N; comparación exacta de registros y payloads canónicos antes/después; tablas nuevas vacías; fallo transaccional sin cambios parciales |
| Seguridad | Cambio de cuenta/logout entre previsualizar y confirmar, autorización revocada, workspace no seleccionado, acceso a finca ajena; ninguna escritura ni filtración |
| Offline | Crear/cerrar y reiniciar, doble toque, reintento de operación; mismos UUID y cantidades, sin perder borradores ni duplicar registros |
| Agregados | Gastos POSTED/DRAFT, monedas distintas, costes derivados sin doble suma, límites de enteros, entregas e históricos manuales, cobertura de rendimiento |
| Compatibilidad P4 | Dos dispositivos/ciclos distintos sobre la misma revisión de finca, ACK perdido, hash incompatible, mensajes desordenados y cliente sin soporte; conflicto explícito |
| Interfaz | Seis escritores canónicos, + desde cada raíz, histórico consultado frente a escritura actual, teclado, volver atrás y 360/390/430 dp |

Riesgos principales: atribución histórica incorrecta, cierres concurrentes, clientes antiguos, datos aún no sincronizados y downgrade Room. Las medidas anteriores son condiciones de aceptación, no garantías ya implementadas.

## 7. Secuencia propuesta tras revisión

- [ ] Contrastar las siete precisiones del documento real de Antigravity y resolver diferencias con CR-015; aprobar este diseño.
- [ ] Solicitar GO específico de P3 con alcance local: reglas puras y tests; modelo/migración aditiva verificada; comandos transaccionales y vínculos explícitos; integración de escritores y pantallas sin pérdida de históricos. Cada bloque tendrá pruebas rojas/verdes y commit propio.
- [ ] Mantener G2.6 intacto durante P3 y documentar su límite entre dispositivos. P4 requerirá contrato, pruebas de conflictos y autorización separada antes de cualquier SQL remoto.
- [ ] Mantener P5 sin iniciar: ingresos, anticipos, cobros, liquidaciones y jornales generales con personas/horas/pagos necesitan diseño y autorización propios.

**Decisión solicitada:** revisión de la propuesta, no autorización implícita de cambios Room/Supabase. Hasta recibir el documento pendiente y el GO correspondiente, P2 permanece exclusivamente documental.

