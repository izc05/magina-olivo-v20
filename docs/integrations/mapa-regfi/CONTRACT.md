# MAPA REGFI — contrato de preparación #681

Estado: PREP / research only. Verificado el 2026-10-07. Parent #553; relacionados #534, #536, #677/#680.
Base de investigación: main `69a3e0708b671a286ee65e6e5ef2fd7fa6620a96`.
Este contrato prepara la implementación; no activa catálogo, Android, Room, IUWS ni backend desplegable.

## Fuente oficial y reproducción

- Registro: https://servicio.mapa.gob.es/regfiweb
- Resúmenes: https://servicio.mapa.gob.es/regfiweb/Resumenes/Index
- Descarga: https://servicio.mapa.gob.es/regfiweb/Exportaciones/ExportJsonProductosAutorizados
- Evidencia del transporte: https://servicio.mapa.gob.es/regfiweb/js/site.min.js
- Descripción oficial: botón «Descripción JSON Productos Autorizados» de Resúmenes; el JS observado envía POST a `/regfiweb/Exportaciones/ExportPdf`, formulario `dataDto=ProductosAutorizadosPdf`. La petición del 2026-10-07 devolvió HTTP 500 indicando que el fichero PDF no se encuentra en el servidor MAPA. Se documenta el contrato de la descarga viva; no se atribuyen al PDF campos que no se pudieron leer.

La inspección puntual de Resúmenes y del JS sirve para identificar la descarga oficial. El futuro adaptador consume exclusivamente el export JSON; no hace scraping de resultados HTML ni utiliza la consulta por producto como fuente del catálogo.

Petición comprobada sin credenciales ni cookies:

```powershell
$response = Invoke-WebRequest -Uri 'https://servicio.mapa.gob.es/regfiweb/Exportaciones/ExportJsonProductosAutorizados' -Method Post -ContentType 'application/x-www-form-urlencoded' -Body @{tipoExportacion='ProductosAutorizados'; dataDto='{}'}
$envelope = $response.Content | ConvertFrom-Json
if ($envelope -is [string]) { $envelope = $envelope | ConvertFrom-Json }
$catalog = $envelope.Contenido | ConvertFrom-Json
```

Respuesta observada: HTTP 200, `application/json; charset=utf-8`. El primer JSON es una **cadena JSON serializada**; se decodifica una vez más para obtener `{Id, Tipo, Contenido, Fecha}`. `Contenido` es otra cadena JSON cuyo objeto tiene `Productos`. Admitir también un envoltorio objeto, con validación idéntica. Máximo dos decodificaciones para obtener el envoltorio y una para Contenido; nunca un bucle de parseo sin límite. Un GET a la descarga devolvió página de error: no usar GET como fallback.

## Evidencia de la muestra viva

La captura local temporal no se incorpora a Git (aproximadamente 86 MB serializados). Datos medidos:

| Propiedad | Observación |
| --- | --- |
| `Tipo` | `CEX` |
| `Fecha` | `2026-10-02T00:00:00` (sin zona horaria) |
| Bytes UTF-8 de `Contenido` | 50.052.871 |
| SHA-256 de esos bytes exactos | `e173abae54e05973165719435cd497831ef13f229d1562eb73a1d46fff4cee1e` |
| Filas | 2.082 |
| Estados | 1.994 `Vigente`, 88 `Cancelado` |
| Registros distintos | 2.081 |
| Duplicado observado | `16192`: IdProducto 114135 y 113357, límites de venta distintos |

«Productos Autorizados» es el nombre del export, **no una garantía de que todas las filas estén vigentes**. La fecha de una página rastreada o la versión de la aplicación web no reemplaza `Fecha` del export. El registro anuncia actualización semanal; no se ha comprobado un día/hora fijo.

## Contrato de entrada observado

`Contenido.Productos[]` contiene `DATOSPRODUCTO`, `COMPOSICION`, `USOS` y opcionalmente `OTRASDENOMINACIONES`, `OTROSNOMBRES`. Los campos se acceden con sus nombres exactos, incluidos espacios y mayúsculas.

