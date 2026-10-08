# MAPA REGFI — QA de preparación y aceptación futura

## Lo que verifica esta PR

`node --test docs/integrations/mapa-regfi/prep.test.mjs` valida la integridad de fixtures: estructura oficial, round-trip del transporte, escenario cobre, variantes contradictorias y transición histórica. No ejecuta un normalizador, caché, publicación, búsqueda productiva ni Edge Function. Estos tests no demuestran que el futuro backend funcione.

La petición viva identificó HTTP 200, envoltorio, claves, fecha, conteos y checksum documentados en CONTRACT.md. No forma parte de CI; los tests usan solo fixtures locales. `git diff --check` y revisión del diff verifican alcance docs-only. No se necesita build Android para esta entrega.

## Tests obligatorios de implementación futura

| ID | Test/entrada | Resultado verificable |
| --- | --- | --- |
| T01 | Transporte objeto y cadena serializada + Contenido string | Mismo catálogo; parseo limitado |
| T02 | HTML con 200, truncado, Contenido inválido, Tipo distinto CEX | Rechazo íntegro; puntero intacto |
| T03 | búsqueda `cobre`, `CÓBRE`, nombre, alias y SYN-00001 | Hallazgo por índice, sin cambiar nombre oficial |
| T04 | duplicates.synthetic.json | 1 producto, 2 variantes, CONFLICT/REVIEW_REQUIRED; ninguna seleccionada |
| T05 | mismo IdProducto repetido con fechas diferentes | conflicting_provider_id; sin publicación |
| T06 | v1 → v2 | SYN-00001 CANCELLED, SYN-00002 ausente; snapshot anterior idéntico byte a byte |
| T07 | timeout, 429/Retry-After, 5xx, fallo almacenamiento | LKG intacto; fetchedAt original; freshness STALE |
| T08 | sin LKG y error upstream | 503 catalog_unavailable, nunca 200 vacío |
| T09 | misma Fecha+checksum; mismo checksum/nueva Fecha; igual Fecha/diferente checksum; Fecha anterior | Idempotencia solo de sourceVersion idéntica; nueva versión de metadatos cuando cambia Fecha; revisión serializada; rollback bloqueado |
| T10 | registro 00001/ES-001, Unicode, espacios, null, lista tipo erróneo | Mantener identidad; error en tipos ilegales; null no es cero |
| T11 | NP, NO PROCEDE, dosis %, concentración expresada en Cu, rango 7-14 | Texto/unidades intactos, no convertir ni inventar 0 días |
| T12 | min>max, NaN, Infinity, negativo, fecha 2026/02/30 | Candidato inválido; sin publicar parte válida |
| T13 | mismo cultivo/plaga con condiciones/método/dosis distintos | Preservar filas completas, sin producto cartesiano ni dedup por pareja |
| T14 | candidato vacío, pérdida >20%, exceso bytes/filas | Cuarentena/rechazo, LKG intacto; límites configurados |
| T15 | dos refresh concurrentes, proceso muere antes/después del cambio de puntero | Lectura íntegra anterior/nueva; CAS bloquea escritura obsoleta |
| T16 | snapshot refiere versión antigua, rollback, GC | Versión y raw retenidos; histórico no resuelto contra catálogo vivo |
| T17 | carga equivalente a muestra 86 MB/50 MB | RSS/latencia medidos; elegir worker si Edge no tiene margen |
| T18 | lectura paginada con cambio de current durante descarga | Páginas fijadas a sourceVersion/checksum; sin mezclar versiones |
| T19 | usuario normal intenta refresh; upstream URL en request | Refresh denegado; solo endpoint MAPA configurado por servidor |
| T20 | estado administrativo desconocido / conflicto | UNKNOWN/CONFLICT; no acreditar autorización ni aceptación CUE |

## Handoff

Fase #681 PREP. Rama `docs/mapa-regfi-prep` sobre main `69a3e0708b671a286ee65e6e5ef2fd7fa6620a96`. Solo cambia `docs/**`. Sin migraciones, cambios Android, runtime Supabase, despliegues ni evidencia de emulador; no aplican a este slice.

Pendiente antes de producción: gate explícito, contrastar PDF oficial de descripción cuando MAPA lo reponga y medir ingestión en runtime. La descarga del PDF se intentó con el mismo formulario que el JS oficial: HTTP 500, fichero ausente en servidor MAPA. No impide especificar el contrato observado del JSON, pero se mantiene como limitación de corroboración documental. La fuente y el transporte están verificados; no existe garantía de estabilidad del endpoint. El siguiente paso permitido sigue siendo revisión/preparación; implementación en PR nueva cuando se autorice el gate. No se añade alcance de fases posteriores.
