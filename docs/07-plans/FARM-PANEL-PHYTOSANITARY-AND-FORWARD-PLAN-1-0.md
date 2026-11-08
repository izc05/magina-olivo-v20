# Panel de finca, fitosanitarios y plan posterior 1.0

Estado: **auditoría y propuesta.** El panel y los formularios usan solo Room 36. Ningún cambio de esquema ni SQL.

## 1. Qué se muestra ya en la finca (Room 36)

| Sección | Datos | Fuente |
|---|---|---|
| Tratamientos | Último, fecha, producto, parcelas, superficie, cantidad, número en la campaña, registros incompletos | `ActivityRepository` |
| Trabajos | Tres últimos, realizados y previstos | `ActivityRepository` |
| Recolección | Campaña activa, jornadas, kilos cosechados, pesadas, kilos entregados, destinos, último rendimiento | `HarvestRepository`, `DeliveryRepository`, `CampaignRepository` |
| Gastos | Gastos confirmados de la campaña por moneda, jornales, maquinaria y productos; borradores aparte | `ExpenseRepository` |
| Documentos | Número de documentos de la finca | `AttachmentRepository` |

Ingresos y margen muestran «Sin datos suficientes» hasta que P5 esté integrada. Titularidad y Personas no aparecen hasta tener datos reales.

Reglas aplicadas: un tratamiento con varias parcelas cuenta una vez; sin superficie en alguna parcela no se da suma parcial; lo previsto no cuenta como realizado; un total desbordado se muestra como no disponible.

**Limitación:** kilos cosechados y entregados son cifras separadas y no se reparten entre parcelas.

## 2. Registro fitosanitario

| Requisito | Estado |
|---|---|
| Fecha, parcelas, superficie por parcela | Existe |
| Producto, sustancia activa, cantidad, unidad, dosis, motivo | Existe y editable |
| Nº de registro y observaciones | Columnas existentes; **añadidos al formulario** |
| Plaga codificada, eficacia, aplicador, máquina, empresa | Columnas existentes; **sin formulario** |
| Fuente, versión y contexto de autorización | Columnas existentes; sin formulario ni servicio oficial |
| Carné y asesor | En `PhytosanitaryResources` (personas); falta vincularlos a la actividad |
| Cultivo y variedad del tratamiento | Variedad en parcela; cultivo en Room 38 |
| Varios productos por aplicación | **No existe**: un detalle por actividad |
| Plazo de seguridad | **No existe** |
| Foto o documento | Adjuntos a la actividad |

**Riesgo a revisar:** al editar un tratamiento el detalle se reconstruye desde el formulario. Los campos sin formulario se perderían si alguna vía llega a rellenarlos.

Antes del 01/01/2027 hay que verificar el alcance real del registro digital y su formato; esta app **no sustituye ni está homologada como CUE**.

## 3. Nueva versión necesaria

| Versión | Contenido |
|---|---|
| 37 | P5 |
| 38 | Plantación, paraje, terreno y suelo |
| 39 | Recintos SIGPAC, caché, recintos trabajados y perfil de riego |
| 40 | Titularidad y explotación, sujeta a privacidad |
| **41 (propuesta)** | Biblioteca de productos, productos por tratamiento, categorías y fechas de documentos, plazo de seguridad |

Modelo de la 41: `phytosanitary_products` (nombre, registro MAPA, sustancia, fuente y fecha de verificación); `treatment_products` (actividad, producto, cantidad, dosis, unidad), migrando el detalle actual sin perder datos; adjuntos enlazados al producto. Una foto u OCR nunca certifica autorización.

**Dependencia:** Room es consecutivo, así que 38 a 41 esperan a que P5 (37) esté integrada, o a reordenar.

## 4. SIGPAC en Room 39 (diseño)

`sigpac_recintos`: parcela, código completo, recinto, atributos tal como los publica FEGA, fuente, fecha, campaña si existe. `parcel_worked_recintos`: relación elegida por el agricultor. Marcar un recinto en pantalla no crea esta relación.

## 5. Titularidad (Room 40)

Amplía la propuesta básica: propietarios, aparcería, vigencia, identificador y contacto, todos opcionales. Requiere privacidad, exportación y borrado antes de habilitarla.

## 6. Roles y permisos

| Capacidad | Titular | Gestor | Recolectora | Jornalero | Lectura |
|---|---|---|---|---|---|
| Registrar actividades | Sí | Sí | No | Asignadas | No |
| Registrar pesadas | Sí | Sí | Asignadas | No | No |
| Consultar documentos | Sí | Autorizados | No | No | Autorizados |
| Consultar gastos | Sí | Autorizados | No | No | No |
| Gestionar colaboradores | Sí | No | No | No | No |

Alcance por finca, parcela y operación, con vigencia, aceptación expresa y revocación. Invitación por correo o QR de un solo uso. Mágina IA usa las mismas capacidades. No se activa sin RLS, RPC, aislamiento A/B/C y revocación validados con Codex.

## 7. Informe de explotación

Base existente: `CampaignReport`, `SeasonReport` y `ReportPdf`. Propuesta: informe por finca y campaña con PDF y formato estructurado, indicando que no sustituye al CUE. Pendiente de implementar.