| Entrada | Salida propuesta | Regla |
| --- | --- | --- |
| `DATOSPRODUCTO.Num_Registro` | `registrationNumber` | Cadena no vacía, trim exterior; conservar ceros y guiones, nunca Number |
| `DATOSPRODUCTO.IdProducto` | `variants[].providerProductId` | Identidad de variante; no sustituye nº registro |
| `DATOSPRODUCTO.Nombre` | `commercialName` | Nombre oficial, Unicode NFC; conservar texto original en raw |
| `DATOSPRODUCTO.Estado` | `administrativeStatus` y `administrativeStatusRaw` | Vigente→CURRENT; Cancelado→CANCELLED; otro/no informado→UNKNOWN |
| `DATOSPRODUCTO.Formulado` | `formulation` | Texto, sin extraer sustancias con regex |
| `DATOSPRODUCTO.Fecha_Registro` | `registeredDateRaw` | Literal de origen; sin inventar zona |
| `DATOSPRODUCTO.Fecha_Caducidad` | `expiryDate` | `YYYY/MM/DD` válido→`YYYY-MM-DD`; vacío→null |
| `DATOSPRODUCTO.Fecha_Cancelacion` | `cancellationDate` | Misma regla |
| `DATOSPRODUCTO.Fecha_LimiteVenta` | `saleDeadlineDate` | No inferir plazo de uso desde venta |
| `DATOSPRODUCTO.Condicionamiento` | `conditions` | Texto completo |
| `COMPOSICION[].Nombre Sustancia` | `activeSubstances[].name` | No separar artificialmente una sustancia compuesta |
| `COMPOSICION[].NombreUE` | `activeSubstances[].euName` | Vacío→null |
| `COMPOSICION[].Concentracion` | `activeSubstances[].concentration` | Número finito ≥0 o null si ausente; conservar unidad separada |
| `COMPOSICION[].DescripcionNota` | `activeSubstances[].concentrationUnitRaw` | Por ejemplo `% (EXPR. EN CU)` no equivale a `%` genérico |
| `OTRASDENOMINACIONES[].Nombre_Origen`, `OTROSNOMBRES[].Nombre` | `aliases[]` | Buscar también por alias; deduplicar NFC/trim sin cambiar raw |
| `USOS[].CodigoCultivo`, `Cultivo` | `uses[].crop.{code,name}` | Códigos como cadenas, conservar jerarquía; no inferir EPPO |
| `USOS[].CodigoAgente`, `Agente` | `uses[].pest.{code,name}` | No convertir a códigos IUWS sin crosswalk oficial |
| `USOS[].Dosis_Min`, `Dosis_Max`, `Unidad Medida dosis` | `uses[].dose.{min,max,unitRaw}` | Números finitos ≥0; min≤max; no convertir unidades |
| `USOS[].Plazo Seguridad` | `uses[].safetyIntervalRaw` | `NP`, `NO PROCEDE`, rangos y texto no son 0 días |
| `USOS[].Volumen Caldo`, `Aplicaciones`, `IntervaloAplicaciones`, `Bbch` | Campos `*Raw` de uso | Conservar texto/rangos; sin elegir un número arbitrario |
| `USOS[].CondicionamientoEspecifico` | `uses[].conditions` | Mantener relación cultivo/plaga/dosis/condiciones en una misma fila |
| `USOS[].TipoUsuario`, `Ambito`, `SistemaCultivo`, `MetodoAplicacion` | Campos homónimos camelCase de uso | Vacío→null, no inferir desde otro campo |
| `USOS[].Volumen_Min`, `VolumenMax`, `Unidades Volumen` | `uses[].volume.{minRaw,maxRaw,unitRaw}` | Un 0 publicado se conserva; no interpretarlo como volumen permitido 0 |

Las listas opcionales ausentes equivalen a [] solo cuando el campo no existe. Una lista con tipo incorrecto invalida el candidato. `USOS` conserva todas las filas completas: no crear el producto cartesiano de cultivos y plagas. Cualquier fecha no vacía inválida, número no finito, identidad vacía o dosis invertida invalida el candidato completo. Campos desconocidos se conservan en raw; cambios de tipo de campos conocidos provocan rechazo. Concentración/dosis nula significa desconocida, no cero.

## Salida propuesta v1

```typescript
type Status = "CURRENT" | "CANCELLED" | "UNKNOWN" | "CONFLICT";
interface Catalog {
  schemaVersion: 1;
  source: { id: "MAPA_REGFI"; url: string; exportType: "CEX" };
  sourceVersion: string; // Fecha literal + ':sha256:' + SHA-256 de Contenido
  fetchedAt: string; // instante UTC de la descarga aceptada
  checksum: string; // SHA-256 hexadecimal de los bytes UTF-8 exactos de Contenido
  sourcePublishedAtRaw: string; // Fecha sin asumir que es un instante UTC
  products: Product[]; // una entrada por registrationNumber
}
interface Product {
  registrationNumber: string;
  commercialName: string | null; // null si las variantes discrepan
  activeSubstances: Substance[] | null; // null si discrepan
  administrativeStatus: Status;
  resolution: "UNAMBIGUOUS" | "REVIEW_REQUIRED";
  variants: Variant[]; // TODAS las variantes distintas del mismo registro
}
interface Substance {
  name: string; euName: string | null;
  concentration: number | null; concentrationUnitRaw: string | null;
}
interface Variant {
  providerProductId: string;
  commercialName: string;
  administrativeStatus: Exclude<Status, "CONFLICT">;
  administrativeStatusRaw: string | null;
  activeSubstances: Substance[];
  aliases: string[];
  formulation: string | null; registeredDateRaw: string | null;
  expiryDate: string | null; cancellationDate: string | null;
  saleDeadlineDate: string | null; conditions: string | null;
  uses: Use[];
}
interface Use {
  crop: { code: string | null; name: string | null };
  pest: { code: string | null; name: string | null };
  dose: { min: number | null; max: number | null; unitRaw: string | null };
  safetyIntervalRaw: string | null; sprayVolumeRaw: string | null;
  applicationsRaw: string | null; applicationIntervalRaw: string | null;
  bbchRaw: string | null; conditions: string | null;
  userType: string | null; scope: string | null;
  cultivationSystem: string | null; applicationMethod: string | null;
  volume: { minRaw: number | null; maxRaw: number | null; unitRaw: string | null };
}
```

## Deduplicación y búsqueda

Agrupar por nº registro exacto tras trim, nunca por nombre ni IdProducto. Ordenar productos por comparación lexicográfica de código (sin locale dependiente), variantes por IdProducto. Colapsar solo filas idénticas tras normalización completa, incluyendo usos y condiciones; el orden de USOS no cambia su significado pero no elimina condiciones distintas. Una variante por IdProducto; si el mismo IdProducto tiene contenido contradictorio, rechazar el candidato (`conflicting_provider_id`).

Con múltiples variantes diferentes: `resolution=REVIEW_REQUIRED`, `administrativeStatus=CONFLICT`, sin variante seleccionada implícitamente. Proyectar nombre/sustancias solo si son iguales en todas; conservar fechas y usos dentro de variantes. El registro real 16192 tiene límites de venta 2026-09-03 y 2026-10-03: elegir mayor IdProducto o fecha más reciente perdería información sin fundamento. Una entrada con conflicto puede publicarse para consulta con todas las variantes, pero no autoriza autocompletar un contexto de autorización sin revisión.

Índice de búsqueda separado, no normativo: NFD, quitar diacríticos, lower-case y colapsar espacios, aplicado a registro, nombres, aliases y nombres de sustancias de todas las variantes. «cobre» encuentra «OXICLORURO DE COBRE» aunque el nombre comercial no incluya cobre. Un filtro de olivar solo usa las filas de uso correspondientes; no presume que toda la gama del producto sirve para olivo. La búsqueda por cultivo no interpreta por sí sola autorizaciones heredadas de categorías superiores.

## Versiones, publicación y última versión válida

1. Descargar y guardar raw en staging; verificar status, tipos y estructura antes de normalizar. Preservar transporte y Contenido con checksum separado si se almacena el transporte.
2. `sourceVersion = Fecha + ':sha256:' + checksum`. Fecha literal no se sustituye por fetchedAt. Distintos checksums con igual Fecha son versiones distintas. El mismo checksum es una actualización idempotente; no se reescribe fetchedAt original, sí lastCheckedAt operativo.
3. Validar todo el candidato antes de publicación; prohibido publicar parcialmente o convertir errores en `products=[]`.
4. Guardar raw + catálogo normalizado como objetos inmutables por versión; publicar cambiando **atómicamente** un puntero al catálogo validado. Lectores ven el anterior o el nuevo, nunca mitad de ambos.
5. Control optimista compare-and-swap del puntero; una ingestión retrasada no pisa una nueva. Rechazar Fecha anterior a la versión actual; una restauración requiere acción explícita y trazable. Igual Fecha/diferente checksum exige serializar el refresh, nunca decidir orden por hash.
6. Una fila desaparecida en una versión no demuestra cancelación. No fabricar estado; queda ausente del catálogo actual y presente en versiones/snapshots históricos.
7. Timeout, 429/5xx, HTML con 200, JSON truncado, schema drift, payload vacío, checksum inválido o escritura fallida mantienen el puntero anterior. Si no existe versión válida: 503 `catalog_unavailable`, jamás catálogo vacío como éxito.
8. `fetchedAt` no se actualiza al servir fallback. Respuesta de lectura separa `{ catalog, freshness: { state: FRESH|STALE, lastCheckedAt, lastAttemptAt, lastError } }`; no mutar catálogo inmutable para simular actualidad.

Política propuesta, no obligación del proveedor: comprobar una vez al día con una sola ingestión activa; contenido sin cambios no crea versión. Reintentos transitorios 1/5/30 minutos, jitter y respetar Retry-After, con máximo tres intentos por ejecución; schema drift no se reintenta en bucle. Marcar STALE tras 8 días desde fetchedAt o inmediatamente si falla el último refresh; mostrar fecha fuente y descarga. Catalog stale puede consultarse offline, no acredita autorización vigente. Guardar todos los raw/catalogs publicados mientras haya snapshots que referencien su sourceVersion; no borrar automáticamente históricos.

## Tamaño y ejecución futura

La muestra observada tiene 50 MB interiores y aproximadamente 86 MB en transporte guardado. Triple parseo y normalización simultánea pueden multiplicar la memoria. Antes de producción medir pico RSS, tiempo, tamaños comprimidos y catálogo normalizado en el runtime seleccionado. Límites iniciales propuestos: 120 MiB transporte descomprimido, 80 MiB Contenido, 60 s de descarga, 10.000 filas. Conteo cero o caída superior al 20% frente al último válido entra en cuarentena para revisión, no publicación automática. Los límites son protección del proyecto, no contrato MAPA; ajuste documentado si el dataset crece.

La Edge Function de lectura servirá catálogo ya preparado; no descargará/parseará MAPA por cada petición Android. La ingestión debe ejecutarse en un worker con recursos medidos o demostrar que cabe con margen en Edge. Si no cabe, el worker produce artefactos inmutables y la Edge solo sirve la versión actual. Hasta confirmar esta medición no se afirma viabilidad de ingestión completa en Edge.

## Históricos y límites del catálogo

El futuro tratamiento conserva nº registro, nombre, sustancias, sourceVersion, fetchedAt, checksum y el contexto de uso/variante elegido como snapshot local. No FK rígida a catálogo vivo. Actualizaciones no editan Activities previas, ni reinterpretan dosis, ni resuelven automáticamente el mismo producto actual al editar un tratamiento. Estado MAPA no equivale a aceptación CUE ni a autorización legal para un tratamiento concreto. Sin cálculo de cumplimiento, IUWS, certificados o asesoramiento agronómico en #681.

## Entrega y comprobación

- Fixtures: [fixtures/README.md](fixtures/README.md), todas sintéticas.
- QA y limitaciones de lo ya comprobado: [QA.md](QA.md).
- Implementación futura: [plan](../../superpowers/plans/2026-10-07-mapa-regfi.md).
- Se revisaron las funciones weather/oil-market existentes: patrón de handler con fetch/clock inyectados, pruebas Node sin live upstream, errores estructurados. Se reutiliza el patrón, no el scraping de oil-market.

No se han modificado `app/**`, Room, `supabase/**`, configuración, secretos ni las PR #676/#679/#680. El gate de producción permanece independiente y no se da por liberado por esta preparación.
